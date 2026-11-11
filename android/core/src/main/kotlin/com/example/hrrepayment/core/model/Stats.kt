package com.example.hrrepayment.core.model

import kotlinx.serialization.Serializable

/**
 * Recruitment and promotion counters (技术文档 §4.1).
 *
 *  - globalSuccessCount drives 50/100/150 promotion bonuses (one-time each).
 *  - globalFailureCount is for surface statistics only.
 *  - successiveFailureCount resets on any success and drives the trust drop.
 *  - successRewardPending is set only by the submission reaching 3/6/9/... successes,
 *    then consumed when its result opens the reward panel. Later failures cannot replay it.
 *
 * Across-month carry: 全球成功/失败计数从不按月清零；连续失败计数器跨月保留。
 */
@Serializable
data class Stats(
    val globalSuccessCount: Int = 0,
    val globalFailureCount: Int = 0,
    val successiveFailureCount: Int = 0,
    val successRewardPending: Int = 0,
    val reviewedSubmissionCount: Int = 0,
    val correctReviewCount: Int = 0,
) {
    val promotionBonusesClaimed: Int
        get() = listOf(50, 100, 150).count { it <= globalSuccessCount }

    /** Effective promoted monthly delta from 50/100/150 milestones. */
    val promotionDelta: Int
        get() = when {
            globalSuccessCount >= 150 -> 3000 + 4000 + 5000
            globalSuccessCount >= 100 -> 3000 + 4000
            globalSuccessCount >= 50 -> 3000
            else -> 0
        }
}
