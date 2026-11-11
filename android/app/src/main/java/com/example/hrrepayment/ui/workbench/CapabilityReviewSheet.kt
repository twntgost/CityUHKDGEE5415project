package com.example.hrrepayment.ui.workbench

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.model.Attributes
import com.example.hrrepayment.ui.theme.Tokens

/**
 * 状态面板的分区卡片（方案 B）。
 *
 * 视觉约定：
 *  - **财务夹** 与 **本月小结** 用白纸卡（和简历卡同一套纸张语言）；
 *  - **能力档案** 用卡其纸卡，形成“财务 / 个人能力 / 本月结论”的三段式层级；
 *  - 配色只用项目色板：主蓝（数据条）、卡其（分区）、绿（正向）、红（危险），
 *    不引入新颜色的前提下靠深浅与描边区分层级。
 */
@Composable
fun StatusSectionCard(
    strings: Strings,
    sectionKey: Key,
    icon: ImageVector,
    khaki: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.CornerTile.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (khaki) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f) else Color(0xFFFFFDF8))
            .border(
                BorderStroke(1.25.dp, if (khaki) Tokens.Secondary.copy(alpha = 0.45f) else Color(0xFFCFC3A8)),
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (khaki) Tokens.Secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = strings[sectionKey],
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** 能力档案：6 项属性 = 名称 + 数值/量程 + 比例条（工作能力额外标 20/30/40 阈值）。 */
@Composable
fun CapabilityArchive(
    strings: Strings,
    attributes: Attributes,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("tag_capability_sheet")) {
        AttributeStat(
            icon = com.example.hrrepayment.ui.theme.GameIcons.Ability,
            label = strings[Key.ATTR_WORK],
            value = attributes.work,
            reference = 50,
            thresholds = listOf(20, 30, 40),
            testTag = "tag_energy_work",
        )
        AttributeStat(
            icon = com.example.hrrepayment.ui.theme.GameIcons.Trust,
            label = strings[Key.ATTR_TRUST],
            value = attributes.trust,
            reference = 10,
            danger = attributes.trust < 0,
            testTag = "tag_energy_trust",
        )
        AttributeStat(
            icon = com.example.hrrepayment.ui.theme.GameIcons.Luck,
            label = strings[Key.ATTR_LUCK],
            value = attributes.luck,
            reference = 10,
            testTag = "tag_energy_luck",
        )
        AttributeStat(
            icon = com.example.hrrepayment.ui.theme.GameIcons.Health,
            label = strings[Key.ATTR_HEALTH],
            value = attributes.health,
            reference = 10,
            danger = attributes.health <= 2,
            testTag = "tag_energy_health",
        )
        AttributeStat(
            icon = com.example.hrrepayment.ui.theme.GameIcons.Dopamine,
            label = strings[Key.ATTR_DOPAMINE],
            value = attributes.dopamine,
            reference = 10,
            testTag = "tag_energy_dopamine",
        )
        AttributeStat(
            icon = com.example.hrrepayment.ui.theme.GameIcons.English,
            label = strings[Key.ATTR_ENGLISH],
            value = attributes.english,
            reference = 10,
            testTag = "tag_energy_english",
        )
    }
}

@Composable
private fun AttributeStat(
    icon: ImageVector,
    label: String,
    value: Int,
    reference: Int,
    testTag: String,
    thresholds: List<Int> = emptyList(),
    danger: Boolean = false,
) {
    val ratio = (value.toFloat() / reference.toFloat()).coerceIn(0f, 1f)
    val barColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else Tokens.Secondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$value/$reference",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(testTag),
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .height(4.dp)
                    .background(barColor),
            )
            // 阈值记号：只画线不写数字（数字统一在下方一行说明里，避免小屏拥挤）
            thresholds.forEach { threshold ->
                val x = (threshold.toFloat() / reference.toFloat()).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth(x)
                        .height(4.dp)
                        .padding(end = 1.dp)
                        .background(Color(0x00000000)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(x)
                        .height(4.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(1.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                    )
                }
            }
        }
    }
}
