package com.example.hrrepayment.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.ui.compositionlocals.LocalStrings
import com.example.hrrepayment.ui.theme.AppType

/**
 * 浏览器窗口内的**网页配色**：白页面 + 近黑正文 + 蓝色链接。
 * 简报里的内容都用 `MaterialTheme.colorScheme.*` 取色，所以换这套 scheme
 * 就会自动从“纸面卡其”变成“网页白”。
 */
private val WebScheme = lightColorScheme(
    primary = Color(0xFF1B6FC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F0FC),
    onPrimaryContainer = Color(0xFF0F4C86),
    secondary = Color(0xFF5C6470),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEFF2),
    onSecondaryContainer = Color(0xFF2B3038),
    tertiary = Color(0xFF2E7D5B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF5A5A5A),
    background = Color.White,
    onBackground = Color(0xFF1F1F1F),
    outline = Color(0xFFD6D6D6),
    outlineVariant = Color(0xFFE8E8E8),
    error = Color(0xFFC0392B),
    onError = Color.White,
    inverseSurface = Color(0xFF1F1F1F),
    inverseOnSurface = Color(0xFFF6F6F6),
)

/**
 * 「今日简报」做成一个**浏览器窗口**：
 *
 *  - 外壳：浅灰窗口色 + **硬边小圆角**（外 10dp / 页面 2dp），比之前的圆润卡片更利落；
 *  - 标题栏：三个窗口圆点 + 一条地址栏（`intranet.hr/hr-os/briefing`）；
 *  - 页面区：纯白网页 + 细分隔线，正文用深灰，强调色是网页蓝；
 *  - 底栏：`HR-OS 3.0 · 已连接` + 绿色状态点。
 *
 * 这个面板是**实心**的：工作台的背景暗纹会对它做避让，不会再叠在简报上面。
 */
@Composable
fun ScreenPanel(
    modifier: Modifier = Modifier,
    url: String = "intranet.hr/hr-os/briefing",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE6E4DE))
            .border(1.dp, Color(0xFFD3D1CA), RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        // ---- 窗口标题栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Color(0xFFE08A7E), Color(0xFFE8C07A), Color(0xFF8FC9A2)).forEach { dot ->
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(dot))
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFF7F6F2))
                    .border(1.dp, Color(0xFFDDDBD4), RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = url,
                    style = AppType.NoticeMonoTitle,
                    color = Color(0xFF6B6B6B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // ---- 页面区
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            MaterialTheme(colorScheme = WebScheme) {
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
        // ---- 底栏状态
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HR-OS 3.0", style = AppType.NoticeMonoTitle, color = Color(0xFF6B6B6B))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(Color(0xFF4CAF7D)))
            Spacer(Modifier.width(4.dp))
            Text(
                text = LocalStrings.current[Key.WEB_CONNECTED],
                style = AppType.NoticeMonoTitle,
                color = Color(0xFF6B6B6B),
            )
        }
    }
}
