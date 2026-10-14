package com.yichincho.deladv

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** One pass over a Google App window tree. Optionally appends a privacy-reduced dump. */
class AdScanner(private val screenW: Int, private val screenH: Int) {
    var labelHits = 0
        private set
    private var visited = 0

    fun walk(root: AccessibilityNodeInfo, dump: StringBuilder?): List<Rect> {
        val found = mutableListOf<Rect>()
        visit(root, 0, dump, found)
        return found
    }

    private fun visit(node: AccessibilityNodeInfo, depth: Int, dump: StringBuilder?, found: MutableList<Rect>) {
        if (depth > MAX_DEPTH || ++visited > MAX_NODES || found.size >= MAX_ADS) return
        val bounds = Rect().also(node::getBoundsInScreen)
        val hit = AdRules.isSponsoredLabel(node.text) || AdRules.isSponsoredLabel(node.contentDescription)
        dump?.appendNode(node, depth, bounds, hit)
        if (hit && bounds.width() > 0 && bounds.height() > 0) {
            labelHits++
            cardFor(node)?.let { found.add(it) }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { visit(child, depth + 1, dump, found) } finally { child.recycle() }
        }
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
        private const val MAX_ADS = 6
        private const val DUMP_LIMIT = 200_000
    }
}
