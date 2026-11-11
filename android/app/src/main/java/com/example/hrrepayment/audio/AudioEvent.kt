package com.example.hrrepayment.audio

/**
 * 音频事件层（开发日志 P2 合同 §13 / Codex 评审建议 3）。
 *
 * 三段抽象，职责单一：
 *
 *  1. [com.example.hrrepayment.presentation.GameViewModel] 在**保存成功之后**
 *     把业务事实映射成 [AudioEvent]（映射逻辑在 [AudioEventRouter]，纯函数可单测）；
 *  2. [SoundCatalog] 把事件解析为可替换资源路径的显式清单（禁止猜文件名）；
 *  3. [AudioPlayer] 只负责播放（可替换为静音实现/测试替身）。
 *
 * Compose 点击回调从不直接触碰文件路径；静音、防重复播放、后台停止都只改
 * 这一层。
 */
sealed interface AudioEvent {
    /**
     * 简历确认音效池（项目书 §15.6）：按**当日两份简历的性别组合**选择，
     * 与招聘成功/失败无关——accept/refuse 只是音频分组，不是结果。
     */
    data class ResumeConfirm(val pool: ResumePool) : AudioEvent

    /** 解开牛皮纸档案袋（档案袋打开时）。 */
    data object DossierOpen : AudioEvent

    /** 简历翻页（在两张叠放的简历之间切换时，极短）。 */
    data object ResumeFlip : AudioEvent

    /** 红笔圈出造假部分时的笔尖摩擦声。 */
    data object PenCircle : AudioEvent

    /** 成功弹窗的盖章声。 */
    data object Stamp : AudioEvent

    /** 竞猜赢钱时撒钱的声音。 */
    data object CashShower : AudioEvent

    /** 竞猜下注（P4 接线；P2 仅在开发预览中验证路由）。 */
    data object BetStart : AudioEvent

    /** 竞猜兑现（P4 接线；P2 仅路由）。 */
    data object BetWin : AudioEvent

    /** 竞猜未兑现（P4 接线；P2 仅路由）。 */
    data object BetLose : AudioEvent

    /** 结局音：人生逆袭 / 逍遥法外 / 其余 6 个结局及全部原因变体。 */
    data class Ending(val tone: EndingTone) : AudioEvent

    enum class ResumePool { MALE_ACCEPT, FEMALE_ACCEPT, MALE_REFUSE, FEMALE_REFUSE }

    enum class EndingTone { GOOD, HEALTH, BAD }
}

/**
 * 纯映射：业务事实 → [AudioEvent]。
 *
 *  - 一男一女 → 被推送者性别的 accept 池；
 *  - 两男 → male_refuse；两女 → female_refuse；
 *  - 同一事务同时结算与触发结局时，**结局音优先**，简历确认音不播。
 */
object AudioEventRouter {

    fun resumePool(todayGenders: List<String>, pushedGender: String): AudioEvent.ResumePool {
        require(todayGenders.size == 2) { "day must offer exactly two candidates" }
        for (gender in todayGenders) {
            require(gender == "male" || gender == "female") {
                "unknown gender in today's candidates: '$gender'"
            }
        }
        require(pushedGender == "male" || pushedGender == "female") {
            "unknown pushed gender: '$pushedGender'"
        }
        require(pushedGender in todayGenders) {
            "pushed gender '$pushedGender' is not among today's candidates $todayGenders"
        }
        val maleCount = todayGenders.count { it == "male" }
        val femaleCount = todayGenders.count { it == "female" }
        return when {
            maleCount == 1 && femaleCount == 1 ->
                if (pushedGender == "male") {
                    AudioEvent.ResumePool.MALE_ACCEPT
                } else {
                    AudioEvent.ResumePool.FEMALE_ACCEPT
                }
            maleCount == 2 -> AudioEvent.ResumePool.MALE_REFUSE
            femaleCount == 2 -> AudioEvent.ResumePool.FEMALE_REFUSE
            else -> throw IllegalArgumentException("unexpected gender combo: $todayGenders")
        }
    }

    /** GoodEnding=人生逆袭；HealthEnding=逍遥法外（隐藏结局专用）；其余→BadEnding。 */
    fun endingTone(endingId: String): AudioEvent.EndingTone = when (endingId) {
        "overseas_repaid" -> AudioEvent.EndingTone.GOOD
        "overseas_indebted" -> AudioEvent.EndingTone.HEALTH
        "debt_free_restart", "bankruptcy", "indebted_layoff",
        "health_death", "low_dopamine", "exposed",
        -> AudioEvent.EndingTone.BAD
        // 结局内容未来扩展时，未知/缺失 ID 也必须落到保守的 bad ending 音，
        // 不能因为音效映射异常让已结束的业务流程崩溃或误播简历音。
        else -> AudioEvent.EndingTone.BAD
    }

    /**
     * 一次提交的音效：结局优先；否则按当日性别组合播简历确认音。
     * 恢复存档不会走到这里（ViewModel 只在用户动作保存成功后调用）。
     */
    fun forSubmission(
        todayGenders: List<String>,
        pushedGender: String,
        ended: Boolean,
        endingId: String?,
    ): AudioEvent = if (ended) {
        AudioEvent.Ending(endingTone(endingId.orEmpty()))
    } else {
        AudioEvent.ResumeConfirm(resumePool(todayGenders, pushedGender))
    }
}
