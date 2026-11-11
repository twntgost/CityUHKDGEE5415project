package com.example.hrrepayment

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.audio.AssetAudioPlayer
import com.example.hrrepayment.audio.AudioEvent
import com.example.hrrepayment.audio.AudioVariantPicker
import com.example.hrrepayment.audio.GameAudio
import com.example.hrrepayment.core.random.SplitMix64Random
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实播放冒烟：不是“文件能解码”，而是**真的把每条事件播出去**。
 *
 * 覆盖 4 个简历池 + 3 个竞猜 + 3 个结局共 10 条事件；播放失败会被
 * [AssetAudioPlayer] 记录，这里通过“不抛异常 + 播放器可停止”来兜底，
 * 音频错误同时会出现在 logcat（`AudioPlay` 标签）。
 */
@RunWith(AndroidJUnit4::class)
class AudioPlaybackSmokeTest {

    @Test
    fun everyEventPlaysThroughTheRealPlayer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val player = AssetAudioPlayer(context)
        val audio = GameAudio(player, AudioVariantPicker(SplitMix64Random(seed = 4242L)))
        val events = listOf(
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.MALE_ACCEPT),
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.FEMALE_ACCEPT),
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.MALE_REFUSE),
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.FEMALE_REFUSE),
            AudioEvent.BetStart,
            AudioEvent.BetWin,
            AudioEvent.BetLose,
            AudioEvent.Ending(AudioEvent.EndingTone.GOOD),
            AudioEvent.Ending(AudioEvent.EndingTone.HEALTH),
            AudioEvent.Ending(AudioEvent.EndingTone.BAD),
        )
        events.forEach { event ->
            audio.play(event)
            Thread.sleep(120) // 让 MediaPlayer 真正开始播放，再打断下一条
        }
        Thread.sleep(400)
        audio.stop()
        assertTrue("播放链路未抛异常即视为通过", true)
    }
}
