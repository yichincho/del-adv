package com.yichincho.deladv

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView

/**
 * No-root Google Discover prototype.
 * Only observes Google App's accessibility tree; never clicks UI, reads screen images,
 * sends data to an API, or interacts with unrelated applications.
 *
 * Android cannot remove another app's card from its layout. Only reversible overlays
 * are possible here. If the card bounds cannot be reliably established, do nothing.
 *
 * On launcher "minus one" screens (Xiaomi, Pixel) Discover is a Google App window drawn
 * over the launcher, so the *active* window is usually the launcher. We therefore scan
 * all interactive windows and only read the ones owned by the Google App.
 */
class DiscoverService : AccessibilityService() {
    private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler = Handler(Looper.getMainLooper())
    private val overlays = mutableMapOf<String, View>()
    private val dismissed = mutableSetOf<String>()
    private var lastSignature = ""
    private val refreshTask = Runnable { refresh() }
    // Events are package-filtered, so leaving Google App produces no event. While overlays
    // are visible, poll the window list and remove them as soon as Discover is gone.
    private val watchdog = object : Runnable {
        override fun run() {
            if (overlays.isEmpty()) return
            if (googleWindowRoots().also { roots -> roots.forEach { it.recycle() } }.isEmpty()) clear()
            else handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticLog.status(this, "服務已連線 ${Build.MANUFACTURER} ${Build.MODEL} SDK ${Build.VERSION.SDK_INT}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!Prefs.enabled(this)) {
            clear()
            return
        }
        // Content moves while scrolling; a stale overlay would cover the wrong card.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) hideAll()
        handler.removeCallbacks(refreshTask)
        handler.postDelayed(refreshTask, Prefs.debounceMs(this))
    }

    private fun googleWindowRoots(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val list: List<AccessibilityWindowInfo> = try { windows } catch (_: Exception) { emptyList() }
        for (window in list) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            if (root.packageName?.toString() == GOOGLE_PACKAGE) result.add(root) else root.recycle()
        }
        if (result.isEmpty()) {
            // Fallback for ROMs that do not report interactive windows.
            rootInActiveWindow?.let {
                if (it.packageName?.toString() == GOOGLE_PACKAGE) result.add(it) else it.recycle()
            }
        }
        return result
    }

    private fun refresh() {
        val roots = googleWindowRoots()
        if (roots.isEmpty()) {
            clear()
            return
        }
        val screen = resources.displayMetrics
        val ads = linkedMapOf<String, Rect>()
        val scan = AdScanner(screen.widthPixels, screen.heightPixels)
        val dump = if (DiagnosticLog.armed) StringBuilder() else null
        for (root in roots) {
            try {
                scan.walk(root, dump).forEach { area ->
                    if (ads.values.none { Rect.intersects(it, area) }) {
                        ads["${area.left}:${area.top}:${area.width()}:${area.height()}"] = area
                    }
                }
            } finally {
                root.recycle()
            }
        }
        if (dump != null) {
            dump.append("偵測到 ${scan.labelHits} 個廣告標籤，可遮罩 ${ads.size} 個區域\n")
            DiagnosticLog.write(this, dump.toString())
        }
        DiagnosticLog.counters(this, scan.labelHits, ads.size)

        val signature = ads.keys.joinToString("|")
        if (signature != lastSignature) {
            dismissed.clear()
            lastSignature = signature
        }
        (overlays.keys - ads.keys).toList().forEach(::removeOverlay)
        for ((key, area) in ads) {
            if (key !in dismissed) {
                val existing = overlays[key]
                if (existing == null) showOverlay(key, area) else existing.visibility = View.VISIBLE
            }
        }
        handler.removeCallbacks(watchdog)
        if (overlays.isNotEmpty()) handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun showOverlay(key: String, area: Rect) {
        val cover = TextView(this).apply {
            text = "🛡 已遮罩贊助內容\n點此暫時顯示"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(16, 35, 62))
            setOnClickListener {
                dismissed.add(key)
                removeOverlay(key)
            }
        }
        val params = WindowManager.LayoutParams(
            area.width(), area.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = area.left
            y = area.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            manager.addView(cover, params)
            overlays[key] = cover
        } catch (_: Exception) {
            // Fail open: normal content remains usable if overlays are unsupported.
        }
    }

    private fun hideAll() = overlays.values.forEach { it.visibility = View.GONE }

    private fun removeOverlay(key: String) {
        overlays.remove(key)?.let { try { manager.removeView(it) } catch (_: Exception) {} }
    }

    private fun clear() {
        handler.removeCallbacks(refreshTask)
        handler.removeCallbacks(watchdog)
        overlays.keys.toList().forEach(::removeOverlay)
        dismissed.clear()
        lastSignature = ""
    }

    override fun onInterrupt() = clear()
    override fun onDestroy() { clear(); super.onDestroy() }

    companion object {
        const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val WATCHDOG_MS = 500L
    }
}
