package com.example.hrrepayment.audio

/**
 * 音频门面：业务层（ViewModel）只面对它。
 *
 * 变体挑选使用注入的独立随机源（见 [SoundCatalog.AudioVariantPicker] 文档）；
 * 静音只需要替换 [AudioPlayer] 实现，不需要动业务代码。
 */
class GameAudio(
    private val player: AudioPlayer,
    private val picker: AudioVariantPicker,
) {
    fun play(event: AudioEvent) {
        val files = SoundCatalog.filesFor(event)
        if (files.isEmpty()) return
        player.play(picker.pick(files))
    }

    fun stop() = player.stop()
}

/** 当前正式构建使用的静音实现。业务仍发送 AudioEvent，替换此实现即可接入新音效。 */
class NoOpAudioPlayer : AudioPlayer {
    override fun play(assetPath: String) = Unit
    override fun stop() = Unit
}
