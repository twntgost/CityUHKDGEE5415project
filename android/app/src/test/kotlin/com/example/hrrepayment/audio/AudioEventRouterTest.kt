package com.example.hrrepayment.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音频映射纯函数契约（项目书 §15.6 / 开发日志 P2 合同）：
 *  - 一男一女 → 被推者性别的 accept 池（与结果无关）；
 *  - 两男 → male_refuse；两女 → female_refuse；
 *  - 同一事务结算并触发结局时结局音优先；
 *  - GoodEnding=人生逆袭、HealthEnding=逍遥法外、其余全部 → BadEnding。
 */
class AudioEventRouterTest {

    @Test
    fun `mixed day maps to the pushed gender's accept pool`() {
        assertEquals(
            AudioEvent.ResumePool.MALE_ACCEPT,
            AudioEventRouter.resumePool(listOf("male", "female"), "male"),
        )
        assertEquals(
            AudioEvent.ResumePool.FEMALE_ACCEPT,
            AudioEventRouter.resumePool(listOf("male", "female"), "female"),
        )
        assertEquals(
            AudioEvent.ResumePool.FEMALE_ACCEPT,
            AudioEventRouter.resumePool(listOf("female", "male"), "female"),
        )
    }

    @Test
    fun `same-gender days map to the refuse pools`() {
        assertEquals(
            AudioEvent.ResumePool.MALE_REFUSE,
            AudioEventRouter.resumePool(listOf("male", "male"), "male"),
        )
        assertEquals(
            AudioEvent.ResumePool.FEMALE_REFUSE,
            AudioEventRouter.resumePool(listOf("female", "female"), "female"),
        )
    }

    @Test
    fun `ending takes priority over the resume confirm sound`() {
        val event = AudioEventRouter.forSubmission(
            todayGenders = listOf("male", "female"),
            pushedGender = "male",
            ended = true,
            endingId = "exposed",
        )
        assertEquals(AudioEvent.Ending(AudioEvent.EndingTone.BAD), event)
    }

    @Test
    fun `ending tones follow the project mapping`() {
        assertEquals(AudioEvent.EndingTone.GOOD, AudioEventRouter.endingTone("overseas_repaid"))
        assertEquals(AudioEvent.EndingTone.HEALTH, AudioEventRouter.endingTone("overseas_indebted"))
        for (id in listOf("debt_free_restart", "bankruptcy", "indebted_layoff", "health_death", "low_dopamine", "exposed")) {
            assertEquals(AudioEvent.EndingTone.BAD, AudioEventRouter.endingTone(id))
        }
        assertEquals(AudioEvent.EndingTone.BAD, AudioEventRouter.endingTone("future_ending"))
        assertEquals(
            AudioEvent.Ending(AudioEvent.EndingTone.BAD),
            AudioEventRouter.forSubmission(listOf("male", "female"), "male", true, null),
        )
    }

    @Test
    fun `every event resolves to at least one catalogued file`() {
        val pools = listOf(
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.MALE_ACCEPT),
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.FEMALE_ACCEPT),
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.MALE_REFUSE),
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.FEMALE_REFUSE),
            AudioEvent.BetStart,
            AudioEvent.BetWin,
            AudioEvent.BetLose,
            AudioEvent.Ending(AudioEvent.EndingTone.GOOD),
            AudioEvent.Ending(AudioEvent.EndingTone.HEALTH),
            AudioEvent.Ending(AudioEvent.EndingTone.BAD),
        )
        for (event in pools) {
            val files = SoundCatalog.filesFor(event)
            assertTrue("$event must resolve", files.isNotEmpty())
            assertTrue(files.all { it.startsWith("audio/") && it.endsWith("." + SoundCatalog.EXT) })
        }
    }

    @Test
    fun `catalog counts match the 53-file manifest`() {
        // 48 条既有 + 4 条材质交互音（拆档案袋/翻页/笔尖/盖章）+ 1 条撒钱音。
        assertEquals(53, SoundCatalog.all.size)
        assertEquals(53, SoundCatalog.all.toSet().size)
        assertEquals(53, SoundCatalog.expectedCounts.values.sum())
    }
}
