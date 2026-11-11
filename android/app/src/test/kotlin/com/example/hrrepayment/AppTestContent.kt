package com.example.hrrepayment

import com.example.hrrepayment.core.content.CandidateContent
import com.example.hrrepayment.core.content.ExportedContentRef
import com.example.hrrepayment.core.content.MonthContent
import com.example.hrrepayment.core.content.WorkdayContent
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.content.ReviewStatement
import com.example.hrrepayment.core.content.ReviewEvidence
import com.example.hrrepayment.core.content.JobDemand

/**
 * app 层测试内容：与导出 JSON 相同的 DTO，但概率可控制
 * （day0：男 0bps 必失败 / 女高成功率），性别组合覆盖四个音频池。
 */
object AppTestContent {

    private data class Slot(val id: String, val gender: String, val bps: Int)

    private val table: List<Pair<Int, List<Slot>>> = listOf(
        0 to listOf(Slot("c00", "male", 0), Slot("c01", "female", 10000)), // 一男一女
        1 to listOf(Slot("c10", "male", 5000), Slot("c11", "male", 5000)), // 两男
        2 to listOf(Slot("c20", "female", 5000), Slot("c21", "female", 5000)), // 两女
        3 to listOf(Slot("c30", "female", 5000), Slot("c31", "male", 5000)), // 一女一男
        4 to listOf(Slot("c40", "male", 5000), Slot("c41", "female", 5000)),
        5 to listOf(Slot("c50", "male", 5000), Slot("c51", "male", 5000)),
    )

    fun month(): MonthContent {
        val days = table.map { (index, slots) ->
            WorkdayContent(
                index = index,
                date = "2016-07-%02d".format(4 + index * 5),
                role = "内容审核",
                candidateIds = slots.map { it.id },
                demand = JobDemand("测试技能", "测试经历", "测试任务"),
            )
        }
        val candidates = table.flatMap { (day, slots) ->
            slots.mapIndexed { i, slot ->
                candidate(
                    id = slot.id,
                    workdayIndex = day,
                    name = "测试候选人$day-$i",
                    gender = slot.gender,
                    successBps = slot.bps,
                )
            }
        }
        return MonthContent(
            contentVersion = GameState.EXPECTED_CONTENT_VERSION,
            rulesVersion = GameState.EXPECTED_RULES_VERSION,
            exportedFrom = "app-test",
            openingTitle = "游戏开始了。",
            openingBody = "测试用开场正文。",
            workdays = days,
            candidates = candidates,
        )
    }

    fun ref(): ExportedContentRef = ExportedContentRef(month())

    fun candidate(
        id: String,
        workdayIndex: Int,
        name: String,
        gender: String,
        successBps: Int,
    ): CandidateContent = CandidateContent(
        id = id,
        workdayIndex = workdayIndex,
        resumeNo = "R$id",
        role = "内容审核",
        name = name,
        gender = gender,
        genderLabel = if (gender == "male") "男" else "女",
        age = 24,
        avatarAsset = "avatar/avatar_01.png",
        educationSkills = "测试学历技能",
        workHistory = "测试工作经历",
        fraudType = "项目造假",
        fraudLevel = "严重",
        fraudField = "学历",
        uniqueLie = "唯一假话",
        truth = "真实情况",
        judgementBasis = "判定依据",
        successBps = successBps,
        statements = listOf("education-form", "school", "major", "education-dates", "work-claim").map {
            ReviewStatement(it, if (it == "work-claim") "workExperience" else "educationSkills",
                if (it == "work-claim") "唯一假话" else "测试信息 $it")
        },
        evidence = listOf(ReviewEvidence("academic", "学籍", "已核对"), ReviewEvidence("work", "任职", "已核对")),
        lieStatementId = "work-claim",
        supportingEvidenceIds = listOf("work"),
    )
}
