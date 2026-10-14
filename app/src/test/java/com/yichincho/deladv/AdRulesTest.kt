package com.yichincho.deladv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRulesTest {
    @Test fun exactLabels() {
        for (s in listOf("廣告", "贊助", " 贊助 ", "Sponsored", "AD", "ad", "Promoted", "广告")) {
            assertTrue(s, AdRules.isSponsoredLabel(s))
        }
    }

    @Test fun labelWithPublisher() {
        assertTrue(AdRules.isSponsoredLabel("贊助 · example.com"))
        assertTrue(AdRules.isSponsoredLabel("Sponsored • Shop"))
    }

    @Test fun headlinesAreNotLabels() {
        for (s in listOf(
            "政府擬修法規範網路廣告", "Ad hoc committee meets today", "advertising market grows",
            "贊助商退出世足賽", "廣告業者 · 年度報告", "", null
        )) {
            assertFalse(s.toString(), AdRules.isSponsoredLabel(s))
        }
    }

    private val w = 1080
    private val h = 2400

    @Test fun picksLargestCardBelowFeed() {
        val label = Box(40, 900, 120, 940)
        val row = Box(40, 880, 1040, 960)
        val card = Box(0, 500, 1080, 1300)
        val feed = Box(0, 0, 1080, 2400)
        assertEquals(card, AdRules.pickCard(listOf(label, row, card, feed), w, h))
    }

    @Test fun nothingCardLikeMeansNoMask() {
        val label = Box(40, 900, 120, 940)
        val feed = Box(0, 0, 1080, 2400)
        assertNull(AdRules.pickCard(listOf(label, feed), w, h))
    }

    @Test fun stopsAtFeedContainer() {
        val label = Box(40, 900, 120, 940)
        val feed = Box(0, 0, 1080, 2400)
        val weirdAbove = Box(0, 100, 1080, 600)
        assertNull(AdRules.pickCard(listOf(label, feed, weirdAbove), w, h))
    }

    @Test fun offscreenCardIsRejected() {
        val label = Box(40, 2350, 120, 2390)
        val card = Box(0, 2000, 1080, 2700)
        assertNull(AdRules.pickCard(listOf(label, card), w, h))
    }
}
