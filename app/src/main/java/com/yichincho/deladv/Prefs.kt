package com.yichincho.deladv

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    private fun of(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = of(ctx).getBoolean("enabled", true)
    fun setEnabled(ctx: Context, value: Boolean) = of(ctx).edit().putBoolean("enabled", value).apply()
    fun debounceMs(ctx: Context) = of(ctx).getLong("debounce_ms", 300L)
}

/**
 * Local-only diagnostics so a real phone can tell us what Discover's tree looks like.
 * Nothing leaves the device unless the user taps "分享" themselves.
 */
object DiagnosticLog {
    /** Set by MainActivity; the next Google App refresh writes one dump and disarms. */
    @Volatile var armed = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("diag", Context.MODE_PRIVATE)
    fun file(ctx: Context) = File(ctx.filesDir, "discover-dump.txt")

    fun write(ctx: Context, body: String) {
        armed = false
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        file(ctx).writeText("del-adv 畫面結構 $stamp\n$body")
    }

    fun status(ctx: Context, text: String) = prefs(ctx).edit().putString("status", text).apply()

    fun counters(ctx: Context, labels: Int, masked: Int) {
        val p = prefs(ctx)
        p.edit()
            .putInt("scans", p.getInt("scans", 0) + 1)
            .putInt("labels_total", p.getInt("labels_total", 0) + labels)
            .putInt("masked_total", p.getInt("masked_total", 0) + masked)
            .apply()
    }

    fun summary(ctx: Context): String {
        val p = prefs(ctx)
        return "掃描 ${p.getInt("scans", 0)} 次 / 偵測標籤 ${p.getInt("labels_total", 0)} 次 / " +
            "遮罩 ${p.getInt("masked_total", 0)} 次\n${p.getString("status", "服務尚未連線")}"
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit().remove("scans").remove("labels_total").remove("masked_total").apply()
        file(ctx).delete()
    }
}
