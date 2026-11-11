package com.example.hrrepayment

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.audio.AudioEvent
import com.example.hrrepayment.audio.SoundCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 音频资源对账：SoundCatalog 里每一个路径都必须真的存在于 APK 的 assets 中，
 * 且每个音频都能被 MediaPlayer 打开。
 *
 * 为什么需要这一层：音效是**按事件清单**索引的，路径写错或文件漏打时，
 * 代码不会崩、只是**静默没有声音**——这是最难靠肉眼发现的一类问题。
 */
@RunWith(AndroidJUnit4::class)
class AudioAssetsInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun exists(path: String): Boolean = runCatching {
        context.assets.openFd(path).use { true }
    }.getOrDefault(false)

    @Test
    fun everyCataloguedAssetIsPackaged() {
        val missing = SoundCatalog.all.filterNot { exists(it) }
        assertTrue("以下音频没有打进 APK：\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun catalogCoversEveryEventAndVariantCountMatches() {
        // 四个简历池 + 竞猜三类 + 三个结局，全部要有文件
        val events = listOf(
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.MALE_ACCEPT),
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.FEMALE_ACCEPT),
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.MALE_REFUSE),
            AudioEvent.ResumeConfirm(AudioEvent.ResumePool.FEMALE_REFUSE),
            AudioEvent.BetStart, AudioEvent.BetWin, AudioEvent.BetLose,
            AudioEvent.Ending(AudioEvent.EndingTone.GOOD),
            AudioEvent.Ending(AudioEvent.EndingTone.HEALTH),
            AudioEvent.Ending(AudioEvent.EndingTone.BAD),
        )
        events.forEach { e ->
            val files = SoundCatalog.filesFor(e)
            assertTrue("$e 没有对应音频", files.isNotEmpty())
            files.forEach { assertTrue("$e 的 $it 不存在", exists(it)) }
        }
    }

    @Test
    fun expectedVariantCountsStillMatchTheCatalog() {
        assertEquals(12, SoundCatalog.maleAccept.size)
        assertEquals(6, SoundCatalog.femaleAccept.size)
        assertEquals(11, SoundCatalog.maleRefuse.size)
        assertEquals(6, SoundCatalog.femaleRefuse.size)
        assertEquals(5, SoundCatalog.betStart.size)
        assertEquals(3, SoundCatalog.betWin.size)
        assertEquals(2, SoundCatalog.betLose.size)
    }

    @Test
    fun everyAssetIsADecodableAudioFile() {
        SoundCatalog.all.forEach { path ->
            context.assets.openFd(path).use { fd ->
                val player = android.media.MediaPlayer()
                try {
                    player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    player.prepare()
                    assertTrue("$path 时长为 0", player.duration > 0)
                } finally {
                    runCatching { player.release() }
                }
            }
        }
    }
}
