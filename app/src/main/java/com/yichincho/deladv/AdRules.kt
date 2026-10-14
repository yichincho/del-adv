package com.yichincho.deladv

/**
 * Pure decision rules, kept free of Android types so they run as plain JVM unit tests.
 */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

object AdRules {
    private val labels = setOf(
        "廣告", "贊助", "赞助", "贊助內容", "廣告內容", "赞助内容", "广告",
        "sponsored", "sponsored content", "ad", "ads", "advertisement", "promoted"
    )
    // Discover sometimes renders the label as "贊助 · publisher.com" in one node.
    private val separators = charArrayOf('·', '•', '‧', '|', '・')

    /** Conservative: only a known label, alone or followed by a separator. Never a substring of a headline. */
    fun isSponsoredLabel(raw: CharSequence?): Boolean {
        val text = raw?.toString()?.trim() ?: return false
        if (text.isEmpty() || text.length > 60) return false
        if (text.lowercase() in labels) return true
        val cut = text.indexOfAny(separators)
        if (cut <= 0) return false
        return text.substring(0, cut).trim().lowercase() in labels
    }

    /**
     * Walks from the label upwards ([chain] = label first, then its ancestors) and keeps the
     * largest box that still looks like a single feed card. Returns null rather than guessing.
     */
    fun pickCard(chain: List<Box>, screenW: Int, screenH: Int, maxSteps: Int = 6): Box? {
        var selected: Box? = null
        for (box in chain.take(maxSteps + 1)) {
            val cardLike = box.width >= (screenW * 0.60).toInt() && box.width <= screenW &&
                box.height in 100..(screenH * 0.65).toInt() &&
                box.top >= 0 && box.bottom <= screenH
            if (cardLike) selected = box
            // Once an ancestor is taller than a card, everything above is the feed itself.
            if (box.height > screenH * 0.65) break
        }
        return selected
    }
}
