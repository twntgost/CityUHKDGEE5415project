package com.example.hrrepayment.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 顶部安全区内边距，并保证一个**最小高度下限**。
 *
 * 为什么不能只用 `statusBarsPadding()`：部分国产 ROM / 特殊刘海或手势配置下，
 * 窗口可能以非 edge-to-edge 方式布局，`WindowInsets.statusBars` 会返回 0，
 * 于是顶栏按钮直接压到状态栏底下。这里取「系统上报值」与 [min] 的较大者，
 * 任何机型上都不会贴到状态栏。
 */
@Composable
fun Modifier.safeTopInset(min: Dp = 26.dp): Modifier = composed {
    val reported = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    padding(top = if (reported < min) min else reported)
}
