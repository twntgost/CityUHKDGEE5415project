package com.example.hrrepayment.audio

/**
 * 可替换音效的显式路径模板。
 *
 * 2026-11-02：内置了 48 条**合成占位音效**（`tools/gen_audio.py` 生成，WAV 单声道
 * 16-bit / 22050Hz）。之所以用 WAV 而不是原计划的 OGG：构建机上没有
 * ffmpeg/oggenc，Android 的 MediaPlayer 又原生支持 WAV，因此以扩展名常量
 * [EXT] 统一管理——将来换成真实 OGG 素材时，只需把文件放到同一路径并把
 * [EXT] 改回 "ogg"，**不必改任何映射代码**。
 *
 * **禁止**出现猜出来的 start1/win1/lose1——真实文件名是
 * gambol_start、gambol_start2..5；gambol_win、win2、win3；gambol_lose、lose2。
 */
object SoundCatalog {

    /** 音频扩展名。换真实素材时改这一处即可。 */
    const val EXT = "wav"

    private const val RESUME = "audio/resume_sound"
    private const val GAMBOL = "audio/gambol_sound"

    val maleAccept: List<String> = (1..12).map { "$RESUME/male_accept$it.$EXT" }
    val femaleAccept: List<String> = (1..6).map { "$RESUME/female_accept$it.$EXT" }
    val maleRefuse: List<String> = (1..11).map { "$RESUME/male_refuse$it.$EXT" }
    val femaleRefuse: List<String> = (1..6).map { "$RESUME/female_refuse$it.$EXT" }

    val betStart: List<String> =
        listOf("$GAMBOL/gambol_start.$EXT") + (2..5).map { "$GAMBOL/gambol_start$it.$EXT" }
    val betWin: List<String> =
        listOf("$GAMBOL/gambol_win.$EXT", "$GAMBOL/gambol_win2.$EXT", "$GAMBOL/gambol_win3.$EXT")
    val betLose: List<String> = listOf("$GAMBOL/gambol_lose.$EXT", "$GAMBOL/gambol_lose2.$EXT")

    val ENDING_GOOD = "audio/game_GoodEnding.$EXT"
    val ENDING_HEALTH = "audio/game_HealthEnding.$EXT"
    val ENDING_BAD = "audio/game_BadEnding.$EXT"

    // ---- 材质交互音效（2026-11-11）
    const val DOSSIER_OPEN = "audio/dossier_open.$EXT"
    const val RESUME_FLIP = "audio/page_flip.$EXT"
    const val PEN_STROKE = "audio/pen_stroke.$EXT"
    const val CASH_SHOWER = "audio/cash_shower.wav"
    const val STAMP = "audio/stamp.$EXT"

    /** 未来替换资源时用于与 assets 清单对账。 */
    val all: List<String> =
        maleAccept + femaleAccept + maleRefuse + femaleRefuse +
            betStart + betWin + betLose +
            listOf(ENDING_GOOD, ENDING_HEALTH, ENDING_BAD) +
            listOf(DOSSIER_OPEN, RESUME_FLIP, PEN_STROKE, CASH_SHOWER, STAMP)

    fun filesFor(event: AudioEvent): List<String> = when (event) {
        is AudioEvent.ResumeConfirm -> when (event.pool) {
            AudioEvent.ResumePool.MALE_ACCEPT -> maleAccept
            AudioEvent.ResumePool.FEMALE_ACCEPT -> femaleAccept
            AudioEvent.ResumePool.MALE_REFUSE -> maleRefuse
            AudioEvent.ResumePool.FEMALE_REFUSE -> femaleRefuse
        }
        AudioEvent.DossierOpen -> listOf(DOSSIER_OPEN)
        AudioEvent.ResumeFlip -> listOf(RESUME_FLIP)
        AudioEvent.PenCircle -> listOf(PEN_STROKE)
        AudioEvent.CashShower -> listOf(CASH_SHOWER)
        AudioEvent.Stamp -> listOf(STAMP)
        AudioEvent.BetStart -> betStart
        AudioEvent.BetWin -> betWin
        AudioEvent.BetLose -> betLose
        is AudioEvent.Ending -> when (event.tone) {
            AudioEvent.EndingTone.GOOD -> listOf(ENDING_GOOD)
            AudioEvent.EndingTone.HEALTH -> listOf(ENDING_HEALTH)
            AudioEvent.EndingTone.BAD -> listOf(ENDING_BAD)
        }
    }

    /** 未来替换资源时的事件分组期望条数。 */
    val expectedCounts: Map<String, Int> = mapOf(
        "resume_male_accept" to 12,
        "resume_female_accept" to 6,
        "resume_male_refuse" to 11,
        "resume_female_refuse" to 6,
        "bet_start" to 5,
        "bet_win" to 3,
        "bet_lose" to 2,
        "ending_overseas_repaid" to 1,
        "ending_overseas_indebted" to 1,
        "ending_bad" to 1,
        "ui_dossier_open" to 1,
        "ui_resume_flip" to 1,
        "ui_pen_stroke" to 1,
        "ui_cash_shower" to 1,
        "ui_stamp" to 1,
    )
}

/**
 * 音频变体选择：使用**独立的**随机源（绝不复用引擎的游戏 RNG）。
 * 同一存档是否播放音效、播哪一条变体，都不能改变招聘概率。
 */
class AudioVariantPicker(
    private val random: com.example.hrrepayment.core.random.RandomSource,
) {
    fun pick(files: List<String>): String {
        require(files.isNotEmpty()) { "empty audio pool" }
        return files[random.nextInt(0, files.size)]
    }
}

/** 播放器抽象：业务层只面对这一接口。 */
interface AudioPlayer {
    /** 播放 assets 内的一条音频；新音效打断旧音；失败只记录，绝不影响业务。 */
    fun play(assetPath: String)

    /** 立即停止当前播放（后台 ON_STOP / 结局切换）。 */
    fun stop()
}
