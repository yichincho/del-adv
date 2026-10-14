package com.yichincho.deladv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JevTest {
    private fun probs(ad: Double, news: Double, promo: Double = 0.0, ui: Double = 0.0) =
        mapOf("sponsored_ad" to ad, "news_article" to news, "google_promo" to promo, "feed_ui" to ui)

    @Test fun confidentAdIsHidden() {
        assertTrue(JevRules.decide(probs(0.92, 0.05, 0.02, 0.01), "sponsored_ad", 0.9).hide)
    }

    @Test fun belowThresholdIsKept() {
        assertFalse(JevRules.decide(probs(0.75, 0.25), "sponsored_ad", 0.9).hide)
    }

    @Test fun lowConfidenceIsKept() {
        assertFalse(JevRules.decide(probs(0.9, 0.1), "sponsored_ad", 0.2).hide)
    }

    @Test fun newsIsKept() {
        val d = JevRules.decide(probs(0.03, 0.95, 0.01, 0.01), "news_article", 0.95)
        assertFalse(d.hide)
        assertEquals("news_article", d.label)
        assertEquals(JevConfig.NEGATIVE_TTL_DAYS, JevRules.ttlDays(d))
    }

    @Test fun missingProbabilitiesFallBackToConfidence() {
        assertTrue(JevRules.decide(null, "sponsored_ad", 0.95).hide)
        assertFalse(JevRules.decide(null, "sponsored_ad", 0.6).hide)
        assertFalse(JevRules.decide(null, "unknown_label", 0.99).hide)
    }

    @Test fun cleanStripsBackticksAndControlChars() {
        assertEquals("a b c", JevRules.clean("a`b​c", 50))
        assertEquals("ab…", JevRules.clean("abcdef", 3))
        assertNull(JevRules.clean("  \n ", 10))
    }

    @Test fun fingerprintIsStableAndOrderSensitiveForText() {
        val a = JevRules.fingerprint(listOf("贊助", "標題"), listOf("x", "y"))
        assertEquals(a, JevRules.fingerprint(listOf("贊助", "標題"), listOf("y", "x")))
        assertNotEquals(a, JevRules.fingerprint(listOf("標題", "贊助"), listOf("x", "y")))
        assertEquals(24, a.length)
    }

    @Test fun adViewIds() {
        assertTrue(JevRules.isAdViewId("com.google.android.googlequicksearchbox:id/sponsored_label"))
        assertFalse(JevRules.isAdViewId("com.google.android.googlequicksearchbox:id/headline"))
        assertFalse(JevRules.isAdViewId(null))
    }

    private val card = CardInfo(listOf("贊助", "超值手機方案"), listOf("sponsored_label"), 1080, 600, true, true, 1)

    @Test fun requestShape() {
        val req = JevRequest.build(listOf(card, card.copy(labelHit = false, adViewId = false, images = 0)), "zh-TW")
        assertEquals("jev-latest", req.getString("model"))
        val q = req.getJSONObject("questions").getJSONObject("c1")
        assertEquals("choice", q.getString("type"))
        assertTrue(q.getString("instructions").contains("`cards[1]`"))
        assertEquals(JevRules.CRITERIA.keys, q.getJSONObject("criteria").keySet())
        val c0 = req.getJSONObject("state").getJSONArray("cards").getJSONObject(0)
        assertEquals("1080x600", c0.getString("size"))
        assertEquals("贊助 · 超值手機方案", c0.getString("text"))
        assertEquals(2, c0.getJSONArray("signals").length())
        assertFalse(req.getJSONObject("state").getJSONArray("cards").getJSONObject(1).has("signals"))
    }

    @Test fun parseAnswers() {
        val body = JSONObject(
            """{"model":"jev-1","usage":{"input_tokens":321,"output_tokens":0},"answers":{
              "c0":{"type":"choice","choice":"sponsored_ad","confidence":0.9,
                    "probabilities":{"sponsored_ad":0.93,"news_article":0.05,"google_promo":0.01,"feed_ui":0.01}},
              "c1":{"type":"noul","noul":0.5}}}"""
        )
        val out = JevRequest.parse(body, 3)
        assertTrue(out[0]!!.hide)
        assertNull(out[1])
        assertNull(out[2])
        assertEquals(321, JevRequest.inputTokens(body))
    }
}
