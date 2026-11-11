package com.example.hrrepayment.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.ui.theme.Tokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 下注确认滑条（项目书 §15.7 / P2 合同：只做独立组件与契约测试，不接真实竞猜）。
 *
 * 契约行为：
 *  - 满轨；手柄在左端；填充随手移动；文案“向右滑动确认下注”；
 *  - 拖到 100% 并松手 → 触发一次（[onConfirmRequested]；危险下注时由宿主弹
 *    二次确认，是否真正下注由宿主决定）；
 *  - 未到 100% 松手 → 180ms 回弹；**轻点不触发**；
 *  - [enabled] = false（提交中）不可拖；拖动中 [stateToken] 变化（revision/
 *    状态刷新）→ 作废本次手势并回弹；
 *  - 到位只触发一次；
 *  - 无障碍等价：整条滑轨带语义点击动作，与滑动路径共用
 *    [onConfirmRequested] 与 [enabled] 校验。
 */
@Composable
fun SlideToConfirmBar(
    text: String,
    stateToken: Any?,
    onConfirmRequested: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "slide_to_confirm",
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var contractState by remember {
        mutableStateOf(SlideToConfirmState(token = stateToken, enabled = enabled))
    }

    fun dispatch(event: SlideToConfirmEvent) {
        val transition = SlideToConfirmContract.reduce(contractState, event)
        contractState = transition.state
        scope.launch {
            progress.animateTo(
                targetValue = transition.state.progress,
                animationSpec = tween(
                    if (transition.state.progress == 0f) Tokens.PANEL_MS else 0,
                ),
            )
        }
        if (transition.confirmed) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onConfirmRequested()
        }
    }

    // 状态变化（revision 等）打断拖动：作废本次手势并回弹
    LaunchedEffect(stateToken) {
        dispatch(SlideToConfirmEvent.TokenChanged(stateToken))
    }
    LaunchedEffect(enabled) {
        dispatch(SlideToConfirmEvent.EnabledChanged(enabled))
    }

    val handlePx = with(density) { 48.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
            .semantics {
                role = Role.Button
                contentDescription = text
                this.testTag = testTag
                if (!enabled || contractState.locked) disabled()
                onClick(label = text) {
                    val before = contractState
                    dispatch(SlideToConfirmEvent.AccessibilityConfirm(stateToken))
                    before.enabled && !before.locked && before.token == stateToken
                }
            }
            .pointerInput(enabled, stateToken) {
                detectTapGestures(
                    onTap = {
                        // 轻点不提交（契约）
                    },
                )
            }
            .pointerInput(enabled, stateToken) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dispatch(SlideToConfirmEvent.Begin(stateToken))
                    },
                    onDragEnd = {
                        dispatch(SlideToConfirmEvent.Release(stateToken))
                    },
                    onDragCancel = {
                        dispatch(SlideToConfirmEvent.Cancel(stateToken))
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val range = (trackWidthPx - handlePx - 8f).coerceAtLeast(1f)
                    dispatch(SlideToConfirmEvent.Move(stateToken, dragAmount / range))
                }
            },
    ) {
        // 填充：跟随手指
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(
                    with(density) {
                        (progress.value * trackWidthPx).toDp()
                    },
                )
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(6.dp)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 72.dp),
            maxLines = 1,
        )
        // 手柄：起点在左端
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val x = (progress.value * (trackWidthPx - handlePx - 8f))
                        .coerceAtLeast(0f)
                    IntOffset(x.roundToInt(), 0)
                }
                .padding(start = 4.dp)
                .size(48.dp)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 14.dp, height = 2.dp)
                    .background(MaterialTheme.colorScheme.onPrimary),
            )
        }
    }
}
