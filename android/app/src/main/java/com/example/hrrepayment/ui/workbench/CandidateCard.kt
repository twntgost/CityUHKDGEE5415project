package com.example.hrrepayment.ui.workbench

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import com.example.hrrepayment.ui.components.MaterialAssets
import com.example.hrrepayment.ui.components.rememberAssetBitmaps
import kotlin.random.Random
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.core.content.CandidateContent
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.ui.components.AvatarImage
import com.example.hrrepayment.ui.components.HairLine
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens

/**
 * 一份简历（紧凑表格感）。选择必须**看得见**：被标记的陈述整行换底色 + 左侧对勾，
 * 120ms 过渡，不是只靠一个单选圆点。
 *
 * 揭示分级（v2.0 不变）：能力 ≤20 无红色标签，>20 提示复核字段，>30 给出造假类型
 * 与唯一假话，>40 给出真实情况与判定依据。
 */
@Composable
fun CandidateCard(
    candidate: CandidateContent,
    strings: Strings,
    selected: Boolean,
    workAbility: Int,
    busy: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    markedStatementId: String? = null,
    onMark: (String) -> Unit = {},
) {
    var showEvidence by remember(candidate.id) { mutableStateOf(false) }

    val shape = RoundedCornerShape(Tokens.CornerPanel.dp)
    Column(
        modifier
            .fillMaxWidth()
            // 简历是一张**白纸**：投影 + 硬描边，和卡其页面底色明确分层
            .shadow(
                elevation = if (selected) 10.dp else 5.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x55000000),
            )
            .background(Color(0xFFFFFDF8), shape)
            .border(
                BorderStroke(
                    if (selected) 2.dp else 1.25.dp,
                    if (selected) MaterialTheme.colorScheme.primary else Color(0xFFCFC3A8),
                ),
                shape,
            )
            .clip(shape),
    ) {
        // ② 卡头：候选人基本信息用**卡其色带**，与白色简历正文分区
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                AvatarImage(candidate.avatarAsset, Modifier.width(58.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(candidate.name, style = AppType.SerifTitle, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        strings.format(Key.WB_AGE_GENDER, candidate.genderLabel, candidate.age, candidate.role),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        strings.format(Key.WB_RESUME_NO, candidate.resumeNo),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Icon(
                        GameIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        // ③ 正文区：白色简历页
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {

        Spacer(Modifier.height(Tokens.InnerGap.dp))
        HairLine()
        if (candidate.statements.isNotEmpty()) {
            Text(
                text = strings.format(Key.WB_MARK_HINT, 2),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 玩法 A（简化版）：先给一条**免费线索**，玩家据此推理，而不是在 6 条里盲猜。
            // 只给一条、直接摊开、不用点开——既不增加操作，也不至于把答案送出去。
            candidate.evidence.firstOrNull()?.let { lead ->
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Tokens.SkySoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("tag_lead_${candidate.id}"),
                ) {
                    Text(
                        text = "${strings[Key.WB_LEAD_LABEL]} · ${lead.title}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Tokens.SkyDeep,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = lead.body,
                        style = AppType.SerifSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (candidate.statements.isEmpty()) {
            ResumeRow(strings[Key.FIELD_EDUCATION], candidate.educationSkills)
            ResumeRow(strings[Key.FIELD_WORK], candidate.workHistory)
        } else {
            // 只保留「背景 / 项目」两项（作者要求：选项太多），每组压成**一到两行**：
            // 同一字段的多条陈述用 “ · ” 连成一句，不再一条一行地占掉整屏。
            val groups = listOf(
                "educationSkills" to strings[Key.GROUP_BACKGROUND],
                "workExperience" to strings[Key.GROUP_PROJECT],
            )
            groups.forEach { (field, label) ->
                val items = candidate.statements.filter { it.field == field }
                if (items.isEmpty()) return@forEach
                val first = items.first()
                ClaimRow(
                    label = label,
                    // 每条陈述自带“学历形式：/毕业学校：”这类前缀，连成一句时纯属占地方，
                    // 去掉前缀只留内容，一到两行就能说完（作者要求压缩背景栏）。
                    text = items.joinToString(" · ") { stripLabel(it.text) },
                    marked = markedStatementId != null && items.any { it.id == markedStatementId },
                    enabled = !busy,
                    testTag = "tag_statement_${candidate.id}_${first.id}",
                    onClick = { onMark(first.id) },
                )
            }
            TextButton(
                onClick = { showEvidence = !showEvidence },
                modifier = Modifier.testTag("tag_evidence_${candidate.id}"),
            ) {
                Icon(
                    GameIcons.Dossier,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (showEvidence) strings[Key.WB_EVIDENCE_HIDE]
                    else strings.format(Key.WB_EVIDENCE_SHOW, candidate.evidence.size),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (showEvidence) {
                candidate.evidence.forEach { evidence ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Text(
                            evidence.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            evidence.body,
                            style = AppType.SerifSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        if (workAbility > 20) {
            RevealRow(
                icon = GameIcons.Warning,
                label = strings[Key.WB_REVIEW_LABEL],
                value = strings.format(
                    Key.WB_REVIEW_BODY,
                    when (candidate.reviewFieldCode) {
                        "educationSkills" -> strings[Key.FIELD_EDUCATION]
                        "workExperience" -> strings[Key.FIELD_WORK]
                        else -> strings[Key.FIELD_RESUME]
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (workAbility > 30) {
            RevealRow(GameIcons.Warning, strings[Key.WB_FRAUD_LABEL], candidate.fraudType, MaterialTheme.colorScheme.error)
            RevealRow(GameIcons.Warning, strings[Key.WB_LIE_LABEL], candidate.uniqueLie, MaterialTheme.colorScheme.error)
        }
        if (workAbility > 40) {
            RevealRow(GameIcons.Check, strings[Key.WB_TRUTH_LABEL], candidate.truth, Tokens.Success)
            RevealRow(GameIcons.Dossier, strings[Key.WB_BASIS_LABEL], candidate.judgementBasis, MaterialTheme.colorScheme.onSurface)
        }
    }
    }
}

/**
 * 去掉陈述里的字段前缀（“学历形式：”“毕业学校：”…）。
 * 只在分隔符前是很短的标签时才去掉，避免误伤正文里的冒号。
 */
private fun stripLabel(text: String): String {
    val index = text.indexOfFirst { it == '：' || it == ':' }
    if (index in 1..40) {
        val head = text.substring(0, index).trim()
        // 中文前缀 2–8 字；英文前缀放宽到 4 个词 / 24 字符
        // （"Education form"、"Employer and role" 这种都要能剥掉）。
        // 再长就当它是正文里的冒号，不动。
        val words = head.split(' ').size
        if ((head.length <= 8 && words <= 2) || (head.length <= 24 && words <= 4)) {
            return text.substring(index + 1).trim()
        }
    }
    return text
}

/**
 * 选择项：**标签 + 一行陈述**。
 *
 * 作者反馈“选项太多、背景信息占幅太多”，所以从“每条陈述一行”改成
 * “背景一行、项目一行”两个选项：标签在左，陈述折成一到两行，整行可点。
 * 选中态靠底色 + 对勾，120ms 过渡，不靠单选圆点。
 */
@Composable
private fun ClaimRow(
    label: String,
    text: String,
    marked: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    // 5 种手写红笔圈随机挑一个；选中后用“从左到右揭开”的方式画出来
    val inkCircles = rememberAssetBitmaps(MaterialAssets.INK_CIRCLES)
    var inkIndex by remember { mutableIntStateOf(0) }
    val inkReveal = remember { Animatable(0f) }
    LaunchedEffect(marked) {
        if (marked && inkCircles.isNotEmpty()) {
            inkIndex = Random.nextInt(inkCircles.size)
            inkReveal.snapTo(0f)
            inkReveal.animateTo(1f, tween(430, easing = FastOutSlowInEasing))
        } else {
            inkReveal.snapTo(0f)
        }
    }
    Box {

    val background by animateColorAsState(
        targetValue = if (marked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(Tokens.SELECT_MS),
        label = "claimBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(background, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (marked) GameIcons.Check else GameIcons.Locked,
            contentDescription = null,
            tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 3.dp).size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            // 34dp 是按中文两字算的；英文 “Background” 会被逐字折成
            // “Backgrou/nd”。改成“最小 34dp + 按内容自适应 + 绝不换行”。
            modifier = Modifier
                .widthIn(min = 34.dp)
                // 英文标签（Background/Project）较宽，补一点右侧间距，避免和正文黏在一起
                .padding(top = 2.dp, end = 6.dp)
                .testTag("tag_claim_label_${label}"),
            style = MaterialTheme.typography.labelLarge,
            color = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            // 五行封顶：中文两行就够；英文简化后最长约 130 字符，5 行可完整显示。
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            style = AppType.SerifSmall,
            color = if (marked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
        if (marked && inkCircles.isNotEmpty()) {
            Image(
                bitmap = inkCircles[inkIndex % inkCircles.size],
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = inkReveal.value
                        rotationZ = -2.5f + inkIndex * 1.2f
                    }
                    .drawWithContent {
                        // 从左往右揭开，像笔绕着这一行画了一圈
                        clipRect(right = size.width * inkReveal.value) { this@drawWithContent.drawContent() }
                    }
                    .testTag("tag_ink_circle_$label"),
            )
        }
    }
}

@Composable
private fun ResumeRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = AppType.SerifSmall, color = color)
    }
}

@Composable
private fun RevealRow(icon: ImageVector, label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            label,
            modifier = Modifier.width(58.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = AppType.SerifSmall, color = color)
    }
}
