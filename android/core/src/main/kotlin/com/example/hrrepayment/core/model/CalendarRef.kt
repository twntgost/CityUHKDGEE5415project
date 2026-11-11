package com.example.hrrepayment.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * 工作日历引用。日期来源是外部 252 工作日表；本类型只携带必要的索引与冻结
 * 字段，由 core 决定查找逻辑。
 *
 * 章节定义（每半年一章，共 7 章 42 月）:
 *  1: 2016-07..12    2: 2017-01..06   3: 2017-07..12
 *  4: 2018-01..06   5: 2018-07..12   6: 2019-01..06   7: 2019-07..12
 */
@Serializable
data class WorkdayRef(
    /** 0-based index into the 252-workday table. 0 == 2016-07-04. */
    val index: Int,
) {
    init {
        require(index in 0 until TOTAL_WORKDAYS) {
            "workday index must be in [0, $TOTAL_WORKDAYS), got $index"
        }
    }

    val monthIndex: Int get() = index / DAYS_PER_MONTH  // 0..41
    val dayOfMonth: Int get() = index % DAYS_PER_MONTH   // 0..5
    val chapter: Int get() = monthIndex / CHAPTER_MONTHS // 0..6

    companion object {
        const val TOTAL_WORKDAYS = 252
        const val DAYS_PER_MONTH = 6
        const val TOTAL_MONTHS = 42
        const val CHAPTER_MONTHS = 6

        val FIRST = WorkdayRef(0)
        val LAST = WorkdayRef(TOTAL_WORKDAYS - 1)
    }
}

/**
 * 项目书 §3.1：竞猜窗口与最后一日冻结都以“工作日索引”为单位；技术文档 §3
 * 要求日期索引来自工作日表，不使用系统时间推进。
 */
@Serializable
data class CalendarPosition(
    val current: WorkdayRef,
    val endedAt: WorkdayRef? = null,
) {
    val hasEnded: Boolean get() = endedAt != null
}
