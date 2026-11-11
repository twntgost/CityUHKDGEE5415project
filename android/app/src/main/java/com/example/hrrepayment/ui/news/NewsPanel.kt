package com.example.hrrepayment.ui.news

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.model.BetOutcome
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.ui.components.IconPlate
import com.example.hrrepayment.ui.components.SkyRule
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens
import com.example.hrrepayment.ui.workbench.BettingPanel

/**
 * 新闻层 v2：居中弹窗 + 展开/收起。
 *
 * The author asked for the news to (a) sit in the middle of the screen instead of
 * hanging off the top, (b) animate in like a dialog, and (c) gain a collapse button
 * that folds it back to its compact form.
 *
 * So the panel opens as a centred, rounded card that scales in, and its header keeps
 * an expand/collapse control plus a close button. Collapsed, only the header strip
 * remains — the “folded back to how it was” state — and the optional betting block
 * only exists while expanded.
 */
@Composable
fun NewsPanel(
    strings: Strings,
    state: GameState,
    content: ContentRef,
    busy: Boolean,
    date: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDecideSpecialLoan: (Boolean) -> Unit = {},
    onReadSpecialLoanInvitation: () -> Unit = {},
) {
    var showLoan by remember(state.saveId, state.position.current.index) { mutableStateOf(false) }
    if (showLoan && state.specialLoanDecision == null) {
        com.example.hrrepayment.ui.workbench.SpecialLoanDialog(strings, busy, onDecideSpecialLoan, { showLoan = false })
    }
    var expanded by remember { mutableStateOf(true) }
    val news = content.newsForWorkday(state.position.current.index)
    val enter = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x55140F08))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClose() }
            .testTag("tag_information_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                // 内容多少弹窗就多大：不再固定占 72% 屏高，只有超长才滚动（作者要求无冗余留白）
                .heightIn(max = 560.dp)
                .scale(enter.value)
                .clip(RoundedCornerShape(Tokens.CornerPanel.dp))
                .background(MaterialTheme.colorScheme.surface)
                // 点击弹窗内部不关闭
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .padding(Tokens.BlockGap.dp),
        ) {
            // 标题与两个按钮**分两行**：实机上字体放大后，三者挤在一行会把标题
            // 顶出容器（“2016-08-01 · 新”被截断并与按钮重叠）。标题独占一行后，
            // 任何字体缩放下都不会互相抢空间。
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPlate(GameIcons.News)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.format(Key.WB_OVERLAY_NEWS_TITLE, date),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!expanded) {
                    Text(
                        text = strings.format(Key.NEWS_COUNT, news.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("tag_news_toggle"),
                ) {
                    Text(
                        text = if (expanded) strings[Key.NEWS_COLLAPSE] else strings[Key.NEWS_EXPAND],
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = onClose, modifier = Modifier.testTag("tag_news_close")) {
                    Text(strings[Key.COMMON_CLOSE], color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
                    fadeIn(tween(Tokens.PANEL_MS)),
                exit = shrinkVertically(tween(Tokens.PANEL_MS)) { it / 3 } + fadeOut(tween(140)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                ) {
                    SkyRule()
                    Spacer(Modifier.height(Tokens.InnerGap.dp))
                    if (state.position.current.index == com.example.hrrepayment.core.engine.GameEngine.SPECIAL_LOAN_CLOSED_DAY) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.CornerTile.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp)
                            .testTag("tag_news_special_loan_closed")) {
                            Text(strings[Key.LOAN_CLOSED_TITLE], style = MaterialTheme.typography.titleMedium)
                            Text(strings[Key.LOAN_CLOSED_BODY], style = AppType.SerifSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(Tokens.InnerGap.dp))
                    }
                    if (state.phase == com.example.hrrepayment.core.model.Phase.DayEntry &&
                        state.position.current.index == com.example.hrrepayment.core.engine.GameEngine.CHAPTER2_FIRST_DAY && state.specialLoanDecision == null) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.CornerTile.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable(enabled = !busy) {
                                showLoan = true
                                if (state.specialLoanInvitationUnread) onReadSpecialLoanInvitation()
                            }.padding(14.dp)
                            .testTag("tag_news_special_loan")) {
                            if (state.specialLoanInvitationUnread) Text(strings[Key.LOAN_NEW],
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("tag_news_loan_unread"))
                            Text(strings[Key.LOAN_INVITE], style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error)
                            Text(strings[Key.LOAN_TEASER], style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(strings[Key.LOAN_UNDECIDED], style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(Tokens.InnerGap.dp))
                    }
                    if (news.isEmpty()) {
                        Text(strings[Key.WB_NEWS_EMPTY], style = MaterialTheme.typography.bodyMedium)
                    } else {
                        news.forEach { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Tokens.CornerTile.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Tokens.SkySoft)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            item.category,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Tokens.SkyDeep,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = item.body,
                                    style = AppType.SerifSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(Tokens.InnerGap.dp))
                        }
                    }

                    Spacer(Modifier.height(Tokens.InnerGap.dp))
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

