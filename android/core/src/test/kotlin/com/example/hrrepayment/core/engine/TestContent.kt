package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.content.CandidateContent
import com.example.hrrepayment.core.content.ExportedContentRef
import com.example.hrrepayment.core.content.MonthContent
import com.example.hrrepayment.core.content.WorkdayContent
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.content.ReviewStatement
import com.example.hrrepayment.core.content.ReviewEvidence
import com.example.hrrepayment.core.content.JobDemand

/**
 * 测试内容：与真实导出的首月同构（6 个工作日、每天两份简历、性别与概率
 * 覆盖四种音效池组合），但概率可由测试自行指定。
 */
object TestContent {

    const val VERSION = GameState.EXPECTED_CONTENT_VERSION

    fun month(
        probs: List<Int> = List(12) { 5000 },
        full: Boolean = false,
        genders: List<String> = listOf(
            "male", "female",
            "female", "male",
            "male", "female",
            "male", "male",
            "female", "female",
            "female", "female",
        ),
    ): MonthContent {
        val dates = listOf(
            "2016-07-04", "2016-07-11", "2016-07-15",
            "2016-07-27", "2016-07-28", "2016-07-29",
        )
        val roles = listOf(
            "招聘运营专员", "人力数据分析师", "内容运营专员",
            "商业化产品专员", "Java开发工程师", "Python数据工程师",
        )
        val candidates = (0 until if (full) 504 else 12).map { i ->
            val day = i / 2
            val gender = genders.getOrElse(i % genders.size) { "male" }
            CandidateContent(
                id = id(i),
                workdayIndex = day,
                resumeNo = "resume_%03d".format(i + 1),
                role = roles[day % roles.size],
                name = "测试候选人${i + 1}",
                gender = gender,
                genderLabel = if (gender == "male") "男" else "女",
                age = 25 + i,
                avatarAsset = "avatar/avatar_01.png",
                educationSkills = "学历技能（测试 ${i + 1}）",
                workHistory = "工作经历（测试 ${i + 1}）",
                fraudType = "项目造假",
                fraudLevel = "moderate",
                fraudField = "工作经历",
                uniqueLie = "唯一假话（测试 ${i + 1}）",
                truth = "真实情况（测试 ${i + 1}）",
                judgementBasis = "判定依据（测试 ${i + 1}）",
                successBps = probs.getOrElse(i % probs.size) { 5000 },
                statements = listOf("education-form", "school", "major", "education-dates", "work-claim").map {
                    ReviewStatement(it, if (it == "work-claim") "workExperience" else "educationSkills",
                        if (it == "work-claim") "唯一假话（测试 ${i + 1}）" else "已核对的测试信息 $it")
                },
                evidence = listOf(ReviewEvidence("academic", "学籍", "测试学籍记录"), ReviewEvidence("work", "任职", "测试任职记录")),
                lieStatementId = "work-claim",
                supportingEvidenceIds = listOf("work"),
            )
        }
        return MonthContent(
            contentVersion = VERSION,
            rulesVersion = GameState.EXPECTED_RULES_VERSION,
            exportedFrom = "test",
            openingTitle = "游戏开始了。",
            openingBody = "开场正文（测试）。",
            workdays = (0 until if (full) 252 else 6).map { d ->
                WorkdayContent(
                    index = d,
                    date = dates.getOrNull(d) ?: "2017-${(d / 21 + 1).toString().padStart(2, '0')}-${(d % 21 + 1).toString().padStart(2, '0')}",
                    role = roles[d % roles.size],
                    candidateIds = listOf(id(d * 2), id(d * 2 + 1)),
                    demand = JobDemand("测试技能", "测试经历", "测试任务"),
                )
            },
            candidates = candidates,
        )
    }

    fun ref(probs: List<Int> = List(12) { 5000 }): com.example.hrrepayment.core.content.ContentRef =
        object : com.example.hrrepayment.core.content.ContentRef by ExportedContentRef(month(probs, full = true)) {
            override fun bettingMarkets() = listOf(
                com.example.hrrepayment.core.content.BettingMarket("m1", "Test", 0, 1, "n1", listOf(com.example.hrrepayment.core.content.BetOption("home", "Home"), com.example.hrrepayment.core.content.BetOption("a", "Away")), "home"),
                com.example.hrrepayment.core.content.BettingMarket("m2", "Test", 1, 2, "n2", listOf(com.example.hrrepayment.core.content.BetOption("a", "A"), com.example.hrrepayment.core.content.BetOption("b", "B")), "a"),
                com.example.hrrepayment.core.content.BettingMarket("cancel", "Cancelled", 0, 1, "n1", listOf(com.example.hrrepayment.core.content.BetOption("a", "A"), com.example.hrrepayment.core.content.BetOption("b", "B")), null, true),
            )
        }

    fun id(i: Int): String = "m1d%03d%03d".format(i / 2 + 1, i + 1)

    val day0First: String get() = id(0)
    val day0Second: String get() = id(1)
}

/** 剧本随机源：按序返回 chance 结果并记录调用；强制场景不调用即不推进。 */
class ScriptedRandomSource(
    private val outcomes: List<Boolean>,
    private val onChance: MutableList<Int>? = null,
) : com.example.hrrepayment.core.random.RandomSource {
    private var cursor = 0
    private var snapshot = com.example.hrrepayment.core.random.RandomSnapshot.atSeed(1L)

    override fun nextInt(from: Int, until: Int): Int = from
    override fun nextLong(): Long = 1L
    override fun chance(bps: Int): Boolean {
        onChance?.add(bps)
        val v = outcomes.getOrNull(cursor) ?: outcomes.last()
        cursor += 1
        return v
    }

    override fun snapshot() = snapshot
    override fun restore(snapshot: com.example.hrrepayment.core.random.RandomSnapshot) {
        this.snapshot = snapshot
    }
}
