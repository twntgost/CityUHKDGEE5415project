package com.example.hrrepayment.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * assets 里材质素材的加载工具。
 *
 * 为什么放 assets 而不是 `res/drawable`：这些是**材质类**资源（牛皮纸、杜邦纸、颗粒、
 * 红笔圈、印章），体积较大、需要按原始像素平铺，放 assets 用位图缓存更合适，
 * 也方便整体替换素材包。
 */
object MaterialAssets {
    const val KRAFT = "textures/kraft.png"
    const val TYVEK = "textures/tyvek.png"
    const val GRAIN = "textures/grain.png"
    const val STAMP_PUSHED = "stamps/stamp_pushed.png"

    /** 5 枚「推送成功」章的随机磨损变体（每次盖章随机取一枚，磨损形态都不同）。 */
    val STAMP_VARIANTS = (1..5).map { "stamps/stamp_pushed_$it.png" }

    /** 5 种手写红笔圈，随机挑一个。 */
    val INK_CIRCLES = (1..5).map { "ink/circle_$it.png" }
}

private fun Context.loadAssetBitmap(path: String): Bitmap? = runCatching {
    assets.open(path).use { BitmapFactory.decodeStream(it) }
}.getOrNull()

@Composable
fun rememberAssetImage(path: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(path) { context.loadAssetBitmap(path)?.asImageBitmap() }
}

@Composable
fun rememberAssetBitmaps(paths: List<String>): List<ImageBitmap> {
    val context = LocalContext.current
    return remember(paths) { paths.mapNotNull { context.loadAssetBitmap(it)?.asImageBitmap() } }
}

/** 纹理平铺笔刷：按原始像素重复，放大后依然保留颗粒感。 */
@Composable
fun rememberTiledBrush(path: String): Brush? {
    val image = rememberAssetImage(path) ?: return null
    return remember(image) {
        val shader = ImageShader(image, TileMode.Repeated, TileMode.Repeated)
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader = shader
        }
    }
}
