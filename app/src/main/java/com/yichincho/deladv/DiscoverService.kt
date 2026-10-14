package com.yichincho.deladv

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/**
 * No-root Google Discover prototype.
 * Only observes Google App's accessibility tree; never clicks UI, reads screen images,
 * sends data to an API, or interacts with unrelated applications.
 *
 * Android cannot remove another app's card from its layout. Only reversible overlays
 * are possible here. If the card bounds cannot be reliably established, do nothing.
 */
class DiscoverService : AccessibilityService() {
    private val googlePackage = "com.google.android.googlequicksearchbox"
    private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler = Handler(Looper.getMainLooper())
    private val overlays = mutableMapOf<String, View>()
    private val dismissed = mutableSetOf<String>()
    private var lastSignature = ""
    private val sponsoredLabel = Regex(
        "^(廣告|贊助|贊助內容|廣告內容|sponsored|sponsored content|ad|advertisement|promoted)$",
        RegexOption.IGNORE_CASE
    )
    private val refreshTask = Runnable { refresh() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != googlePackage) {
            clear()
            return
        }
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enabled", true)) {
            clear()
            return
        }
        handler.removeCallbacks(refreshTask)
        handler.postDelayed(refreshTask, 400L)
    }

    private fun refresh() {
        val root = rootInActiveWindow ?: run { clear(); return }
        if (root.packageName?.toString() != googlePackage) {
            clear()
            return
        }
        val screen = resources.displayMetrics
        val ads = linkedMapOf<String, Rect>()
        var visited = 0

        fun find(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 28 || ++visited > 1500 || ads.size >= 5) return
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (sponsoredLabel.matches(text) || sponsoredLabel.matches(desc)) {
                val marker = Rect().also(node::getBoundsInScreen)
                if (marker.width() > 0 && marker.height() > 0) {
                    // A bounded card-like ancestor: don't cover the full screen/feed.
                    var current: AccessibilityNodeInfo = node
                    var selected: Rect? = null
                    for (step in 0..3) {
                        val bounds = Rect().also(current::getBoundsInScreen)
                        if (bounds.width() in (screen.widthPixels * 0.60).toInt()..screen.widthPixels &&
                            bounds.height() in 100..(screen.heightPixels * 0.65).toInt() &&
                            bounds.top >= 0 && bounds.bottom <= screen.heightPixels) {
                            selected = bounds
                        }
                        val parent = current.parent ?: break
                        if (current !== node) current.recycle()
                        current = parent
                    }
                    if (current !== node) current.recycle()
                    val area = selected
                    if (area != null && ads.values.none { Rect.intersects(it, area) }) {
                        // Location + size are a best-effort identity; no user content leaves device.
                        val key = "${area.left}:${area.top}:${area.width()}:${area.height()}"
                        ads[key] = area
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try { find(child, depth + 1) } finally { child.recycle() }
            }
        }

        try { find(root, 0) } finally { root.recycle() }
        val signature = ads.keys.joinToString("|")
        if (signature != lastSignature) {
            dismissed.clear()
            lastSignature = signature
        }
        (overlays.keys - ads.keys).toList().forEach(::removeOverlay)
        for ((key, area) in ads) {
            if (key !in overlays && key !in dismissed) showOverlay(key, area)
        }
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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = area.left
            y = area.top
        }
        try {
            manager.addView(cover, params)
            overlays[key] = cover
        } catch (_: Exception) {
            // Fail open: normal content remains usable if overlays are unsupported.
        }
    }

    private fun removeOverlay(key: String) {
        overlays.remove(key)?.let { try { manager.removeView(it) } catch (_: Exception) {} }
    }

    private fun clear() {
        handler.removeCallbacks(refreshTask)
        overlays.keys.toList().forEach(::removeOverlay)
        dismissed.clear()
        lastSignature = ""
    }

    override fun onInterrupt() = clear()
    override fun onDestroy() { clear(); super.onDestroy() }
}
