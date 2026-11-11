package com.example.hrrepayment.core.content

import kotlin.test.*

class StoryTextTest {
    @Test fun allElevenEndingVariantsHaveCompleteDistinctBodies() {
        val variants = listOf(
            "overseas_repaid" to "debt_zero", "overseas_indebted" to "debt_remaining",
            "debt_free_restart" to "final_check", "debt_free_restart" to "trust_negative",
            "bankruptcy" to "cash_exhausted", "bankruptcy" to "debt_cap_exceeded",
            "indebted_layoff" to "final_check", "health_death" to "cold_health_low",
            "health_death" to "final_health_low", "low_dopamine" to "prolonged_low", "exposed" to "trust_negative")
        val bodies = variants.map { (id, reason) -> StoryText.endingBody(id, reason) }
        assertEquals(11, bodies.distinct().size)
        assertTrue(bodies.all { it.length > 65 && !it.contains("待接入") })
        assertEquals(2, bodies.count { it.contains("那一天来了，一切的计划消失。") })
        assertFalse(StoryText.endingBody("debt_free_restart", "trust_negative").contains("疫情"))
        assertTrue(variants.all { StoryText.endingTitle(it.first) != "游戏结束" })
    }

    @Test fun fixedEventsHaveStableBodiesAndTheUnchosenOverseasRouteDoesNotInventCompensation() {
        val events = listOf("chapter2_success", "manager_handover", "overseas_offer", "overseas_missed", "winter_cold")
        assertTrue(events.all { StoryText.eventBody(it).length > 30 })
        assertFalse(StoryText.eventBody("overseas_missed").contains("补偿"))
    }
}
