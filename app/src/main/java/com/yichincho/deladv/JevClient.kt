package com.yichincho.deladv

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class JevException(val kind: Kind, val status: Int?, message: String, val retryAfterMs: Long? = null) : Exception(message) {
    enum class Kind { AUTH, VALIDATION, RATE_LIMIT, OVERLOADED, SERVER, NETWORK, TIMEOUT }
}

/** Blocking HTTP client; call only from a background thread. Retry policy mirrors jev-adblock's client.ts. */
class JevClient(private val apiKey: String, private val base: String = JevConfig.API_BASE) {

    private fun open(path: String, method: String): HttpURLConnection =
        (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = JevConfig.TIMEOUT_MS
            readTimeout = JevConfig.TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

    private fun send(path: String, method: String, body: String?): Pair<Int, String> {
        val conn = open(path, method)
        try {
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw toError(code, text, conn.getHeaderField("Retry-After"))
            return code to text
        } catch (e: JevException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw JevException(JevException.Kind.TIMEOUT, null, "request timed out")
        } catch (e: IOException) {
            throw JevException(JevException.Kind.NETWORK, null, e.message ?: "network error")
        } finally {
            conn.disconnect()
        }
    }

    private fun toError(code: Int, body: String, retryAfter: String?): JevException {
        val msg = "$code ${body.take(300)}"
        val wait = retryAfter?.toLongOrNull()?.times(1000)
        return when (code) {
            401, 403 -> JevException(JevException.Kind.AUTH, code, msg)
            400, 422 -> JevException(JevException.Kind.VALIDATION, code, msg)
            429 -> JevException(JevException.Kind.RATE_LIMIT, code, msg, wait)
            503, 529 -> JevException(JevException.Kind.OVERLOADED, code, msg, wait)
            else -> JevException(JevException.Kind.SERVER, code, msg)
        }
    }

    /** Throws on auth failure; used by the settings "test key" button. */
    fun checkKey() {
        send(JevConfig.MODELS_PATH, "GET", null)
    }

    fun systemOne(request: JSONObject): JSONObject {
        val body = request.toString()
        val rateDelays = longArrayOf(1000, 2000, 4000)
        val overloadDelays = longArrayOf(2000, 4000)
        var rateIdx = 0
        var overIdx = 0
        var networkRetried = false
        var result: JSONObject? = null
        while (result == null) {
            try {
                result = JSONObject(send(JevConfig.SYSTEMONE_PATH, "POST", body).second)
            } catch (e: JevException) {
                val wait: Long = when {
                    (e.kind == JevException.Kind.NETWORK || e.kind == JevException.Kind.TIMEOUT) && !networkRetried -> {
                        networkRetried = true
                        500L
                    }
                    e.kind == JevException.Kind.RATE_LIMIT && rateIdx < rateDelays.size -> {
                        val d = rateDelays[rateIdx]
                        rateIdx++
                        e.retryAfterMs ?: d
                    }
                    e.kind == JevException.Kind.OVERLOADED && overIdx < overloadDelays.size -> {
                        val d = overloadDelays[overIdx]
                        overIdx++
                        e.retryAfterMs ?: d
                    }
                    else -> throw e
                }
                Thread.sleep(minOf(wait, 15_000L))
            }
        }
        return checkNotNull(result)
    }
}
