package com.example.hrrepayment

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.data.content.AssetContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 技术文档 P1 requires the existing avatar to be *actually loaded on Android*
 * (the previous delivery said “头像留到 P4 再验证”, which the review rejected).
 *
 * P2 起：头像路径来自**导出内容 JSON**（story-v1 标记，sha256 区分导出批次），
 * 不再是硬编码引用。
 * This runs on a real device/emulator and decodes the PNG through Android's asset
 * pipeline, proving the path in the exported content resolves.
 */
@RunWith(AndroidJUnit4::class)
class OpeningAvatarAssetTest {

    @Test
    fun openingAvatarDecodesThroughTheAndroidAssetPipeline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = AssetContentLoader.load(context, Lang.ZH)
        val assetPath = content.openingAvatarAsset

        val bitmap = context.assets.open(assetPath).use { stream ->
            BitmapFactory.decodeStream(stream)
        }

        assertNotNull("asset $assetPath must decode to a bitmap", bitmap)
        assertEquals(128, bitmap!!.width)
        assertEquals(160, bitmap.height)
    }
}
