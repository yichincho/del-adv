package com.yichincho.deladv

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** A visible feed card plus where it is on screen. */
class FeedCard(val bounds: Rect, val info: CardInfo, val editable: Boolean)

/**
 * One pass over a Google App window tree:
 *  - finds explicit sponsored labels (local rule, free, no network)
 *  - lists the cards of the main feed (largest scrollable container) for optional Jev judging
 *  - optionally appends a privacy-reduced dump for on-device diagnostics
 */
class AdScanner(private val screenW: Int, private val screenH: Int) {
    var labelHits = 0
        private set
    /** Cards of the largest scrollable container seen so far (the Discover feed). */
    var cards: List<FeedCard> = emptyList()
        private set
    /** Label hits that did not fall inside any listed card. */
    val labelOnly = mutableListOf<Rect>()
    private val labelRects = mutableListOf<Rect>()
    private var visited = 0
    private var bestFeedArea = 0L

    fun walk(root: AccessibilityNodeInfo, dump: StringBuilder?) {
        visit(root, 0, dump)
    }

    /** Call once after all windows are walked. */
    fun finish() {
        for (r in labelRects) {
            if (cards.none { Rect.intersects(it.bounds, r) }) labelOnly.add(r)
        }
    }

    private fun visit(node: AccessibilityNodeInfo, depth: Int, dump: StringBuilder?) {
        if (depth > MAX_DEPTH || ++visited > MAX_NODES) return
        val bounds = Rect().also(node::getBoundsInScreen)
        val hit = AdRules.isSponsoredLabel(node.text) || AdRules.isSponsoredLabel(node.contentDescription)
        dump?.appendNode(node, depth, bounds, hit)
        if (hit && bounds.width() > 0 && bounds.height() > 0) {
            labelHits++
            cardFor(node)?.let(labelRects::add)
        }
        if (node.isScrollable) {
            val area = bounds.width().toLong() * bounds.height()
            if (area > bestFeedArea && bounds.width() >= screenW * 0.6) {
                bestFeedArea = area
                cards = describeChildren(node)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { visit(child, depth + 1, dump) } finally { child.recycle() }
        }
    }

    private fun describeChildren(feed: AccessibilityNodeInfo): List<FeedCard> {
        val out = mutableListOf<FeedCard>()
        for (i in 0 until feed.childCount) {
            val child = feed.getChild(i) ?: continue
            try {
                val b = Rect().also(child::getBoundsInScreen)
                if (b.width() < screenW * 0.6 || b.height() < MIN_CARD_H || !b.intersect(0, 0, screenW, screenH)) continue
                val acc = Collector()
                acc.collect(child, 0)
                out.add(FeedCard(Rect(b), acc.toInfo(b), acc.editable))
            } finally {
                child.recycle()
            }
        }
        return out
    }

    private class Collector {
        val texts = mutableListOf<String>()
        val ids = LinkedHashSet<String>()
        var label = false
        var adId = false
        var images = 0
        var editable = false
        var count = 0

        fun collect(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12 || ++count > 150) return
            if (n.isEditable) editable = true
            if (n.className?.toString()?.contains("Image") == true) images++
            n.viewIdResourceName?.substringAfter(":id/")?.let { id ->
                if (ids.size < JevConfig.MAX_VIEW_IDS) ids.add(id.take(40))
                if (JevRules.isAdViewId(id)) adId = true
            }
            for (raw in listOf(n.text, n.contentDescription)) {
                if (AdRules.isSponsoredLabel(raw)) label = true
                val s = JevRules.clean(raw, 120) ?: continue
                if (texts.size < JevConfig.MAX_TEXTS && s !in texts) texts.add(s)
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                try { collect(c, depth + 1) } finally { c.recycle() }
            }
        }

        fun toInfo(b: Rect) = CardInfo(texts.toList(), ids.toList(), b.width(), b.height(), label, adId, images)
    }

    private fun cardFor(label: AccessibilityNodeInfo): Rect? {
        val chain = mutableListOf<Box>()
        val owned = mutableListOf<AccessibilityNodeInfo>()
        var current: AccessibilityNodeInfo? = label
        try {
            while (current != null && chain.size <= 6) {
                val r = Rect().also(current::getBoundsInScreen)
                chain.add(Box(r.left, r.top, r.right, r.bottom))
                current = current.parent?.also(owned::add)
            }
        } finally {
            owned.forEach { it.recycle() }
        }
        val box = AdRules.pickCard(chain, screenW, screenH) ?: return null
        return Rect(box.left, box.top, box.right, box.bottom)
    }

    private fun StringBuilder.appendNode(node: AccessibilityNodeInfo, depth: Int, b: Rect, hit: Boolean) {
        if (length > DUMP_LIMIT) return
        append("  ".repeat(depth.coerceAtMost(20)))
        append(if (hit) "★ " else "- ")
        append(node.className?.toString()?.substringAfterLast('.') ?: "?")
        node.viewIdResourceName?.let { append(" #").append(it.substringAfter(":id/")) }
        append(" [${b.left},${b.top}][${b.right},${b.bottom}]")
        safeText(node.text)?.let { append(" t=\"").append(it).append('"') }
        safeText(node.contentDescription)?.let { append(" d=\"").append(it).append('"') }
        if (node.isClickable) append(" click")
        if (node.isScrollable) append(" scroll")
        append('\n')
    }

    /** Short strings (labels, buttons) are kept; long ones (headlines) are reduced to their length. */
    private fun safeText(value: CharSequence?): String? {
        val s = value?.toString()?.replace('\n', ' ')?.trim() ?: return null
        if (s.isEmpty()) return null
        return if (s.length <= 16) s else "<${s.length} 字>"
    }

    companion object {
        private const val MAX_DEPTH = 40
        private const val MAX_NODES = 2500
        private const val MIN_CARD_H = 100
        private const val DUMP_LIMIT = 200_000
    }
}
