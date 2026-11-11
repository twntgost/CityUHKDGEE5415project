package com.example.hrrepayment.ui.dossier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.fillMaxHeight
import com.example.hrrepayment.ui.components.MaterialAssets
import com.example.hrrepayment.ui.components.safeTopInset
import com.example.hrrepayment.ui.components.rememberTiledBrush
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Strings
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import com.example.hrrepayment.ui.theme.Tokens
import com.example.hrrepayment.presentation.DossierEntry
import com.example.hrrepayment.ui.components.AvatarImage
import com.example.hrrepayment.ui.theme.GameIcons

/**
 * 面试档案袋：所有推送成功过的人，按职位分栏，头像 + 姓名方格排列。
 *
 * 双指捏合/张开在每行 **5 个** 与 **3 个** 之间切换（阈值 + 累积缩放，不跟踪手指
 * 比例，避免每帧重排）。网格只组合可见项，头像走共享 [com.example.hrrepayment.ui.components.AvatarCache]，
 * 因此即使收集了数百人也不会把内存顶上去。
 */
@Composable
fun DossierScreen(
    strings: Strings,
    entries: List<DossierEntry>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 打开档案袋时的“解开牛皮纸”音效。 */
    onOpenSound: () -> Unit = {},
) {
    var columns by remember { mutableIntStateOf(DENSE_COLUMNS) }
    val gridState = rememberLazyGridState()
    // 返回按钮用杜邦纸质感（只在按钮上，不铺整屏）
    val tyvek = rememberTiledBrush(MaterialAssets.TYVEK)

    // Group by job role, preserving first-seen order so the sections stay stable.
    val sections = remember(entries) {
        entries.groupBy { it.role }.entries.map { it.key to it.value }
    }

    LaunchedEffect(Unit) { onOpenSound() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeTopInset()
            .navigationBarsPadding()
            .padding(horizontal = Tokens.PageMargin.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.InnerGap.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 杜邦纸质感的返回按钮：纸纹 + 轻微内阴影感，而不是扁平纯色
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tyvek ?: Brush.linearGradient(listOf(TyvekFallback, TyvekFallback)))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("tag_dossier_close"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    GameIcons.Dossier,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(strings[Key.MENU_BACK], color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                GameIcons.Zoom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = strings[Key.DOSSIER_COUNT].format(entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = strings[Key.DOSSIER_TITLE],
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = strings[Key.DOSSIER_ZOOM_HINT],
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(Tokens.InnerGap.dp))

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = strings[Key.DOSSIER_EMPTY],
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp).testTag("tag_dossier_empty"),
                )
            }
            return@Column
        }

        val zoomAccumulator = remember { FloatArray(1) { 1f } }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures(panZoomLock = true) { _, _, zoom, _ ->
                        zoomAccumulator[0] *= zoom
                        val acc = zoomAccumulator[0]
                        if (acc >= ZOOM_IN_THRESHOLD && columns == DENSE_COLUMNS) {
                            columns = COARSE_COLUMNS
                            zoomAccumulator[0] = 1f
                        } else if (acc <= ZOOM_OUT_THRESHOLD && columns == COARSE_COLUMNS) {
                            columns = DENSE_COLUMNS
                            zoomAccumulator[0] = 1f
                        }
                    }
                }
                .testTag("tag_dossier_grid"),
            horizontalArrangement = Arrangement.spacedBy(Tokens.InnerGap.dp),
            verticalArrangement = Arrangement.spacedBy(Tokens.InnerGap.dp),
        ) {
            sections.forEach { (role, people) ->
                item(
                    key = "header-$role",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Tokens.BlockGap.dp, bottom = Tokens.CellGap.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            GameIcons.Trophy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.format(Key.DOSSIER_ROLE_COUNT, role, people.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                items(people, key = { it.candidateId }) { entry ->
                    DossierCell(entry = entry, strings = strings)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * 档案袋里的一个人。
 *
 * 职业状态直接做在头像上（作者 2026-10-11 定）：
 *  - **已离职**：照片去色压暗 + 红字“已离职”；
 *  - **晋升 1 次**：边框绿色；**晋升 2 次**：边框金色。
 */
@Composable
private fun DossierCell(entry: DossierEntry, strings: Strings) {
    val frame = when {
        entry.resigned -> MaterialTheme.colorScheme.outline
        entry.promotions >= 2 -> Tokens.Gold
        entry.promotions == 1 -> Tokens.Success
        else -> Color.Transparent
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, frame, RoundedCornerShape(10.dp))
                .testTag("tag_dossier_cell_${entry.candidateId}"),
        ) {
            AvatarImage(
                assetPath = entry.avatarAsset,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (entry.resigned) {
                            Modifier.graphicsLayer {
                                colorFilter = ColorFilter.colorMatrix(
                                    ColorMatrix().apply { setToSaturation(0f) },
                                )
                                alpha = 0.55f
                            }
                        } else {
                            Modifier
                        },
                    ),
                corner = 10.dp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (entry.resigned) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            // 英文名（“Zheng Ming”）在 5 列布局里只有 66dp 宽，一行必然被截成
            // “Zheng M...”。改为居中最多三行完整显示，不再用省略号。
            maxLines = 3,
            softWrap = true,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (entry.resigned) {
            Text(
                text = strings[Key.DOSSIER_RESIGNED],
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                modifier = Modifier.testTag("tag_dossier_resigned_${entry.candidateId}"),
            )
        } else if (entry.promotions > 0) {
            Text(
                text = strings.format(Key.DOSSIER_PROMOTED, entry.promotions),
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.promotions >= 2) Tokens.Gold else Tokens.Success,
                maxLines = 1,
                modifier = Modifier.testTag("tag_dossier_promoted_${entry.candidateId}"),
            )
        }
    }
}

/** 纹理未加载时的兜底色（避免闪白）。 */
private val KraftFallback = Color(0xFFC4A474)
private val TyvekFallback = Color(0xFFEEE9DE)

/** 每行 5 个（默认）或 3 个（放大）。 */
private const val DENSE_COLUMNS = 5
private const val COARSE_COLUMNS = 3
private const val ZOOM_IN_THRESHOLD = 1.35f
private const val ZOOM_OUT_THRESHOLD = 0.75f
