package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.content.*
import com.example.hrrepayment.core.model.*
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.random.SplitMix64Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import org.junit.Test
import kotlin.test.*

/** Runs the real engine and packaged content; strategies never edit a game state. */
class FullGameSimulationTest {
    private val assets = File("../app/src/main/assets")
    private val content: ContentRef by lazy {
        val base = ExportedMonthLoader.load(
            monthBytes = File(assets, "content/month1.json").readBytes(),
            manifestBytes = File(assets, "content/manifest.json").readBytes(),
            lang = com.example.hrrepayment.core.i18n.Lang.ZH,
            openAsset = { require(File(assets, it).isFile) },
        )
        val betting = File(assets, "content/betting.json").readBytes()
        BettingContentRef(base, BettingContentLoader.load(betting))
    }
    private data class Strategy(val name: String, val bet: String = "none", val stake: String = "minimum",
        val review: String = "correct", val candidate: String = "fit", val reward: String = "balanced",
        val skill: String = "balanced", val luck: String = "immediate", val repay: String = "keep6000")
    private val strategies = listOf(
        Strategy("no_bets_correct"), Strategy("random_review", review = "random"),
        Strategy("low_fraud_only", candidate = "fraud"), Strategy("work_first", reward = "work"),
        Strategy("trust_first", reward = "trust"), Strategy("overseas_target", reward = "overseas"),
        Strategy("all_correct_min", bet = "correct"), Strategy("all_correct_max", bet = "correct", stake = "maximum"),
        Strategy("all_correct_reserve", bet = "correct", stake = "reserve"), Strategy("all_wrong_min", bet = "wrong"),
        Strategy("all_wrong_max", bet = "wrong", stake = "maximum"), Strategy("alternating", bet = "alternating"),
        Strategy("skip_half", bet = "skip", stake = "maximum"),
        Strategy("save_luck", bet = "wrong", luck = "saved"),
        Strategy("farm_peak_then_win", bet = "farm", stake = "maximum"),
        Strategy("never_health", bet = "correct", stake = "maximum", skill = "english"),
        Strategy("cold_unprepared", bet = "correct", stake = "maximum", skill = "dopamine", reward = "trust"),
        Strategy("debt_remaining", bet = "correct", stake = "maximum", reward = "trust", repay = "minimum")
    )
    private data class Run(val state: GameState, val skillCount: Int, val betNet: Long, val commands: List<String>)

    private fun play(seed: Long, strategy: Strategy, restoreEveryCommand: Boolean = false): Run {
        val choices = SplitMix64Random(seed xor 0x43484f494345L)
        var engine = GameEngine(content, SplitMix64Random(seed))
        var state: GameState? = null
        var sequence = 0
        var skills = 0
        var betNet = 0L
        val trace = mutableListOf<String>()
        fun apply(build: (GameState?, String) -> Command) {
            val before = state
            val command = build(before, "c${sequence++}")
            val next = engine.apply(before, command).newState
            if (command is Command.PlaceBet) betNet -= next.activeBet!!.amount.cents
            if (before?.activeBet != null && next.activeBet == null) betNet += next.cash.cents - before.cash.cents
            if (restoreEveryCommand) {
                state = Json.decodeFromString<GameState>(Json.encodeToString(next))
                engine = GameEngine(content, SplitMix64Random(-1))
            } else state = next
            trace += "${next.position.current.index},${command::class.simpleName},${next.phase},${next.cash.cents},${next.debt.cents}"
        }
        apply { _, id -> Command.StartNewGame(0, Command.NO_SAVE_ID, id, "simulation-$seed", seed) }
        while (state!!.phase != Phase.Ended) {
            assertTrue(sequence < 3000, "state machine did not terminate: ${strategy.name} $seed ${state!!.phase}")
            val s = state!!
            when (s.phase) {
                Phase.Opening -> apply { _, id -> Command.AcknowledgeOpening(s.revision, s.saveId, id) }
                Phase.DayEntry -> {
                    val market = content.bettingMarkets().firstOrNull { it.openWorkdayIndex == s.position.current.index && it.id !in s.settledBetMarketIds }
                    val ordinal = market?.let { content.bettingMarkets().indexOf(it) } ?: -1
                    val wantsBet = strategy.bet != "none" && !(strategy.bet == "skip" && ordinal % 2 == 0)
                    val farming = strategy.bet == "farm" && ordinal < 10
                    val available = when {
                        farming || strategy.stake == "minimum" -> 50_000L
                        strategy.stake == "reserve" -> (s.cash.cents - 600_000).coerceAtLeast(0)
                        else -> (s.cash.cents - 1).coerceAtLeast(0)
                    }
                    val amount = minOf(available, GameEngine.effectiveBetCap(s).cents, s.cash.cents - 1)
                    if (market != null && wantsBet && s.activeBet == null && amount >= 50_000) {
                        val correct = when (strategy.bet) {
                            "wrong" -> false
                            "alternating" -> ordinal % 2 == 0
                            "farm" -> !farming
                            else -> true
                        }
                        val option = if (correct) market.correctOptionId!! else market.options.first { it.id != market.correctOptionId }.id
                        apply { _, id -> Command.PlaceBet(s.revision, s.saveId, id, market.id, option, Money(amount).formatYuan(), market.settleWorkdayIndex) }
                    } else apply { _, id -> Command.EnterRecruiting(s.revision, s.saveId, id) }
                }
                Phase.EventNotice -> apply { _, id -> Command.AcknowledgeEvent(s.revision, s.saveId, id, s.pendingEventId!!) }
                Phase.Recruiting -> {
                    val candidates = content.workdayCandidates(s.position.current.index)
                    val candidate = if (strategy.candidate == "fraud") candidates.minBy { listOf("minor", "moderate", "severe").indexOf(it.fraudLevel) }
                        else candidates.maxBy { it.successBps }
                    if (s.browsedCandidateIndex != candidates.indexOf(candidate)) {
                        apply { _, id -> Command.BrowseCandidate(s.revision, s.saveId, id, candidates.indexOf(candidate)) }
                    } else if (candidate.id !in s.markedStatements) {
                        val statement = if (strategy.review == "random") candidate.statements[choices.nextInt(0, candidate.statements.size)].id else candidate.lieStatementId
                        apply { _, id -> Command.MarkStatement(s.revision, s.saveId, id, candidate.id, statement) }
                    } else if (s.attributes.luck > 0 && !s.useLuckOnSubmission && !GameEngine.isForcedNext(s) &&
                        (strategy.luck == "immediate" || s.attributes.luck == 10)) {
                        apply { _, id -> Command.SetLuckUsage(s.revision, s.saveId, id, true) }
                    } else apply { _, id -> Command.SubmitCandidate(s.revision, s.saveId, id, candidate.id, s.markedStatements.getValue(candidate.id)) }
                }
                Phase.ResultAck -> apply { _, id -> Command.AcknowledgeResult(s.revision, s.saveId, id) }
                Phase.SuccessReward -> {
                    val trust = when (strategy.reward) {
                        "trust" -> true
                        "work" -> false
                        "overseas" -> s.attributes.work >= 20 && s.attributes.trust < 12
                        else -> s.attributes.trust < 10
                    }
                    apply { _, id -> Command.ChooseSuccessReward(s.revision, s.saveId, id, if (trust) Command.SuccessRewardOption.TRUST else Command.SuccessRewardOption.WORK) }
                }
                Phase.MonthIncome -> apply { _, id -> Command.FinishIncome(s.revision, s.saveId, id) }
                Phase.Repayment -> {
                    val minimum = FinancialPreview.minimumAnnualRepayment(s)?.cents ?: 0L
                    val amount = if (strategy.repay == "minimum") minOf(minimum, s.cash.cents - 1).coerceAtLeast(0)
                        else FinancialPreview.preset(s, RepaymentPreset.KEEP_6000).cents
                    apply { _, id -> Command.RepayDebt(s.revision, s.saveId, id, Money(amount).formatYuan()) }
                }
                Phase.InterestCheck -> apply { _, id -> Command.FinishEconomy(s.revision, s.saveId, id) }
                Phase.MonthlySkill -> {
                    val skill = when (strategy.skill) {
                        "english" -> Command.MonthlySkillOption.ENGLISH
                        "dopamine" -> Command.MonthlySkillOption.DOPAMINE
                        else -> when (s.position.current.monthIndex % 6) {
                            0, 3 -> Command.MonthlySkillOption.HEALTH
                            1, 2 -> Command.MonthlySkillOption.ENGLISH
                            else -> Command.MonthlySkillOption.DOPAMINE
                        }
                    }
                    skills++
                    apply { _, id -> Command.ChooseMonthlySkill(s.revision, s.saveId, id, skill) }
                }
                else -> error("Unimplemented reachable phase: ${s.phase}")
            }
        }
        return Run(state!!, skills, betNet, trace)
    }

    @Test fun allContentAndActualAuthoredAnswersArePackaged() {
        assertEquals(252, content.totalWorkdays)
        val candidates = (0..251).flatMap { content.workdayCandidates(it) }
        assertEquals(504, candidates.size)
        assertEquals(2898, candidates.sumOf { it.statements.size })
        assertEquals(1278, candidates.sumOf { it.evidence.size })
        assertEquals(504, (0..251).sumOf { content.newsForWorkday(it).size })
        assertEquals(41, content.bettingMarkets().size)
        assertTrue((0..251).all { content.workdayInfo(it).demand != null })
    }

    @Test fun processRestoreAtEveryCommandProducesTheSameCompleteRun() {
        val strategy = strategies.first { it.name == "all_correct_max" }
        val uninterrupted = play(22, strategy)
        val restored = play(22, strategy, true)
        assertEquals(uninterrupted.state, restored.state)
        assertEquals(uninterrupted.commands, restored.commands)
        assertEquals(uninterrupted.skillCount, restored.skillCount)
    }

    @Test fun eighteenStrategiesRunOneThousandSeedsEachUntilTheirActualEnding() {
        val report = File("build/reports/prerelease").apply { mkdirs() }
        val rows = mutableListOf("strategy,seed,ending,reason,day,date,cash_cents,debt_cents,successes,failures,correct_reviews,review_count,work,trust,health,dopamine,english,luck_peak,skills,bet_net_cents")
        val found = mutableSetOf<String>()
        val summaries = mutableListOf<String>()
        for (strategy in strategies) {
            val counts = mutableMapOf<String, Int>()
            for (seed in 1L..1000L) {
                val run = play(seed, strategy)
                val s = run.state
                val ending = s.endingId!!
                counts[ending] = counts.getOrDefault(ending, 0) + 1
                if (found.add(ending)) File(report, "path-$ending.csv").writeText("day,command,phase,cash_cents,debt_cents\n" + run.commands.joinToString("\n") + "\n")
                if (s.position.current == WorkdayRef.LAST) assertEquals(41, run.skillCount)
                rows += listOf(strategy.name, seed, ending, s.endingReason, s.position.current.index + 1,
                    content.workdayDisplayDate(s.position.current.index), s.cash.cents, s.debt.cents,
                    s.stats.globalSuccessCount, s.stats.globalFailureCount, s.stats.correctReviewCount, s.stats.reviewedSubmissionCount,
                    s.attributes.work, s.attributes.trust, s.attributes.health, s.attributes.dopamine, s.attributes.english,
                    s.attributes.luckPeak, run.skillCount, run.betNet).joinToString(",")
                assertFailsWith<EngineError.WrongPhase> {
                    GameEngine(content, SplitMix64Random(1)).apply(s, Command.EnterRecruiting(s.revision, s.saveId, "ended"))
                }
            }
            summaries += "${strategy.name}: $counts"
        }
        File(report, "simulation.csv").writeText(rows.joinToString("\n") + "\n")
        File(report, "summary.txt").writeText(summaries.joinToString("\n") + "\nReachable endings in sample: $found\n")
        println(summaries.joinToString("\n"))
        println("Observed ${found.size} endings from normal starts: $found")
    }
}
