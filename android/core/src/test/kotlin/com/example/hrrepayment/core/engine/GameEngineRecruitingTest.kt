package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.model.Command
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.random.RandomSource
import com.example.hrrepayment.core.random.SplitMix64Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P2 招聘六日切片规则测试（技术文档 §7 必测项）：
 * 工作 19/20/29/30/31/49/50、周期升级与随机/特殊成功、三成功跨月、四连败、
 * trust 0/-1、恢复、重复提交。
 */
class GameEngineRecruitingTest {

    private fun engine(
        probs: List<Int> = List(12) { 5000 },
        random: RandomSource = ScriptedRandomSource(List(64) { true }),
    ) = GameEngine(content = TestContent.ref(probs), random = random)

    /** 新档并完成 开场确认 → 进入招聘（Recruiting 阶段）。 */
    private fun started(random: RandomSource = ScriptedRandomSource(List(64) { true })): GameState {
        val e = GameEngine(TestContent.ref(), random)
        val fresh = e.apply(null, Command.StartNewGame(0L, Command.NO_SAVE_ID, "c0", "s", 1L)).newState
        var s = e.apply(fresh, Command.AcknowledgeOpening(fresh.revision, fresh.saveId, "c1")).newState
        s = e.apply(s, Command.EnterRecruiting(s.revision, s.saveId, "c2")).newState
        return s
    }

    private fun GameState.select(e: GameEngine, n: Int, id: String): GameState =
        e.apply(this, Command.SelectCandidate(revision, saveId, "sel-$n", id)).newState

    private fun GameState.submit(e: GameEngine, n: Int): GameState {
        val candidateId = selectedCandidateId!!
        val marked = e.apply(this, Command.MarkStatement(revision, saveId, "mark-$n", candidateId, "school")).newState
        return e.apply(marked, Command.SubmitCandidate(marked.revision, marked.saveId, "sub-$n", candidateId, "school")).newState
    }

    private fun GameState.ack(e: GameEngine, n: Int): GameState =
        e.apply(this, Command.AcknowledgeResult(revision, saveId, "ack-$n")).newState

    private fun GameState.reward(e: GameEngine, n: Int, option: Command.SuccessRewardOption): GameState =
        e.apply(this, Command.ChooseSuccessReward(revision, saveId, "rew-$n", option)).newState

    private fun GameState.enter(e: GameEngine, n: Int): GameState =
        e.apply(this, Command.EnterRecruiting(revision, saveId, "ent-$n")).newState

    /**
     * 完整一天：进入招聘 → 选第一份 → 提交 → 确认（领奖则默认 WORK）。
     * 返回处于 DayEntry / MonthIncome / Ended 的状态。
     */
    private fun GameState.playDay(e: GameEngine, n: Int): GameState {
        var s = this
        if (s.phase == Phase.DayEntry) s = s.enter(e, n + 30_000)
        s = s.select(e, n, TestContent.id(s.position.current.index * 2)).submit(e, n)
        if (s.phase == Phase.Ended) return s
        s = s.ack(e, n + 10_000)
        if (s.phase == Phase.SuccessReward) s = s.reward(e, n + 20_000, Command.SuccessRewardOption.WORK)
        return s
    }

    // ------------------------------------------------------------ 基础流程

    @Test
    fun `month end settles salary repayment interest and skill`() {
        val e = engine()
        var s = GameState.newGame("p3", 1L).copy(
            phase = Phase.MonthIncome,
            debt = Money(1_000_000L),
            position = com.example.hrrepayment.core.model.CalendarPosition(com.example.hrrepayment.core.model.WorkdayRef(35)),
        )
        s = e.apply(s, Command.FinishIncome(s.revision, s.saveId, "income")).newState
        assertEquals(Phase.Repayment, s.phase)
        assertTrue(s.cash.cents > 600_000L)
        s = e.apply(s, Command.RepayDebt(s.revision, s.saveId, "repay", "100")).newState
        assertEquals(Phase.InterestCheck, s.phase)
        s = e.apply(s, Command.FinishEconomy(s.revision, s.saveId, "interest")).newState
        assertEquals(Phase.MonthlySkill, s.phase)
        s = e.apply(s, Command.ChooseMonthlySkill(s.revision, s.saveId, "skill", Command.MonthlySkillOption.HEALTH)).newState
        assertEquals(Phase.DayEntry, s.phase)
        assertEquals(36, s.position.current.index)
        assertEquals(6, s.attributes.health)
    }

    @Test
    fun `repayment rejects negative text and amounts above cash`() {
        val e = engine()
        val s = GameState.newGame("p3", 1L).copy(phase = Phase.Repayment)
        assertFailsWith<EngineError.InvalidCommand> { e.apply(s, Command.RepayDebt(s.revision, s.saveId, "negative", "-1")) }
        assertFailsWith<EngineError.InvalidCommand> { e.apply(s, Command.RepayDebt(s.revision, s.saveId, "large", "999999")) }
    }

    @Test
    fun `bet freezes terms and settles a cashable win`() {
        val e = engine()
        val s = GameState.newGame("bet", 1L).copy(phase = Phase.DayEntry, attributes = com.example.hrrepayment.core.model.Attributes(luck = 1))
        val placed = e.apply(s, Command.PlaceBet(s.revision, s.saveId, "place", "m1", "home", "500", 1)).newState
        assertEquals(Money(550_000L), placed.cash)
        assertEquals(Money(650_000L), placed.bettingCap)
        assertEquals(20, placed.activeBet!!.oddsTenths)
        val settled = e.apply(placed.copy(position = placed.position.copy(current = com.example.hrrepayment.core.model.WorkdayRef(1))), Command.SettleBet(placed.revision, placed.saveId, "settle", "m1", "home")).newState
        assertEquals(Money(650_000L), settled.cash)
        assertNull(settled.activeBet)
        assertEquals(1, settled.successfulBetCount)
        assertEquals(Money(650_000L), settled.bettingCap)
    }

    @Test
    fun `wrong bet increases luck and a correct bet always pays`() {
        val e = engine()
        val base = GameState.newGame("bet", 1L).copy(phase = Phase.DayEntry)
        val wrong = e.apply(base, Command.PlaceBet(base.revision, base.saveId, "p1", "m1", "a", "500", 1)).newState
        val afterWrong = e.apply(wrong.copy(position = wrong.position.copy(current = com.example.hrrepayment.core.model.WorkdayRef(1))), Command.SettleBet(wrong.revision, wrong.saveId, "s1", "m1", "b")).newState
        assertEquals(Money(550_000L), afterWrong.cash)
        assertEquals(1, afterWrong.attributes.luck)
        val zero = e.apply(afterWrong.copy(phase = Phase.DayEntry, attributes = afterWrong.attributes.copy(luck = 0)), Command.PlaceBet(afterWrong.revision, afterWrong.saveId, "p2", "m2", "a", "500", 2)).newState
        val paid = e.apply(zero.copy(position = zero.position.copy(current = com.example.hrrepayment.core.model.WorkdayRef(2))), Command.SettleBet(zero.revision, zero.saveId, "s2", "m2", "a")).newState
        assertEquals(Money(600_000L), paid.cash)
    }

    @Test
    fun `cancelled event refunds stake and does not grow cap`() {
        val e = engine()
        val base = GameState.newGame("cancel", 1L).copy(phase = Phase.DayEntry)
        val placed = e.apply(base, Command.PlaceBet(base.revision, base.saveId, "p", "cancel", "a", "500", 1)).newState
        val cancelled = e.apply(placed.copy(position = placed.position.copy(current = com.example.hrrepayment.core.model.WorkdayRef(1))), Command.SettleBet(placed.revision, placed.saveId, "s", "cancel", "", eventCancelled = true)).newState
        assertEquals(base.cash, cancelled.cash)
        assertEquals(base.bettingCap, cancelled.bettingCap)
        assertEquals(0, cancelled.acceptedBetCount)
    }

    @Test
    fun `browsing updates the candidate submitted by the fixed button`() {
        val e = engine()
        var s = started()
        s = e.apply(s, Command.BrowseCandidate(s.revision, s.saveId, "b1", 1)).newState
        assertEquals(1, s.browsedCandidateIndex)
        assertEquals(TestContent.day0Second, s.selectedCandidateId)

        s = s.select(e, 1, TestContent.day0Second)
        assertEquals(TestContent.day0Second, s.selectedCandidateId)

        // 固定按钮始终对应当前页。
        s = e.apply(s, Command.BrowseCandidate(s.revision, s.saveId, "b2", 0)).newState
        assertEquals(0, s.browsedCandidateIndex)
        assertEquals(TestContent.day0First, s.selectedCandidateId)
    }

    @Test
    fun `browse out of range and unknown selection are rejected`() {
        val e = engine()
        val s = started()
        assertFailsWith<EngineError.InvalidCommand> {
            e.apply(s, Command.BrowseCandidate(s.revision, s.saveId, "b", 2))
        }
        assertFailsWith<EngineError.InvalidCommand> {
            e.apply(s, Command.SelectCandidate(s.revision, s.saveId, "b", "ghost"))
        }
    }

    @Test
    fun `submit requires a valid statement and the current visible candidate`() {
        val e = engine()
        val s = started()
        assertFailsWith<EngineError.InvalidCommand> {
            e.apply(s, Command.SubmitCandidate(s.revision, s.saveId, "c", TestContent.day0First))
        }
        val selected = s.select(e, 1, TestContent.day0First)
        assertFailsWith<EngineError.InvalidCommand> {
            e.apply(selected, Command.SubmitCandidate(selected.revision, selected.saveId, "c", TestContent.day0Second, "school"))
        }
    }

    @Test
    fun `failure uses chance bps from content and writes the failure message`() {
        val calls = mutableListOf<Int>()
        val random = ScriptedRandomSource(listOf(false), calls)
        val e = engine(probs = listOf(2100) + List(11) { 5000 }, random = random)
        var s = started(random)
        s = s.select(e, 1, TestContent.day0First).submit(e, 1)
        assertEquals(listOf(2100), calls)
        assertEquals(Phase.ResultAck, s.phase)
        assertEquals(false, s.lastResultSuccess)
        // v3: the result text is no longer stored in the state; the structured fields
        // are what the UI renders (and what survives a language switch).
        assertEquals(false, s.lastReviewCorrect)
        assertEquals(TestContent.day0First, s.lastResultCandidateId)
        assertEquals(0, s.lastLuckUsed)
        assertEquals(1, s.stats.successiveFailureCount)
        assertEquals(1, s.stats.globalFailureCount)
    }

    @Test
    fun `success clears consecutive failure count and a lone failure costs nothing`() {
        val random = ScriptedRandomSource(listOf(true, false))
        val e = engine(random = random)
        var s = started(random)
        s = s.select(e, 1, TestContent.day0First).submit(e, 1)
        assertEquals(0, s.stats.successiveFailureCount)
        assertEquals(1, s.stats.globalSuccessCount)
        assertEquals(true, s.lastResultSuccess)
        assertTrue(s.hired.any { it.candidateId == TestContent.day0First })

        s = s.ack(e, 2).enter(e, 3).select(e, 4, TestContent.id(2)).submit(e, 5)
        assertEquals(1, s.stats.successiveFailureCount)
        assertEquals(3, s.attributes.trust) // 单次失败不扣信任
    }

    // ------------------------------------------------------------ 保底档位

    @Test
    fun `work below 20 has no guarantee - five failures never force success`() {
        val random = ScriptedRandomSource(List(64) { false })
        val e = engine(random = random)
        var s = started(random).copy(attributes = started(random).attributes.copy(work = 19))
        for (i in 0 until 5) {
            s = s.playDay(e, i)
            assertEquals(false, s.lastResultSuccess)
            assertEquals(false, s.lastResultForced)
        }
        assertEquals(0, s.guaranteeTier)
    }

    @Test
    fun `work 20 forces success on every 5th submission`() {
        val random = ScriptedRandomSource(List(64) { false })
        val e = engine(random = random)
        var s = started(random).copy(attributes = started(random).attributes.copy(work = 20))
        for (i in 0 until 4) {
            s = s.playDay(e, i)
            assertEquals(false, s.lastResultSuccess)
        }
        assertEquals(20, s.guaranteeTier)
        assertEquals(4, s.guaranteePeriodDraws)
        s = s.playDay(e, 4) // 第 5 次提交
        assertEquals(true, s.lastResultSuccess)
        assertEquals(true, s.lastResultForced)
        assertEquals(0, s.guaranteePeriodDraws) // 周期重新计数
    }

    @Test
    fun `work 30 gives period 4 and work 50 gives period 3`() {
        val random = ScriptedRandomSource(List(64) { false })
        val e = engine(random = random)

        var s = started(random).copy(attributes = started(random).attributes.copy(work = 30))
        for (i in 0 until 3) {
            s = s.playDay(e, i)
            assertEquals(false, s.lastResultForced)
        }
        s = s.playDay(e, 3) // 第 4 次提交
        assertEquals(true, s.lastResultForced)
        assertEquals(30, s.guaranteeTier)

        var t = started(random).copy(attributes = started(random).attributes.copy(work = 50))
        for (i in 0 until 2) {
            t = t.playDay(e, 10 + i)
            assertEquals(false, t.lastResultForced)
        }
        t = t.playDay(e, 12) // 第 3 次提交
        assertEquals(true, t.lastResultForced)
        assertEquals(50, t.guaranteeTier)
    }

    @Test
    fun `tier boundaries are exactly 20 30 50`() {
        val random = ScriptedRandomSource(List(64) { false })
        val e = engine(random = random)
        val s29 = started(random)
            .copy(attributes = started(random).attributes.copy(work = 29))
            .select(e, 1, TestContent.day0First).submit(e, 1)
        assertEquals(20, s29.guaranteeTier)
        val s49 = started(random)
            .copy(attributes = started(random).attributes.copy(work = 49))
            .select(e, 2, TestContent.day0First).submit(e, 2)
        assertEquals(30, s49.guaranteeTier)
    }

    @Test
    fun `upgrading the tier mid-cycle restarts the count from one`() {
        val random = ScriptedRandomSource(List(64) { false })
        val e = engine(random = random)
        var s = started(random).copy(attributes = started(random).attributes.copy(work = 20))
        s = s.playDay(e, 0)
        s = s.playDay(e, 1)
        assertEquals(2, s.guaranteePeriodDraws)

        // 外部把工作能力升到 31（模拟上次成功领奖升级）：下一次提交周期重新从 1 开始
        s = s.copy(attributes = s.attributes.copy(work = 31))
        s = s.playDay(e, 2)
        assertEquals(30, s.guaranteeTier)
        assertEquals(1, s.guaranteePeriodDraws)
    }

    @Test
    fun `random success does not reset the fixed period`() {
        // 第 1 笔随机成功（true），第 2-4 笔失败，第 5 笔强制成功
        val mixed = ScriptedRandomSource(listOf(true, false, false, false, false))
        val em = engine(random = mixed)
        var s = started(mixed).copy(attributes = started(mixed).attributes.copy(work = 20))
        s = s.playDay(em, 0)
        assertEquals(true, s.lastResultSuccess)
        assertEquals(false, s.lastResultForced)
        assertEquals(1, s.guaranteePeriodDraws)
        s = s.playDay(em, 1)
        s = s.playDay(em, 2)
        s = s.playDay(em, 3)
        assertEquals(4, s.guaranteePeriodDraws)
        s = s.playDay(em, 4)
        assertEquals(true, s.lastResultForced)
        assertTrue(s.stats.globalSuccessCount >= 2)
    }

    @Test
    fun `forced success does not consume a random draw`() {
        val calls = mutableListOf<Int>()
        val random = ScriptedRandomSource(List(64) { false }, calls)
        val e = engine(random = random)
        var s = started(random).copy(attributes = started(random).attributes.copy(work = 50))
        s = s.playDay(e, 0)
        s = s.playDay(e, 1)
        assertEquals(2, calls.size) // 前两次是概率抽取
        s = s.playDay(e, 2) // 第 3 次提交强制成功
        assertEquals(true, s.lastResultForced)
        assertEquals(2, calls.size) // 强制成功不抽签
    }

    // ------------------------------------------------------------ 信任与结局

    @Test
    fun `consecutive failures 2 costs 1 trust and 4 costs 2 more`() {
        val random = ScriptedRandomSource(List(64) { false })
        val e = engine(random = random)
        var s = started(random)
        s = s.playDay(e, 0)
        assertEquals(3, s.attributes.trust) // 第 1 次失败不扣
        s = s.playDay(e, 1)
        assertEquals(2, s.attributes.trust) // 连败 2 → -1
        assertEquals(1, s.lastResultTrustPenalty)

        s = s.playDay(e, 2)
        assertEquals(2, s.attributes.trust) // 连败 3 → 不再扣
        s = s.playDay(e, 3)
        assertEquals(0, s.attributes.trust) // 连败 4 → -2；信任 0 存活
        assertEquals(2, s.lastResultTrustPenalty)
    }

    @Test
    fun `trust below zero ends the run as exposed while debt remains`() {
        val random = ScriptedRandomSource(List(64) { false })
        val e = engine(random = random)
        var s = started(random)
        for (i in 0 until 6) {
            s = s.playDay(e, i)
        }
        // 连败 2→-1，4→-2，6→-2：信任 3-1-2-2 = -2
        assertEquals(-2, s.attributes.trust)
        assertEquals(Phase.Ended, s.phase)
        assertEquals("exposed", s.endingId)
        assertEquals("trust_negative", s.endingReason)
    }

    @Test
    fun `trust below zero with zero debt ends as debt_free_restart`() {
        val random = ScriptedRandomSource(List(64) { false })
        val e = engine(random = random)
        var s = started(random).copy(debt = Money(0L))
        for (i in 0 until 6) {
            s = s.playDay(e, i)
        }
        assertEquals(Phase.Ended, s.phase)
        assertEquals("debt_free_restart", s.endingId)
    }

    // ------------------------------------------------------------ 三成功奖励

    @Test
    fun `every third global success forces an immediate reward choice`() {
        val e = engine(random = ScriptedRandomSource(List(64) { true }))
        var s = started()

        s = s.select(e, 1, TestContent.day0First).submit(e, 1)
        s = s.ack(e, 2)
        assertEquals(Phase.DayEntry, s.phase) // 成功 1：不领奖

        s = s.enter(e, 3).select(e, 4, TestContent.id(2)).submit(e, 5)
        s = s.ack(e, 6)
        assertEquals(Phase.DayEntry, s.phase) // 成功 2：不领奖

        s = s.enter(e, 7).select(e, 8, TestContent.id(4)).submit(e, 9)
        s = s.ack(e, 10)
        assertEquals(Phase.SuccessReward, s.phase) // 成功 3：领奖

        s = s.reward(e, 11, Command.SuccessRewardOption.WORK)
        assertEquals(Phase.DayEntry, s.phase)
        assertEquals(2, s.attributes.work)
    }

    @Test
    fun `a failure after a completed six-success milestone cannot reopen reward`() {
        val e = engine()
        val s = started().copy(
            phase = Phase.ResultAck,
            lastResultSuccess = false,
            stats = started().stats.copy(globalSuccessCount = 6, successRewardPending = 1),
        )
        val next = e.apply(s, Command.AcknowledgeResult(s.revision, s.saveId, "failure-ack")).newState
        assertEquals(Phase.DayEntry, next.phase)
        assertEquals(0, next.stats.successRewardPending)
    }

    @Test
    fun `six successes then a restored failure pays only the third sixth and ninth milestones`() {
        val e = engine(random = ScriptedRandomSource(List(6) { true } + false + List(3) { true }))
        var s = started()
        for (day in 0 until 6) s = s.playDay(e, day)
        assertEquals(6, s.stats.globalSuccessCount)
        assertEquals(3, s.attributes.work)
        s = e.apply(s, Command.FinishIncome(s.revision, s.saveId, "income")).newState
        s = e.apply(s, Command.RepayDebt(s.revision, s.saveId, "repay", "0")).newState
        s = e.apply(s, Command.ChooseMonthlySkill(s.revision, s.saveId, "skill", Command.MonthlySkillOption.HEALTH)).newState
        s = s.enter(e, 100).select(e, 101, TestContent.id(12)).submit(e, 102)
        assertEquals(false, s.lastResultSuccess)
        assertEquals(6, s.stats.globalSuccessCount)
        s = Json.decodeFromString<GameState>(Json.encodeToString(s))
        s = s.ack(e, 103)
        assertEquals(Phase.DayEntry, s.phase)
        assertEquals(3, s.attributes.work)
        for (day in 7 until 10) s = s.playDay(e, day)
        assertEquals(9, s.stats.globalSuccessCount)
        assertEquals(4, s.attributes.work)
        assertEquals(0, s.stats.successRewardPending)
    }

    @Test
    fun `luck adds one percentage point per unit only to the next submission`() {
        val calls = mutableListOf<Int>()
        val e = engine(probs = List(12) { 5200 }, random = ScriptedRandomSource(listOf(false, false), calls))
        var s = started().copy(attributes = started().attributes.copy(luck = 3, luckPeak = 3), useLuckOnSubmission = true)
        s = s.select(e, 1, TestContent.day0First).submit(e, 2)
        assertEquals(listOf(5500), calls)
        assertEquals(0, s.attributes.luck)
        assertEquals(3, s.attributes.luckPeak)
        s = Json.decodeFromString<GameState>(Json.encodeToString(s))
        s = s.ack(e, 3).enter(e, 4).select(e, 5, TestContent.id(2)).submit(e, 6)
        assertEquals(listOf(5500, 5200), calls)
    }

    @Test
    fun `luck bonus is capped at ten points and total ordinary chance at ninety-five percent`() {
        for ((base, expected) in listOf(2100 to 3100, 9000 to 9000, 10000 to 9000)) {
            val calls = mutableListOf<Int>()
            val e = engine(probs = List(12) { base }, random = ScriptedRandomSource(listOf(false), calls))
            val s = started().copy(attributes = started().attributes.copy(luck = Int.MAX_VALUE), useLuckOnSubmission = true)
                .select(e, 1, TestContent.day0First).submit(e, 2)
            assertEquals(listOf(expected), calls)
            assertEquals(0, s.attributes.luck)
        }
    }

    @Test
    fun `guaranteed submissions preserve luck without drawing and invalid submissions keep it`() {
        val calls = mutableListOf<Int>()
        val e = engine(random = ScriptedRandomSource(listOf(false), calls))
        val s = started().copy(attributes = started().attributes.copy(work = 50, luck = 2), guaranteeTier = 50, guaranteePeriodDraws = 2, useLuckOnSubmission = true)
        assertFailsWith<EngineError.InvalidCommand> {
            e.apply(s, Command.SubmitCandidate(s.revision, s.saveId, "invalid", TestContent.day0First))
        }
        assertEquals(2, s.attributes.luck)
        val result = s.select(e, 1, TestContent.day0First).submit(e, 2)
        assertEquals(true, result.lastResultForced)
        assertEquals(2, result.attributes.luck)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `reward trust option raises trust and counters survive the month boundary`() {
        val e = engine(random = ScriptedRandomSource(List(64) { true }))
        var s = started()
        s = s.playDay(e, 0)
        s = s.playDay(e, 1)
        s = s.playDay(e, 2)
        // 三成功 → 领奖面板出现在第 3 天 ack 之后；playDay 默认选 WORK，
        // 这里改成手工走一遍 TRUST：
        var t = started()
        t = t.select(e, 1, TestContent.day0First).submit(e, 1)
        t = t.ack(e, 2).enter(e, 3).select(e, 4, TestContent.id(2)).submit(e, 5)
        t = t.ack(e, 6).enter(e, 7).select(e, 8, TestContent.id(4)).submit(e, 9)
        t = t.ack(e, 10)
        assertEquals(Phase.SuccessReward, t.phase)
        t = t.reward(e, 11, Command.SuccessRewardOption.TRUST)
        assertEquals(4, t.attributes.trust)
        assertEquals(Phase.DayEntry, t.phase)

        // 打满本月剩余成功，月末进入 MonthIncome 时全局计数不清零
        for (i in 3 until 6) {
            t = t.playDay(e, 100 + i)
        }
        assertEquals(Phase.MonthIncome, t.phase)
        assertEquals(6, t.stats.globalSuccessCount)
    }

    // ------------------------------------------------------------ 日推进

    @Test
    fun `acknowledging the sixth day result enters MonthIncome as a P3 placeholder`() {
        val e = engine(random = ScriptedRandomSource(List(64) { true }))
        var s = started()
        for (i in 0 until 6) {
            s = s.playDay(e, 1000 + i)
        }
        assertEquals(Phase.MonthIncome, s.phase)
        assertEquals(5, s.position.current.dayOfMonth)
        assertEquals(5, s.position.current.index)
        assertEquals("2016-07-29", TestContent.ref().workdayDisplayDate(s.position.current.index))
        // 月末面板（P3）不接受招聘命令
        assertFailsWith<EngineError.WrongPhase> {
            e.apply(s, Command.EnterRecruiting(s.revision, s.saveId, "nope"))
        }
    }

    @Test
    fun `selection is cleared when a new workday starts`() {
        val e = engine(random = ScriptedRandomSource(List(64) { true }))
        var s = started()
        s = s.select(e, 1, TestContent.day0First).submit(e, 1)
        s = s.ack(e, 2)
        assertEquals(Phase.DayEntry, s.phase)
        assertNull(s.selectedCandidateId)
        assertEquals(0, s.browsedCandidateIndex)
        assertEquals(1, s.position.current.index)
    }

    // ------------------------------------------------------------ 阶段与恢复

    @Test
    fun `commands outside their phase are rejected`() {
        val e = engine()
        val s = started()
        assertFailsWith<EngineError.WrongPhase> {
            e.apply(s, Command.AcknowledgeResult(s.revision, s.saveId, "x"))
        }
        val day = s.copy(phase = Phase.DayEntry)
        assertFailsWith<EngineError.WrongPhase> {
            e.apply(day, Command.SubmitCandidate(day.revision, day.saveId, "x", TestContent.day0First))
        }
    }

    @Test
    fun `a save restored into a fresh engine continues the same random sequence`() {
        val probs = List(12) { 5000 }
        val engineA = GameEngine(TestContent.ref(probs), SplitMix64Random(77L))
        var a = engineA.apply(null, Command.StartNewGame(0L, Command.NO_SAVE_ID, "c0", "s", 77L)).newState
        a = engineA.apply(a, Command.AcknowledgeOpening(a.revision, a.saveId, "c1")).newState
        a = engineA.apply(a, Command.EnterRecruiting(a.revision, a.saveId, "c2")).newState
        a = engineA.apply(a, Command.SelectCandidate(a.revision, a.saveId, "c3", TestContent.day0First)).newState
        a = engineA.apply(a, Command.MarkStatement(a.revision, a.saveId, "c3-mark", TestContent.day0First, "school")).newState
        a = engineA.apply(a, Command.SubmitCandidate(a.revision, a.saveId, "c4", TestContent.day0First, "school")).newState

        // “杀进程”：两个不同种子的引擎从同一存档状态继续，必须得到同一结果
        val b = GameEngine(TestContent.ref(probs), SplitMix64Random(1L))
            .apply(a, Command.AcknowledgeResult(a.revision, a.saveId, "c5")).newState
        val c = GameEngine(TestContent.ref(probs), SplitMix64Random(2L))
            .apply(a, Command.AcknowledgeResult(a.revision, a.saveId, "c6")).newState
        assertEquals(b.stats, c.stats)
        assertEquals(b.lastResultSuccess, c.lastResultSuccess)
        assertEquals(b.rngState, c.rngState)
    }

    @Test
    fun `month one content exposes real dates for all six days`() {
        val ref = TestContent.ref()
        assertEquals("2016-07-04", ref.workdayDisplayDate(0))
        assertEquals("2016-07-29", ref.workdayDisplayDate(5))
        assertEquals(2, ref.workdayCandidates(0).size)
    }
}
