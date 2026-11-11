package com.example.hrrepayment.core.content

/**
 * 内容接口（技术文档 §2 / §3）。
 *
 * P2 起由真实导出物（`assets/content/month1.json` → [ExportedContentRef]）实现，
 * 不在 Android 端解析 XLSX。
 *
 * 实现必须是不可变且可热替换的；GameEngine 启动时由工厂注入一次，运行期
 * 改变需要重启进程。
 */
interface ContentRef {
    /** 版本与 hash 用于 schema 校验。 */
    val contentVersion: String
    val contentHash: String

    /** The literal opening narrative shown on the first game start. */
    val openingTitle: String
    val openingBody: String

    /** 总工作日数与每月日数的元数据；引擎用其做阶段推进。 */
    val totalWorkdays: Int
    val daysPerMonth: Int

    /**
     * Asset path (relative to `assets/`) of the portrait shown beside the opening
     * copy.
     */
    val openingAvatarAsset: String get() = "avatar/avatar_01.png"

    /**
     * 第 N 个工作日（0-indexed）的完整内容：真实日期、岗位、候选人 ID。
     * 未导出的索引抛 [IllegalArgumentException]，绝不返回编造的日期。
     */
    fun workdayInfo(index: Int): WorkdayContent

    /** 第 N 个工作日的两份候选简历（按 源表 A、B 顺序）。 */
    fun workdayCandidates(workdayIndex: Int): List<CandidateContent>

    /** 按稳定 ID 查找候选人；未知 ID 抛 [IllegalArgumentException]。 */
    fun candidate(id: String): CandidateContent

    fun newsForWorkday(index: Int): List<NewsContent> = emptyList()

    fun bettingMarkets(): List<BettingMarket> = emptyList()

    /**
     * 第 N 个工作日（0-indexed）的日期字符串，仅用于 UI 展示，不参与判定。
     * P2 起来自真实导出表（首月 2016-07-04…07-29）。
     */
    fun workdayDisplayDate(index: Int): String = workdayInfo(index).date

    companion object {
        const val TOTAL_WORKDAYS = com.example.hrrepayment.core.model.WorkdayRef.TOTAL_WORKDAYS
        const val DAYS_PER_MONTH = com.example.hrrepayment.core.model.WorkdayRef.DAYS_PER_MONTH
    }
}
