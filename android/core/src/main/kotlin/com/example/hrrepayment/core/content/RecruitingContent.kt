package com.example.hrrepayment.core.content

import com.example.hrrepayment.core.model.WorkdayRef
import kotlinx.serialization.Serializable

@Serializable
data class ReviewStatement(val id: String, val field: String, val text: String)

@Serializable
data class ReviewEvidence(val id: String, val title: String, val body: String)

@Serializable
data class JobDemand(val skill: String, val experience: String, val task: String)

/**
 * 运行时内容：完整42个月、252个工作日、504份简历。
 *
 * 由创作仓库 `tools/export_month1_runtime.py` 从 `resume_content.xlsx`
 * 确定性导出（`app/src/main/assets/content/month1.json`），Android 端只做
 * JSON 解析，不引入 Excel 依赖。字段与源表一一对应：
 *
 *  - 简历卡片：姓名/性别/年龄/头像/学历技能/工作经历；
 *  - 答案表：造假程度/造假字段/唯一假话/真实情况/判定依据/推送成功率；
 *  - 工作日表：日期/岗位/候选人 A、B。
 *
 * `successBps` = 源表 0–1 小数 × 10000（基点），供
 * [com.example.hrrepayment.core.random.RandomSource.chance] 直接消费。
 */
@Serializable
data class CandidateContent(
    val id: String,
    val workdayIndex: Int,
    val resumeNo: String,
    val role: String,
    val name: String,
    val gender: String, // "male" | "female"
    val genderLabel: String, // "男" | "女"
    val age: Int,
    val avatarAsset: String,
    val educationSkills: String,
    val workHistory: String,
    val educationForm: String = "",
    val school: String = "",
    val schoolTier: String = "",
    val schoolLogoAsset: String = "",
    val major: String = "",
    val company: String = "",
    val companyCity: String = "",
    val companyLogoAsset: String = "",
    val fraudType: String = "",
    val fraudLevel: String,
    val fraudField: String,
    val uniqueLie: String,
    val truth: String,
    val judgementBasis: String,
    val successBps: Int,
    val statements: List<ReviewStatement> = emptyList(),
    val evidence: List<ReviewEvidence> = emptyList(),
    val lieStatementId: String = "",
    val supportingEvidenceIds: List<String> = emptyList(),
    val fit: String = "",
    val fitBasis: String = "",
) {
    /** Stable code used by the UI to pick a localized field label. */
    val reviewFieldCode: String
        get() = when (fraudField) {
            "educationSkills", "学历技能", "学历" -> "educationSkills"
            "workExperience", "工作经历" -> "workExperience"
            else -> "other"
        }

    /** Chinese label kept for the legacy fixture format and existing tests. */
    val reviewFieldLabel: String
        get() = when (reviewFieldCode) {
            "educationSkills" -> "学历技能"
            "workExperience" -> "工作经历"
            else -> "履历资料"
        }
}

@Serializable
data class NewsContent(
    val id: String,
    val workdayIndex: Int,
    val title: String,
    val body: String,
    val category: String,
    val order: Int,
)

@Serializable
data class WorkdayContent(
    val index: Int,
    val date: String,
    val role: String,
    val candidateIds: List<String>,
    val demand: JobDemand? = null,
)

@Serializable
data class MonthContent(
    val contentVersion: String,
    val rulesVersion: String,
    val exportedFrom: String,
    /** "zh" or "en": which language variant this file holds. */
    val lang: String = "zh",
    val openingTitle: String,
    val openingBody: String,
    val openingAvatarAsset: String = "avatar/avatar_01.png",
    val workdays: List<WorkdayContent>,
    val candidates: List<CandidateContent>,
    val news: List<NewsContent> = emptyList(),
) {
    init {
        require(workdays.isNotEmpty()) { "MonthContent needs at least one workday" }
        require(candidates.isNotEmpty()) { "MonthContent needs candidates" }
    }
}

/**
 * 基于导出 JSON 的 [ContentRef]。只覆盖已导出的月份；未导出的工作日索引
 * 抛出异常而不是编造日期（P3 扩展导出时自然消除）。
 */
class ExportedContentRef(
    private val month: MonthContent,
    override val contentHash: String = "",
) : ContentRef {

    override val contentVersion: String = month.contentVersion

    override val openingTitle: String = month.openingTitle
    override val openingBody: String = month.openingBody
    override val openingAvatarAsset: String = month.openingAvatarAsset

    override val totalWorkdays: Int = WorkdayRef.TOTAL_WORKDAYS
    override val daysPerMonth: Int = WorkdayRef.DAYS_PER_MONTH

    private val workdayByIndex = month.workdays.associateBy { it.index }
    private val candidateById = month.candidates.associateBy { it.id }

    override fun workdayInfo(index: Int): WorkdayContent =
        workdayByIndex[index]
            ?: throw IllegalArgumentException(
                "工作日 $index 不在本期导出内容中（当前导出：${month.workdays.first().index}.." +
                    "${month.workdays.last().index}）",
            )

    override fun workdayCandidates(workdayIndex: Int): List<CandidateContent> =
        workdayInfo(workdayIndex).candidateIds.map { candidate(it) }

    override fun candidate(id: String): CandidateContent =
        requireNotNull(candidateById[id]) { "未知的候选人：$id" }

    override fun workdayDisplayDate(index: Int): String = workdayInfo(index).date

    override fun newsForWorkday(index: Int): List<NewsContent> = month.news.filter { it.workdayIndex == index }.sortedBy { it.order }
}
