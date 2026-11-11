package com.example.hrrepayment.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.ui.compositionlocals.LocalAvatarCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A candidate portrait (128:160). Decoding goes through the shared
 * [AvatarCache] so a screen with hundreds of faces (面试档案袋) keeps a bounded
 * bitmap working set and never re-decodes a portrait that is already on screen.
 */
@Composable
fun AvatarImage(
    assetPath: String,
    modifier: Modifier = Modifier,
    corner: Dp = 4.dp,
    testTag: String? = null,
) {
    val context = LocalContext.current
    val cache = LocalAvatarCache.current
    var bitmap by remember(assetPath) { mutableStateOf(cache.get(assetPath)) }

    LaunchedEffect(assetPath) {
        if (bitmap == null) {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (decoded != null) {
                cache.put(assetPath, decoded)
                bitmap = cache.get(assetPath) ?: decoded
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(128f / 160f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(corner))
            .then(if (testTag != null) Modifier.semantics { this.testTag = testTag } else Modifier),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
