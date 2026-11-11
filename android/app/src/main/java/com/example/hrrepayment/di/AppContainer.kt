package com.example.hrrepayment.di

import android.content.Context
import com.example.hrrepayment.audio.AudioPlayer
import com.example.hrrepayment.audio.AudioVariantPicker
import com.example.hrrepayment.audio.GameAudio
import com.example.hrrepayment.audio.AssetAudioPlayer
import com.example.hrrepayment.audio.NoOpAudioPlayer
import com.example.hrrepayment.core.engine.GameEngine
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.random.RandomSource
import com.example.hrrepayment.core.random.SplitMix64Random
import com.example.hrrepayment.data.content.AssetContentLoader
import com.example.hrrepayment.data.content.SwappableContentRef
import com.example.hrrepayment.data.save.LegacySaveMigration
import com.example.hrrepayment.data.save.SaveExpectations
import com.example.hrrepayment.data.save.SaveRepository
import com.example.hrrepayment.data.save.SaveSlot
import com.example.hrrepayment.data.settings.SettingsRepository
import com.example.hrrepayment.ui.components.AvatarCache

/**
 * Manual DI root. Constructed exactly once in [com.example.hrrepayment.HRPayDebtApp];
 * ViewModels reach for collaborators through here so we can swap fakes in tests.
 *
 * Two things changed with the v3 rework:
 *  - the content reference is a [SwappableContentRef], so “Switch Language” swaps the
 *    loaded bundle at runtime while the engine keeps the same reference;
 *  - there are now **two save slots**, each its own DataStore file.
 */
class AppContainer private constructor(
    val context: Context,
    val contentRef: SwappableContentRef,
    val randomSource: RandomSource,
    val gameEngine: GameEngine,
    val saveSlots: Map<SaveSlot, SaveRepository>,
    val settings: SettingsRepository,
    val gameAudio: GameAudio,
    val audioPlayer: AudioPlayer,
    val avatarCache: AvatarCache,
) {

    fun repository(slot: SaveSlot): SaveRepository = saveSlots.getValue(slot)

    /**
     * Reload the content bundle in [lang]. Called at boot with the persisted language
     * and again whenever the player taps “切换语言 / Switch Language”.
     */
    fun applyLanguage(lang: Lang) {
        contentRef.loadAndSwap(context, lang)
    }

    companion object {
        fun create(context: Context): AppContainer {
            val content = SwappableContentRef(AssetContentLoader.load(context, Lang.DEFAULT))
            val random = SplitMix64Random(seed = System.nanoTime())
            val engine = GameEngine(content, random)

            val migration = LegacySaveMigration(
                content,
                context.assets.open("content/legacy-v1.json").use { it.readBytes() },
            )
            val expectations = SaveExpectations.current(contentHash = content.contentHash)
            val slots = SaveSlot.all.associateWith { slot ->
                SaveRepository.create(
                    context = context,
                    slot = slot,
                    expectations = expectations,
                    migration = migration::migrate,
                )
            }

            // 音频变体随机源：独立实例、独立种子，绝不复用上面的游戏 RNG。
            val audioRandom = SplitMix64Random(seed = System.nanoTime())
            // 2026-11-02：48 条合成占位音效已随包发布，正式注入真实播放器。
            // 需要静音（例如调试音频问题）时把这一行换成 NoOpAudioPlayer() 即可。
            val audioPlayer: AudioPlayer = AssetAudioPlayer(context)
            val audio = GameAudio(audioPlayer, AudioVariantPicker(audioRandom))

            return AppContainer(
                context = context,
                contentRef = content,
                randomSource = random,
                gameEngine = engine,
                saveSlots = slots,
                settings = SettingsRepository.create(context),
                gameAudio = audio,
                audioPlayer = audioPlayer,
                avatarCache = AvatarCache(),
            )
        }
    }
}
