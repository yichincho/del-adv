package com.yichincho.deladv

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Optional TypeSafe Jev settings. Nothing is sent until a key is saved AND the disclosure is accepted. */
object JevSettings {
    private fun of(ctx: Context) = ctx.getSharedPreferences("jev", Context.MODE_PRIVATE)
    fun apiKey(ctx: Context): String? = of(ctx).getString("api_key", null)?.takeIf { it.isNotBlank() }
    fun setApiKey(ctx: Context, key: String?) =
        of(ctx).edit().putString("api_key", key?.trim()).putBoolean("key_invalid", false).apply()
    fun enabled(ctx: Context) = of(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, v: Boolean) = of(ctx).edit().putBoolean("enabled", v).apply()
    fun disclosure(ctx: Context) = of(ctx).getBoolean("disclosure", false)
    fun setDisclosure(ctx: Context, v: Boolean) = of(ctx).edit().putBoolean("disclosure", v).apply()

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun tokensToday(ctx: Context): Int = of(ctx).let { if (it.getString("day", "") == today()) it.getInt("tokens", 0) else 0 }
    fun tokensTotal(ctx: Context): Long = of(ctx).getLong("tokens_total", 0)
    fun addTokens(ctx: Context, n: Int) {
        val p = of(ctx)
        p.edit().putString("day", today()).putInt("tokens", tokensToday(ctx) + n)
            .putLong("tokens_total", p.getLong("tokens_total", 0) + n).apply()
    }

    fun keyInvalid(ctx: Context) = of(ctx).getBoolean("key_invalid", false)
    fun setKeyInvalid(ctx: Context, v: Boolean) = of(ctx).edit().putBoolean("key_invalid", v).apply()
    fun lastError(ctx: Context): String? = of(ctx).getString("last_error", null)
    fun setLastError(ctx: Context, v: String?) = of(ctx).edit().putString("last_error", v).apply()

    /** Mirrors jev-adblock's statusFor(): "ok" is the only state that sends anything. */
    fun status(ctx: Context, now: Long = System.currentTimeMillis()): String = when {
        !enabled(ctx) -> "未啟用"
        apiKey(ctx) == null -> "未設定 API Key"
        !disclosure(ctx) -> "尚未同意資料揭露"
        keyInvalid(ctx) -> "API Key 無效"
        tokensToday(ctx) >= JevConfig.DAILY_TOKEN_BUDGET -> "今日預算已用完"
        JevClassifier.circuitOpenUntil > now -> "API 連續失敗，暫停中"
        else -> "ok"
    }
}

/** Per-card verdict cache keyed by a local fingerprint; persisted to app-private storage. */
class VerdictCache(private val file: File) {
    data class Entry(val hide: Boolean, val pAd: Double, val ts: Long, val ttlDays: Int, val pinned: Boolean)

    private val map = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > JevConfig.CACHE_MAX
    }

    init {
        try {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                for (k in o.keys()) {
                    val e = o.getJSONObject(k)
                    map[k] = Entry(e.getBoolean("h"), e.optDouble("p", 0.0), e.getLong("t"), e.getInt("d"), e.optBoolean("pin"))
                }
            }
        } catch (_: Exception) {
            map.clear()
        }
    }

    @Synchronized fun get(fp: String, now: Long = System.currentTimeMillis()): Entry? {
        val e = map[fp] ?: return null
        if (!e.pinned && now - e.ts > e.ttlDays * 86_400_000L) {
            map.remove(fp)
            return null
        }
        return e
    }

    @Synchronized fun put(fp: String, e: Entry) { map[fp] = e }

    @Synchronized fun save() {
        val o = JSONObject()
        for ((k, e) in map) {
            o.put(k, JSONObject().put("h", e.hide).put("p", e.pAd).put("t", e.ts).put("d", e.ttlDays).put("pin", e.pinned))
        }
        try { file.writeText(o.toString()) } catch (_: Exception) {}
    }

    @Synchronized fun clear() { map.clear(); file.delete() }
    @Synchronized fun size() = map.size
}

/**
 * Background classification of Discover cards with Jev. Never blocks the UI thread and
 * never hides anything on error: callers only mask cards whose cached verdict says hide.
 */
object JevClassifier {
    private val executor = Executors.newSingleThreadExecutor()
    private val inflight = HashSet<String>()
    @Volatile var circuitOpenUntil = 0L
        private set
    private var failures = 0
    private var cache: VerdictCache? = null

    @Synchronized fun cache(ctx: Context): VerdictCache =
        cache ?: VerdictCache(File(ctx.applicationContext.filesDir, "jev-cache.json")).also { cache = it }

    /** Marks a card as "not an ad" permanently (user correction from the overlay). */
    fun pinSafe(ctx: Context, fp: String) {
        val c = cache(ctx)
        c.put(fp, VerdictCache.Entry(false, 0.0, System.currentTimeMillis(), JevConfig.NEGATIVE_TTL_DAYS, pinned = true))
        executor.execute { c.save() }
    }

    /** Queues uncached cards; [onDone] runs on the executor thread after new verdicts are stored. */
    fun classify(ctx: Context, cards: List<CardInfo>, onDone: () -> Unit) {
        val app = ctx.applicationContext
        if (JevSettings.status(app) != "ok") return
        val key = JevSettings.apiKey(app) ?: return
        val c = cache(app)
        val todo = synchronized(inflight) {
            cards.filter { it.fp !in inflight && c.get(it.fp) == null }
                .distinctBy { it.fp }
                .take(JevConfig.MAX_BATCH)
                .also { list -> list.forEach { inflight.add(it.fp) } }
        }
        if (todo.isEmpty()) return
        executor.execute {
            try {
                // Re-check on the worker: budget or circuit may have changed while queued.
                if (JevSettings.status(app) != "ok") return@execute
                val request = JevRequest.build(todo, Locale.getDefault().toLanguageTag())
                val body = JevClient(key).systemOne(request)
                JevSettings.addTokens(app, JevRequest.inputTokens(body))
                val now = System.currentTimeMillis()
                JevRequest.parse(body, todo.size).forEachIndexed { i, d ->
                    // A missing answer is cached as "don't hide" for a day, so it is not re-sent on every scroll.
                    c.put(
                        todo[i].fp,
                        if (d != null) VerdictCache.Entry(d.hide, d.pAd, now, JevRules.ttlDays(d), false)
                        else VerdictCache.Entry(false, 0.0, now, JevConfig.UNCERTAIN_TTL_DAYS, false)
                    )
                }
                c.save()
                failures = 0
                JevSettings.setLastError(app, null)
                onDone()
            } catch (e: JevException) {
                JevSettings.setLastError(app, "${e.kind} ${e.message?.take(120)}")
                when (e.kind) {
                    JevException.Kind.AUTH -> JevSettings.setKeyInvalid(app, true)
                    JevException.Kind.VALIDATION -> {}
                    else -> if (++failures >= JevConfig.CIRCUIT_FAILURES) {
                        circuitOpenUntil = System.currentTimeMillis() + JevConfig.CIRCUIT_OPEN_MS
                        failures = 0
                    }
                }
            } catch (e: Exception) {
                JevSettings.setLastError(app, e.javaClass.simpleName)
            } finally {
                synchronized(inflight) { todo.forEach { inflight.remove(it.fp) } }
            }
        }
    }

    /** Settings-screen key test; result delivered on the executor thread. */
    fun testKey(key: String, done: (String) -> Unit) {
        executor.execute {
            done(
                try {
                    JevClient(key).checkKey()
                    "API Key 可用"
                } catch (e: JevException) {
                    if (e.kind == JevException.Kind.AUTH) "API Key 無效（${e.status}）" else "無法連線：${e.kind}"
                } catch (e: Exception) {
                    "錯誤：${e.javaClass.simpleName}"
                }
            )
        }
    }
}
