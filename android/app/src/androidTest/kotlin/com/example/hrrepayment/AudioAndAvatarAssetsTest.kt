package com.example.hrrepayment

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.data.content.AssetContentLoader
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 当前版本不携带音效资源；此测试锁定静音包契约，并继续验证头像资源。
 */
@RunWith(AndroidJUnit4::class)
class AudioAndAvatarAssetsTest {

    /**
     * 2026-11-02 契约变更：此前是“静音构建，包里不应有音频目录”，
     * 现在 48 条占位音效已随包发布。这里只做最粗的哨兵断言，
     * 逐文件对账由 [AudioAssetsInstrumentedTest] 负责。
     */
    @Test
    fun audioAssetsArePackagedNowThatSoundIsWired() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioDirs = context.assets.list("audio").orEmpty()
        assertTrue("音频目录缺失：sound should be wired in", audioDirs.isNotEmpty())
    }

    @Test
    fun everyCandidateAvatarReferencedByContentDecodes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = AssetContentLoader.load(context, Lang.ZH)

        val avatars = (0 until content.daysPerMonth)
            .flatMap { content.workdayCandidates(it) }
            .map { it.avatarAsset }
            .distinct()

        assertTrue("month one must reference candidate avatars", avatars.isNotEmpty())
        for (path in avatars) {
            val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            assertTrue("asset $path must decode to a bitmap", bitmap != null)
            bitmap!!.recycle()
        }
    }

}
