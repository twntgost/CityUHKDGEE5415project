package com.example.hrrepayment.ui.workbench

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.engine.FinancialPreview
import com.example.hrrepayment.core.engine.GameEngine
import com.example.hrrepayment.core.engine.RepaymentPreset
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Story
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.i18n.loanStatus
import com.example.hrrepayment.core.model.Command
import com.example.hrrepayment.core.model.BetOutcome
import com.example.hrrepayment.core.model.CareerEventKind
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.ui.components.AttributeRow
import com.example.hrrepayment.ui.components.ButtonTone
import com.example.hrrepayment.ui.components.GameButton
import com.example.hrrepayment.ui.components.GameCard
import com.example.hrrepayment.ui.components.HeaderAction
import com.example.hrrepayment.ui.components.IconPlate
import com.example.hrrepayment.ui.components.NoticeTone
import com.example.hrrepayment.ui.components.PageIndicator
import com.example.hrrepayment.ui.components.PillLabel
import com.example.hrrepayment.ui.components.ResultStampDialog
import com.example.hrrepayment.ui.components.safeTopInset
import com.example.hrrepayment.ui.components.SettingsDialog
import com.example.hrrepayment.ui.components.ScreenPanel
import com.example.hrrepayment.ui.components.SectionTitle
import com.example.hrrepayment.ui.components.SkyRule
import com.example.hrrepayment.ui.components.StatTile
import com.example.hrrepayment.ui.components.ResultStampDialog
import com.example.hrrepayment.ui.components.StampKind
import com.example.hrrepayment.ui.components.SystemNotice
import com.example.hrrepayment.ui.components.moneyPattern
import com.example.hrrepayment.ui.components.WatermarkOverlay
import com.example.hrrepayment.ui.components.watermarkSeed
import com.example.hrrepayment.ui.components.watermarks
import com.example.hrrepayment.ui.news.NewsPanel
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens

/**
 * 招聘工作台 v2。
 *
 * 本轮排版改动（作者要求）：
 *  - 内容面板**垂直居中**，不再一律从顶部往下堆；
 *  - 顶部把日期块与入口重新排布，英文 Calendar 不再被挤成竖排；
 *  - 推送结果改成屏幕中央的圆角弹窗，180ms 内完成“打勾/打叉 + 描边”动画；
 *  - 简历页露出相邻页一角，换页提示挪到下方；
 *  - 经济面板使用钱的纹路背景；能力面板改用小组件网格（有横有竖）。
 */
@Composable
fun WorkbenchScreen(
    strings: Strings,
    state: GameState,
    content: ContentRef,
    busy: Boolean,
    onEnterRecruiting: () -> Unit,
    onBrowse: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onAcknowledgeResult: () -> Unit,
    onAcknowledgeEvent: (String) -> Unit,
    onChooseReward: (Command.SuccessRewardOption) -> Unit,
    onFinishIncome: () -> Unit,
    onRepayDebt: (String) -> Unit,
    onFinishEconomy: () -> Unit,
    onChooseMonthlySkill: (Command.MonthlySkillOption) -> Unit,
    onPlaceBet: (String, String, String) -> Unit,
    onMarkStatement: (String, String) -> Unit,
    onUseLuck: (Boolean) -> Unit,
    onOpenDossiers: () -> Unit,
    onReturnMenu: () -> Unit,
    onDecideSpecialLoan: (Boolean) -> Unit = {},
    onRepaySpecialLoan: () -> Unit = {},
    onReadSpecialLoanInvitation: () -> Unit = {},
    /** 设置面板：音效开关与存档/读档动作。 */
    soundEnabled: Boolean = true,
    onToggleSound: (Boolean) -> Unit = {},
    onSwitchLanguage: () -> Unit = {},
    onSaveNow: () -> Unit = {},
    onLoadNow: () -> Unit = {},
    /** 两张简历之间翻页的纸声。 */
    onFlipSound: () -> Unit = {},
    /** 红笔圈出造假部分的笔尖声。 */
    onPenSound: () -> Unit = {},
    /** 推送成功弹窗的盖章声。 */
    onStampSound: () -> Unit = {},
) {
    var overlay by remember { mutableStateOf<WorkbenchOverlay?>(null) }
    // 今日简报是一块“实心网页窗口”，背景暗纹要避开它的矩形，不能叠在简报上
    var briefingRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val dayIndex = state.position.current.index
    val workday = content.workdayInfo(dayIndex)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 实机（小米/HyperOS）上顶栏被状态栏压住：除了系统 insets，还要保证
                // 一个最小下限，否则个别机型上报 0 时顶栏会钻到状态栏底下。
                .safeTopInset()
                .padding(horizontal = Tokens.PageMargin.dp),
        ) {
            HeaderBar(
                strings = strings,
                state = state,
                date = workday.date,
                onStatus = { overlay = WorkbenchOverlay.STATUS },
                onNews = { overlay = WorkbenchOverlay.NEWS },
                onDossiers = onOpenDossiers,
                onOpenSettings = { overlay = WorkbenchOverlay.SETTINGS },
            )

            AnimatedContent(
                targetState = state.phase,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    (fadeIn(tween(Tokens.PAGE_MS)) + slideInVertically(tween(Tokens.PAGE_MS)) { it / 14 })
                        .togetherWith(fadeOut(tween(Tokens.PAGE_MS)))
                },
                label = "workbenchPhase",
            ) { phase ->
                when (phase) {
                    Phase.DayEntry -> DayEntryPanel(
                        strings, state, content, busy, onEnterRecruiting,
                        onOpenBetting = { overlay = WorkbenchOverlay.BETTING },
                        onDecideSpecialLoan = onDecideSpecialLoan,
                        onReadSpecialLoanInvitation = onReadSpecialLoanInvitation,
                        onBriefingMeasured = { briefingRect = it },
                    )
                    Phase.Recruiting -> RecruitingPanel(
                        strings = strings,
                        state = state,
                        content = content,
                        busy = busy,
                        onBrowse = onBrowse,
                        onSelect = onSelect,
                        onSubmit = onSubmit,
                        onMarkStatement = onMarkStatement,
                        onUseLuck = onUseLuck,
                        onFlipSound = onFlipSound,
                        onPenSound = onPenSound,
                    )
                    Phase.EventNotice -> EventNoticePanel(strings, state, busy, onAcknowledgeEvent)
                    Phase.SuccessReward -> RewardPanel(strings, busy, onChooseReward)
                    Phase.MonthIncome -> IncomePanel(strings, state, busy, onFinishIncome)
                    Phase.Repayment -> RepaymentPanel(strings, state, busy, onRepayDebt, onRepaySpecialLoan)
                    Phase.InterestCheck -> InterestPanel(strings, busy, onFinishEconomy)
                    Phase.MonthlySkill -> SkillPanel(strings, state, busy, onChooseMonthlySkill)
                    Phase.Ended -> com.example.hrrepayment.ui.ending.EndingPanel(strings, state, onReturnMenu)
                    // ResultAck 由屏幕中央的结果弹窗独占，这里留空。
                    Phase.ResultAck -> Box(Modifier.fillMaxSize())
                    else -> PendingPanel(strings, state)
                }
            }
            Spacer(Modifier.height(Tokens.InnerGap.dp))
            Spacer(Modifier.navigationBarsPadding())
        }

        // 结果：居中弹窗 + 180ms 打勾/打叉与描边动画
        if (state.phase == Phase.ResultAck) {
            ResultStampDialog(
                onStampSound = onStampSound,
                success = state.lastResultSuccess == true,
                title = strings[if (state.lastResultSuccess == true) Key.MSG_SUCCESS else Key.MSG_FAILURE],
                body = buildString {
                    append(strings[if (state.lastReviewCorrect == true) Key.MSG_REVIEW_CORRECT else Key.MSG_REVIEW_WRONG])
                    state.lastResultCandidateId?.let { id ->
                        runCatching { content.candidate(id) }.getOrNull()?.let {
                            append("\n")
                            append(it.judgementBasis)
                        }
                    }
                    if (state.lastResultForced) {
                        append("\n")
                        append(strings[Key.MSG_GUARANTEED])
                    } else if (state.lastLuckUsed > 0) {
                        append("\n")
                        append(strings.format(Key.MSG_LUCK_USED, state.lastLuckUsed))
                    }
                    if (state.lastResultTrustPenalty > 0) {
                        append("\n")
                        append(strings.format(Key.MSG_TRUST_DROP, state.lastResultTrustPenalty))
                    }
                },
                footer = buildString {
                    append(strings.format(Key.WB_RESULT_TOTALS, state.stats.globalSuccessCount, state.stats.globalFailureCount))
                    if (state.lastResultForced) append(strings[Key.WB_RESULT_FORCED])
                },
                continueLabel = strings[Key.COMMON_CONTINUE],
                onContinue = onAcknowledgeResult,
            )
        }

        AnimatedVisibility(
            visible = overlay != null,
            // 覆盖层进入也用定时补间：弹簧在展开/收起时会“弹一下”，观感很廉价
            enter = fadeIn(tween(Tokens.PANEL_MS)) + scaleIn(tween(Tokens.PANEL_MS), initialScale = 0.96f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.98f),
        ) {
            when (overlay) {
                WorkbenchOverlay.NEWS -> NewsPanel(
                    strings = strings,
                    state = state,
                    content = content,
                    busy = busy,
                    date = workday.date,
                    onClose = { overlay = null },
                    onDecideSpecialLoan = onDecideSpecialLoan,
                    onReadSpecialLoanInvitation = onReadSpecialLoanInvitation,
                )
                WorkbenchOverlay.BETTING -> BettingDialog(
                    strings = strings,
                    state = state,
                    content = content,
                    busy = busy,
                    onPlaceBet = onPlaceBet,
                    // 下注或取消后弹窗自行关闭（作者要求）
                    onDismiss = { overlay = null },
                )
                WorkbenchOverlay.SETTINGS -> SettingsDialog(
                    strings = strings,
                    soundEnabled = soundEnabled,
                    busy = busy,
                    onToggleSound = onToggleSound,
                    onSwitchLanguage = onSwitchLanguage,
                    onSaveNow = onSaveNow,
                    onLoadNow = onLoadNow,
                    onReturnMenu = onReturnMenu,
                    onDismiss = { overlay = null },
                )
                else -> StatusPanel(strings, state, onClose = { overlay = null }, onReturnMenu = onReturnMenu)
            }
        }

        // 暗纹覆盖层放在最后：卡片、面板、弹窗都盖不住它（作者要求）
        WatermarkOverlay(watermarkSeed(state.phase.ordinal, dayIndex), avoid = briefingRect)
    }
}

private enum class WorkbenchOverlay { STATUS, NEWS, BETTING, SETTINGS }

// -------------------------------------------------------------------- 顶栏

/**
 * 顶栏：左侧两个入口、右侧日期与日历。
 *
 * Every label is single-line and non-wrapping, and the date block is the flexible
 * part — that is what fixes the English build where “Calendar” was squeezed into a
 * vertical stack of letters.
 */
@Composable
private fun HeaderBar(
    strings: Strings,
    state: GameState,
    date: String,
    onStatus: () -> Unit,
    onNews: () -> Unit,
    onDossiers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // 顶栏改成两行：第一行是入口，第二行居中放日期。
    // 这样中文/英文、长月份文案都不会再互相挤压（英文 Calendar 曾被挤成竖排）。
    Column(Modifier.fillMaxWidth().padding(top = Tokens.InnerGap.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAction(
                text = strings[Key.WB_STATUS],
                icon = GameIcons.Status,
                onClick = onStatus,
                testTag = "tag_status_menu",
            )
            Spacer(Modifier.width(8.dp))
            HeaderAction(
                text = strings[Key.DOSSIER_SHORT],
                icon = GameIcons.Dossier,
                onClick = onDossiers,
                testTag = "tag_workbench_dossier",
                paperTexture = true,
            )
            Spacer(Modifier.weight(1f))
            HeaderAction(
                text = strings[Key.WB_CALENDAR] + if (state.specialLoanInvitationUnread) " •" else "",
                icon = GameIcons.Calendar,
                onClick = onNews,
                testTag = "tag_news_menu",
            )
            Spacer(Modifier.width(6.dp))
            // 齿轮：设置（音效/语言/存档/读档/返回首页）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenSettings() }
                    .size(36.dp)
                    .testTag("tag_settings_menu"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GameIcons.Settings,
                    contentDescription = strings[Key.SETTINGS_TITLE],
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        // 日期与“第X月·第Y个工作日”上下叠放、整体居中（作者要求换行居中叠放）
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date,
                style = AppType.DayDate,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(6.dp))
            PillLabel(
                text = if (state.phase == Phase.Ended) {
                    strings[Key.PHASE_Ended]
                } else {
                    strings.format(
                        Key.WB_DAY_HEADER,
                        state.position.current.monthIndex + 1,
                        state.position.current.dayOfMonth + 1,
                    )
                },
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun LoanStatusBlock(strings: Strings, state: GameState) {
    val status = strings.loanStatus(state) ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("tag_loan_status")) {
        if (state.specialLoanDecision == true) {
            Text(strings[Key.LOAN_PRINCIPAL], style = MaterialTheme.typography.bodyMedium)
            Text(strings[Key.LOAN_TERMS_SUMMARY], style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(status, style = MaterialTheme.typography.bodyMedium,
            color = if (state.specialLoanDue > Money.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(if (state.specialLoanDue > Money.ZERO) "tag_special_loan_due" else "tag_special_loan_record"))
        if (state.specialLoanDecision == true && state.position.current.index == GameEngine.CHAPTER2_FIRST_DAY) {
            Text(strings[Key.LOAN_ACCEPTED_RECORD], style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ------------------------------------------------------------------ 状态层

/**
 * 能力面板：小组件网格。
 *
 * 钱务/统计排成横向小卡，六个属性排成竖向能量格，版式有横有竖。
 */
@Composable
private fun StatusPanel(strings: Strings, state: GameState, onClose: () -> Unit, onReturnMenu: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("tag_information_overlay"),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeTopInset(min = 8.dp)
                .padding(Tokens.PageMargin.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconPlate(GameIcons.Status)
                    Spacer(Modifier.width(8.dp))
                    Text(strings[Key.WB_OVERLAY_STATUS_TITLE], style = AppType.PanelTitle)
                }
                TextButton(onClick = onClose, modifier = Modifier.testTag("tag_status_close")) {
                    Text(strings[Key.COMMON_CLOSE], color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(6.dp))
            SkyRule()
            Spacer(Modifier.height(Tokens.BlockGap.dp))

            // ---------------------------------------------------- 财务夹（白纸）
            StatusSectionCard(
                strings = strings,
                sectionKey = Key.STATUS_FINANCE_SECTION,
                icon = GameIcons.Cash,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.InnerGap.dp)) {
                    StatTile(
                        icon = GameIcons.Cash,
                        label = strings[Key.ATTR_CASH],
                        value = strings.format(Key.MONEY_CNY, state.cash.formatYuan()),
                        emphasised = true,
                        money = true,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = GameIcons.Debt,
                        label = strings[Key.DEBT_ORDINARY],
                        value = strings.format(Key.MONEY_CNY, state.debt.formatYuan()),
                        valueColor = MaterialTheme.colorScheme.error,
                        money = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Tokens.InnerGap.dp))
                LoanStatusBlock(strings, state)
                Text(
                    text = strings.format(Key.DEBT_TOTAL, (state.debt + state.specialLoanDue).formatYuan()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("tag_total_debt"),
                )
                Spacer(Modifier.height(Tokens.InnerGap.dp))
                DebtProgressBar(strings, state)
                Spacer(Modifier.height(Tokens.InnerGap.dp))
                StatTile(
                    icon = GameIcons.Cash,
                    label = strings[Key.WB_EFFECTIVE_SALARY],
                    value = strings.format(Key.MONEY_CNY, state.monthlySalary.formatYuan()),
                    modifier = Modifier.fillMaxWidth(),
                    money = true,
                )
                Spacer(Modifier.height(Tokens.InnerGap.dp))
                // 本月结论并进财务夹，面板整体缩短一屏
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.InnerGap.dp)) {
                    StatTile(
                        icon = GameIcons.Trophy,
                        label = strings[Key.WB_TOTAL_HIRES],
                        value = state.stats.globalSuccessCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = GameIcons.Check,
                        label = strings[Key.WB_REVIEW_ACCURACY],
                        value = "${state.stats.correctReviewCount}/${state.stats.reviewedSubmissionCount}",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = GameIcons.Luck,
                        label = strings[Key.WB_LUCK_BONUS],
                        value = strings.format(
                            Key.WB_LUCK_BONUS_VALUE,
                            state.attributes.luck.coerceIn(0, GameEngine.MAX_LUCK_BOOST),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(Tokens.BlockGap.dp))

            // ---------------------------------------------------- 能力档案（卡其纸）
            StatusSectionCard(
                strings = strings,
                sectionKey = Key.STATUS_CAPABILITY_SECTION,
                icon = GameIcons.Ability,
                khaki = true,
            ) {
                CapabilityArchive(strings = strings, attributes = state.attributes)
            }

            Spacer(Modifier.height(Tokens.BlockGap.dp))

            Spacer(Modifier.height(Tokens.BlockGap.dp))
            GameButton(
                text = strings[Key.ENDING_RETURN_HOME],
                onClick = onReturnMenu,
                tone = ButtonTone.OUTLINED,
                icon = GameIcons.Dossier,
                testTag = "tag_return_menu",
            )
            Spacer(Modifier.height(24.dp))
        }

        // 能力面板也要能看到暗纹：覆盖层放最后，卡片盖不住
        WatermarkOverlay(watermarkSeed(91))
    }
}

/** 还债进度：已还 / 初始债务，一条带百分比的细进度条。 */
@Composable
private fun DebtProgressBar(strings: Strings, state: GameState) {
    val initial = Money(GameState.ORDINARY_DEBT_CAP_CENTS)
    val paid = Money((initial.cents - state.debt.cents).coerceAtLeast(0L))
    val ratio = if (initial.cents <= 0L) 0f
    else (paid.cents.toFloat() / initial.cents.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings[Key.STATUS_DEBT_PROGRESS],
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = strings.format(
                Key.STATUS_DEBT_PROGRESS_VALUE,
                strings.format(Key.MONEY_CNY, paid.formatYuan()),
                strings.format(Key.MONEY_CNY, initial.formatYuan()),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("tag_debt_progress"),
        )
    }
}

// ------------------------------------------------------------------ 各阶段面板

/** 内容垂直居中；内容高于屏幕时仍可滚动。 */
@Composable
private fun CenteredPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun DayEntryPanel(
    strings: Strings,
    state: GameState,
    content: ContentRef,
    busy: Boolean,
    onEnter: () -> Unit,
    onOpenBetting: () -> Unit,
    onDecideSpecialLoan: (Boolean) -> Unit,
    onReadSpecialLoanInvitation: () -> Unit,
    onBriefingMeasured: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    var showLoan by remember(state.saveId, state.position.current.index) { mutableStateOf(false) }
    if (showLoan && state.specialLoanDecision == null) {
        SpecialLoanDialog(strings, busy, onDecideSpecialLoan, { showLoan = false })
    }
    val workday = content.workdayInfo(state.position.current.index)
    val lastSuccess = state.lastResultSuccess
    CenteredPanel {
        if (lastSuccess != null) {
            SystemNotice(
                title = strings[Key.WB_LAST_RESULT],
                body = strings[if (lastSuccess) Key.MSG_SUCCESS else Key.MSG_FAILURE],
                footer = strings.format(
                    Key.WB_RESULT_TOTALS,
                    state.stats.globalSuccessCount,
                    state.stats.globalFailureCount,
                ),
                icon = if (lastSuccess) GameIcons.Check else GameIcons.Warning,
                tone = if (lastSuccess) NoticeTone.SUCCESS else NoticeTone.DANGER,
                testTag = "tag_result_block",
            )
            Spacer(Modifier.height(Tokens.BlockGap.dp))
        }
        // 今日简报：只放当天真实信息（岗位、两位候选人、保底、运气），
        // 不再用“看仔细一点”这类每天一样的套话（作者反馈“看起来很蠢”）。
        val today = content.workdayCandidates(state.position.current.index)
        ScreenPanel(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onBriefingMeasured(it.boundsInRoot()) }
                .testTag("tag_daily_briefing"),
            url = "intranet.hr/hr-os/briefing",
        ) {
                Text(strings[Key.WB_BRIEF_TITLE], style = AppType.EpicKicker, color = MaterialTheme.colorScheme.primary)
                if (state.position.current.index == GameEngine.CHAPTER2_FIRST_DAY && state.specialLoanDecision == null) {
                    TextButton(onClick = {
                        showLoan = true
                        if (state.specialLoanInvitationUnread) onReadSpecialLoanInvitation()
                    }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("tag_special_loan_invite")) {
                        Column {
                            if (state.specialLoanInvitationUnread) Text(strings[Key.LOAN_NEW],
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("tag_special_loan_unread"))
                            Text(strings[Key.LOAN_INVITE], style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error)
                            Text(strings[Key.LOAN_TEASER], style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                LoanStatusBlock(strings, state)
                Spacer(Modifier.height(10.dp))
                workday.demand?.let { demand ->
                    Text(
                        text = "${strings[Key.WB_BRIEF_POOL]}：${demand.skill}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = demand.experience,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = demand.task,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    SkyRule()
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = strings.format(Key.WB_BRIEF_CANDIDATES, today.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                today.forEach { candidate ->
                    Text(
                        text = strings.format(
                            Key.WB_BRIEF_INTERVIEW,
                            candidate.name,
                            candidate.age,
                            candidate.role,
                        ),
                        style = AppType.SerifSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(10.dp))
                SkyRule()
                Spacer(Modifier.height(10.dp))
                val period = GameEngine.pityPeriodFor(state.guaranteeTier)
                Text(
                    text = if (period > 0) {
                        strings.format(Key.WB_BRIEF_PITY_ON, state.guaranteePeriodDraws, period)
                    } else {
                        strings[Key.WB_BRIEF_PITY_OFF]
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (state.attributes.luck > 0) {
                        strings.format(Key.WB_BRIEF_LUCK, state.attributes.luck)
                    } else {
                        strings[Key.WB_BRIEF_LUCK_NONE]
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 上月底的调薪：只在次月第一个工作日播报
                state.salaryReport?.let { report ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.format(
                            Key.WB_BRIEF_SALARY,
                            report.to.formatYuan(),
                            report.from.formatYuan(),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("tag_brief_salary"),
                    )
                }
                // 竞猜：等下注时显示待结算，结算当天直接播报结果（作者要求）
                val betLine: Pair<String, androidx.compose.ui.graphics.Color>? = when {
                    state.activeBet != null -> strings.format(
                        Key.WB_BRIEF_BET_PENDING, state.activeBet!!.amount.formatYuan(),
                    ) to MaterialTheme.colorScheme.onSurfaceVariant

                    state.todayBetOutcome == BetOutcome.WON -> strings.format(
                        Key.WB_BRIEF_BET_WON, state.todayBetPayout?.formatYuan() ?: "0.00",
                    ) to Tokens.Success

                    state.todayBetOutcome == BetOutcome.LOST -> strings.format(
                        Key.WB_BRIEF_BET_LOST, state.attributes.luck,
                    ) to MaterialTheme.colorScheme.error

                    state.todayBetOutcome == BetOutcome.CANCELLED -> strings.format(
                        Key.WB_BRIEF_BET_CANCELLED, state.todayBetPayout?.formatYuan() ?: "0.00",
                    ) to MaterialTheme.colorScheme.onSurfaceVariant

                    else -> null
                }
                state.activeBet?.let { bet ->
                    Text(strings.format(Key.BET_PENDING_DATE, content.workdayInfo(bet.settleWorkdayIndex).date),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("tag_pending_bet_date"))
                }
                if (betLine != null) {
                    Spacer(Modifier.height(10.dp))
                    SkyRule()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = strings[Key.WB_BRIEF_BET],
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = betLine.first,
                        style = MaterialTheme.typography.bodyMedium,
                        color = betLine.second,
                        modifier = Modifier.testTag("tag_brief_bet"),
                    )
                }
                // 今天的人才动态：谁离职了、谁晋升了（信任随之增减）
                if (state.todayCareerEvents.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SkyRule()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = strings[Key.WB_BRIEF_CAREER],
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    state.todayCareerEvents.forEach { event ->
                        val name = runCatching { content.candidate(event.candidateId).name }.getOrDefault(event.candidateId)
                        val (text, color) = when (event.kind) {
                            CareerEventKind.RESIGNED ->
                                strings.format(Key.WB_BRIEF_RESIGNED, name) to MaterialTheme.colorScheme.error
                            CareerEventKind.PROMOTED -> {
                                val again = state.hired
                                    .firstOrNull { it.candidateId == event.candidateId }
                                    ?.promotions ?: 1
                                val key = if (again > 1) Key.WB_BRIEF_PROMOTED_AGAIN else Key.WB_BRIEF_PROMOTED
                                strings.format(key, name) to Tokens.Success
                            }
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            modifier = Modifier.testTag("tag_brief_career_${event.candidateId}"),
                        )
                    }
                }
            }
        // 竞猜入口：作者反馈“太隐晦”。有可竞猜的日期就在每日简报下面直接给一张入口卡，
        // 不用先点开新闻再找。第一次（还没下过注）会先自言自语一句“这或许是翻身机会。”
        // 只有“今天这一期还没结算、也没有待结算注单”时才给入口，
        // 否则刚结算完当天还挂着“去竞猜”，会让人以为可以重复下注。
        val todayMarket = content.bettingMarkets().firstOrNull {
            it.openWorkdayIndex == state.position.current.index &&
                it.id !in state.settledBetMarketIds
        }
        if (todayMarket != null && state.activeBet == null && state.todayBetOutcome == null) {
            Spacer(Modifier.height(Tokens.BlockGap.dp))
            val neverBet = state.lastBetOutcome == null && state.lastBetPayout == null
            GameCard(Modifier.fillMaxWidth().testTag("tag_bet_entry_card")) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconPlate(GameIcons.Luck)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (neverBet) strings[Key.BET_SELF_TALK] else strings[Key.BET_ENTRY_TITLE],
                                style = if (neverBet) AppType.Prose else MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = strings[Key.BET_ENTRY_BODY],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(strings.format(Key.BET_DATES, content.workdayInfo(todayMarket.openWorkdayIndex).date,
                        content.workdayInfo(todayMarket.settleWorkdayIndex).date),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("tag_bet_entry_dates"))
                    Spacer(Modifier.height(10.dp))
                    GameButton(
                        text = strings[Key.BET_ENTRY_ACTION],
                        onClick = onOpenBetting,
                        tone = ButtonTone.SKY_SOFT,
                        icon = GameIcons.Luck,
                        modifier = Modifier.fillMaxWidth(),
                        wide = true,
                        testTag = "tag_bet_entry",
                    )
                }
            }
        }
        Spacer(Modifier.height(Tokens.BlockGap.dp))
        GameButton(
            text = strings[Key.WB_VIEW_RESUMES],
            onClick = onEnter,
            enabled = !busy,
            icon = GameIcons.Push,
            modifier = Modifier.fillMaxWidth(),
            wide = true,
            testTag = "tag_day_entry_next",
        )
    }
}

@Composable
private fun RecruitingPanel(
    strings: Strings,
    state: GameState,
    content: ContentRef,
    busy: Boolean,
    onBrowse: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onMarkStatement: (String, String) -> Unit,
    onUseLuck: (Boolean) -> Unit,
    onFlipSound: () -> Unit,
    onPenSound: () -> Unit,
) {
    val dayIndex = state.position.current.index
    val candidates = content.workdayCandidates(dayIndex)
    val frontIndex = state.browsedCandidateIndex.coerceIn(0, candidates.lastIndex)
    val backIndex = candidates.indices.firstOrNull { it != frontIndex }

    Column(modifier = Modifier.fillMaxSize()) {
        // 岗位需求占满整行并完整换行：英文文案很长，之前和有图标/保底挤在一行会被截断
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlate(GameIcons.Dossier)
            Spacer(Modifier.width(8.dp))
            Text(
                text = strings[Key.WB_DEMAND_LABEL],
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.weight(1f))
            PityHint(strings, state)
        }
        content.workdayInfo(dayIndex).demand?.let { demand ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = demand.skill,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = demand.experience,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = demand.task,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 两张简历**叠放**：下面那张露出边缘，点它就把那张抽到最上面（作者要求）。
        // 翻页瞬间配极短的纸声，替代原来的左右滑 PageIndicator 版面。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .weight(1f)
                .testTag("tag_candidate_pager"),
        ) {
            if (backIndex != null) {
                // 后排只做“一张纸”的边缘：不透出正文（英文长文案会糊成一团），
                // 只显示另一位候选人的姓名 + 本地化切换提示。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 26.dp, start = 16.dp, end = 16.dp)
                        .graphicsLayer {
                            scaleX = 0.975f; scaleY = 0.975f
                            rotationZ = -1.9f
                            alpha = 0.95f
                        }
                        .clip(RoundedCornerShape(Tokens.CornerPanel.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable(enabled = !busy) {
                            onFlipSound()
                            onBrowse(backIndex)
                        }
                        .testTag("tag_candidate_stack_back"),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = candidates[backIndex].name + " · " + strings[Key.WB_STACK_HINT],
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            AnimatedContent(
                targetState = frontIndex,
                transitionSpec = {
                    // 抽到最上面：从下方轻微上浮 + 放大收定，像把纸抽出来
                    (fadeIn(tween(180)) + scaleIn(initialScale = 1.04f, animationSpec = tween(200))) togetherWith
                        (fadeOut(tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)))
                },
                label = "candidateStack",
                modifier = Modifier.fillMaxSize().testTag("tag_candidate_stack_front"),
            ) { idx ->
                CandidateCard(
                    candidate = candidates[idx],
                    strings = strings,
                    selected = state.selectedCandidateId == candidates[idx].id,
                    workAbility = state.attributes.work,
                    busy = busy,
                    onSelect = { onSelect(candidates[idx].id) },
                    markedStatementId = state.markedStatements[candidates[idx].id],
                    onMark = { statementId ->
                        onPenSound()
                        onMarkStatement(candidates[idx].id, statementId)
                    },
                    modifier = Modifier.fillMaxSize().padding(bottom = 22.dp),
                )
            }
        }

        Spacer(Modifier.height(Tokens.InnerGap.dp))
        if (state.attributes.luck > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GameIcons.Luck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = state.useLuckOnSubmission && !GameEngine.isForcedNext(state),
                    onCheckedChange = onUseLuck,
                    enabled = !busy && !GameEngine.isForcedNext(state),
                    modifier = Modifier.testTag("tag_use_luck"),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (GameEngine.isForcedNext(state)) strings[Key.WB_LUCK_KEPT]
                    else strings.format(Key.WB_LUCK_ON, state.attributes.luck),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        // 换页提示挪到下方
        PageIndicator(
            current = frontIndex,
            total = candidates.size,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Tokens.InnerGap.dp))
        GameButton(
            text = strings.format(Key.WB_PUSH, candidates[frontIndex].name),
            onClick = onSubmit,
            enabled = !busy && state.markedStatements.containsKey(candidates[frontIndex].id),
            icon = GameIcons.Push,
            testTag = "tag_submit_candidate",
        )
    }
}

@Composable
private fun PityHint(strings: Strings, state: GameState) {
    val period = GameEngine.pityPeriodFor(state.guaranteeTier)
    if (period <= 0) return
    val remaining = (period - state.guaranteePeriodDraws).coerceIn(1, period)
    PillLabel(
        text = strings.format(Key.WB_PITY, remaining),
        modifier = Modifier.testTag("tag_pity_hint"),
    )
}

/**
 * 剧情事件（离职、入冬感冒、外派通知…）改用与推送结果同一套**弹窗形式**：
 * 居中圆角对话框 + ≤0.2s 的印章动画（事件用感叹号，不抢勾/叉的语义），
 * 这样“原剧情事件”和“推送结果”在视觉上是同一类东西（作者要求）。
 */
@Composable
private fun EventNoticePanel(strings: Strings, state: GameState, busy: Boolean, onAcknowledge: (String) -> Unit) {
    val eventId = state.pendingEventId ?: return
    val extra = if (eventId == GameEngine.EVENT_WINTER_COLD) {
        strings.format(Key.EVENT_WINTER_HEALTH, state.attributes.health)
    } else {
        null
    }
    ResultStampDialog(
        success = true,
        stamp = StampKind.BANG,
        testTag = "tag_event_panel",
        title = if (eventId == GameEngine.EVENT_SPECIAL_LOAN_CLOSED) strings[Key.LOAN_CLOSED_TITLE] else Story.eventTitle(strings.lang, eventId),
        body = buildString {
            if (eventId == GameEngine.EVENT_SPECIAL_LOAN_CLOSED) {
                append(strings[Key.LOAN_CLOSED_BODY])
                append("\n\n")
                append(strings[if (state.specialLoanForgiven) Key.LOAN_FORGIVEN else Key.LOAN_CLOSED_OTHER])
            } else append(Story.eventBody(strings.lang, eventId))
            if (extra != null) {
                append("\n")
                append(extra)
            }
        },
        footer = null,
        continueLabel = strings[if (eventId == GameEngine.EVENT_SPECIAL_LOAN_CLOSED) Key.RETURN_BRIEFING else Key.EVENT_ACK],
        onContinue = { onAcknowledge(eventId) },
        continueTestTag = "tag_event_ack",
    )
}

/** 经济面板统一外观：钱的纹路背景 + 圆角卡，与下方能力面板区分。 */
@Composable
private fun MoneyPanel(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.CornerPanel.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .moneyPattern(Tokens.Secondary)
            .padding(Tokens.BlockGap.dp),
    ) { content() }
}

@Composable
private fun IncomePanel(strings: Strings, state: GameState, busy: Boolean, onFinish: () -> Unit) {
    CenteredPanel {
        MoneyPanel {
            Column {
                Text(strings[Key.INCOME_TITLE], style = AppType.PanelTitle)
                Spacer(Modifier.height(8.dp))
                StatTile(
                    icon = GameIcons.Cash,
                    label = strings[Key.WB_EFFECTIVE_SALARY],
                    value = strings.format(Key.MONEY_CNY, state.monthlySalary.formatYuan()),
                    emphasised = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.format(Key.INCOME_BODY, strings.format(Key.MONEY_CNY, state.monthlySalary.formatYuan())),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                GameButton(strings[Key.INCOME_CONFIRM], onFinish, enabled = !busy, testTag = "tag_finish_income")
            }
        }
    }
}

@Composable
private fun RepaymentPanel(strings: Strings, state: GameState, busy: Boolean, onRepay: (String) -> Unit, onRepaySpecialLoan: () -> Unit) {
    var confirmSpecialLoan by remember { mutableStateOf(false) }
    if (confirmSpecialLoan && state.specialLoanDue > Money.ZERO) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmSpecialLoan = false },
            title = { Text(strings[Key.LOAN_REPAY]) },
            text = { Text(strings.format(Key.LOAN_REPAY_PREVIEW, (state.cash - state.specialLoanDue).formatYuan())) },
            confirmButton = { TextButton(onClick = onRepaySpecialLoan, enabled = !busy) { Text(strings[Key.LOAN_REPAY]) } },
            dismissButton = { TextButton(onClick = { confirmSpecialLoan = false }, enabled = !busy) { Text(strings[Key.COMMON_CANCEL]) } },
        )
    }
    var amount by remember(state.position.current.monthIndex) { mutableStateOf("0") }
    var dangerToken by remember { mutableStateOf<String?>(null) }
    val parsed = Money.parseYuan(amount).getOrNull()
    val valid = parsed != null && parsed <= state.cash && parsed <= state.debt
    val preview = if (valid) FinancialPreview.repayment(state, parsed!!) else null
    val token = "${state.saveId}:${state.revision}:$amount"

    CenteredPanel {
        MoneyPanel {
            Column {
                Text(strings[Key.REPAY_TITLE], style = AppType.PanelTitle)
                LoanStatusBlock(strings, state)
                if (state.specialLoanDue > Money.ZERO) {
                    TextButton(onClick = { confirmSpecialLoan = true },
                        enabled = !busy && state.cash >= state.specialLoanDue,
                        modifier = Modifier.testTag("tag_special_loan_repay")) {
                        Text(strings[Key.LOAN_REPAY])
                    }
                }
                Spacer(Modifier.height(8.dp))
                SkyRule()
                Text(strings[Key.REPAY_ORDINARY_TITLE], style = MaterialTheme.typography.titleMedium)
                Text(strings[Key.REPAY_SEPARATE_HINT], style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.InnerGap.dp)) {
                    StatTile(
                        icon = GameIcons.Cash,
                        label = strings[Key.ATTR_CASH],
                        value = state.cash.formatYuan(),
                        emphasised = true,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = GameIcons.Debt,
                        label = strings[Key.DEBT_ORDINARY],
                        value = state.debt.formatYuan(),
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.format(
                        Key.REPAY_PAID,
                        state.lastSalaryPaid?.formatYuan() ?: strings[Key.REPAY_SETTLED],
                        state.lastBonusPaid?.formatYuan() ?: strings[Key.REPAY_SETTLED],
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    strings.format(Key.REPAY_NEXT_SALARY, state.nextMonthlySalary.formatYuan()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    RepaymentPreset.entries.forEach { preset ->
                        val label = when (preset) {
                            RepaymentPreset.NONE -> strings[Key.REPAY_PRESET_NONE]
                            RepaymentPreset.KEEP_6000 -> strings[Key.REPAY_PRESET_KEEP6000]
                            RepaymentPreset.KEEP_SALARY -> strings[Key.REPAY_PRESET_KEEP_SALARY]
                            RepaymentPreset.MAXIMUM -> strings[Key.REPAY_PRESET_MAX]
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = { amount = FinancialPreview.preset(state, preset).formatYuan() },
                            modifier = Modifier.testTag("tag_repay_preset_${preset.name}"),
                        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    label = { Text(strings[Key.REPAY_AMOUNT_LABEL]) },
                )
                preview?.let {
                    Text(
                        strings.format(Key.REPAY_PREVIEW, it.cashAfter.formatYuan(), it.debtAfter.formatYuan()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("tag_repay_preview"),
                    )
                    if (it.annualInterest) {
                        Text(
                            strings.format(Key.REPAY_AFTER_INTEREST, it.debtAfterInterest.formatYuan()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (it.cashExhausted) {
                        Text(strings[Key.REPAY_DANGER_CASH], color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (it.debtCapExceeded) {
                        Text(strings[Key.REPAY_DANGER_CAP], color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                FinancialPreview.minimumAnnualRepayment(state)?.let {
                    Text(
                        text = strings.format(Key.REPAY_MIN_ANNUAL, it.formatYuan()) +
                            if (it > state.cash) strings[Key.REPAY_CASH_SHORT] else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                GameButton(
                    text = strings[Key.REPAY_ORDINARY_CONFIRM],
                    onClick = {
                        if (preview?.cashExhausted == true || preview?.debtCapExceeded == true) dangerToken = token
                        else onRepay(amount)
                    },
                    enabled = !busy && valid,
                    modifier = Modifier.fillMaxWidth(),
                    wide = true,
                    testTag = "tag_repay",
                )
            }
        }
    }
    if (dangerToken != null) {
        AlertDialog(
            onDismissRequest = { dangerToken = null },
            title = { Text(strings[Key.REPAY_DANGER_TITLE]) },
            text = { Text(strings[Key.REPAY_DANGER_BODY]) },
            confirmButton = {
                TextButton(
                    enabled = !busy && dangerToken == token,
                    onClick = { dangerToken = null; onRepay(amount) },
                ) { Text(strings[Key.REPAY_CONFIRM]) }
            },
            dismissButton = { TextButton(onClick = { dangerToken = null }) { Text(strings[Key.COMMON_CANCEL]) } },
        )
    }
}

@Composable
private fun InterestPanel(strings: Strings, busy: Boolean, onFinish: () -> Unit) {
    CenteredPanel {
        MoneyPanel {
            Column {
                Text(strings[Key.INTEREST_TITLE], style = AppType.PanelTitle)
                Spacer(Modifier.height(8.dp))
                Text(strings[Key.INTEREST_BODY], style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                GameButton(
                    strings[Key.INTEREST_CONFIRM],
                    onFinish,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    wide = true,
                    testTag = "tag_finish_interest",
                )
            }
        }
    }
}

@Composable
private fun SkillPanel(strings: Strings, state: GameState, busy: Boolean, onChoose: (Command.MonthlySkillOption) -> Unit) {
    CenteredPanel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(strings[Key.SKILL_TITLE], style = AppType.PanelTitle)
            if (state.monthsWithoutHealth >= 3) {
                Spacer(Modifier.height(8.dp))
                SystemNotice(
                    title = strings[Key.WB_WARNING_LABEL],
                    body = strings.format(Key.SKILL_WARN_NO_HEALTH, state.monthsWithoutHealth),
                    icon = GameIcons.Warning,
                    tone = NoticeTone.DANGER,
                )
            }
            if (state.position.current.index in 210 until GameEngine.WINTER_COLD_DAY) {
                Spacer(Modifier.height(8.dp))
                SystemNotice(
                    title = strings[Key.WB_WARNING_LABEL],
                    body = strings.format(Key.SKILL_WARN_WINTER, state.attributes.health),
                    icon = GameIcons.Health,
                    tone = NoticeTone.INFO,
                )
            }
            Spacer(Modifier.height(12.dp))
            val options = listOf(
                Triple(Command.MonthlySkillOption.HEALTH, GameIcons.Health, strings[Key.SKILL_HEALTH_SHORT]),
                Triple(Command.MonthlySkillOption.DOPAMINE, GameIcons.Dopamine, strings[Key.SKILL_DOPAMINE_SHORT]),
                Triple(Command.MonthlySkillOption.ENGLISH, GameIcons.English, strings[Key.SKILL_ENGLISH_SHORT]),
            )
            // 当前值单独一行小卡，按钮只写“加什么”，不再用长句撑满屏幕
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.InnerGap.dp)) {
                StatTile(
                    GameIcons.Health, strings[Key.ATTR_HEALTH],
                    state.attributes.health.toString(), Modifier.weight(1f),
                )
                StatTile(
                    GameIcons.Dopamine, strings[Key.ATTR_DOPAMINE],
                    state.attributes.dopamine.toString(), Modifier.weight(1f),
                )
                StatTile(
                    GameIcons.English, strings[Key.ATTR_ENGLISH],
                    state.attributes.english.toString(), Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Tokens.BlockGap.dp))
            options.forEach { (skill, icon, label) ->
                GameButton(
                    text = label,
                    onClick = { onChoose(skill) },
                    enabled = !busy,
                    tone = ButtonTone.SKY_SOFT,
                    icon = icon,
                    modifier = Modifier.width(Tokens.ButtonChoiceWidth.dp),
                    testTag = "tag_skill_${skill.name}",
                )
                Spacer(Modifier.height(Tokens.InnerGap.dp))
            }
        }
    }
}

@Composable
private fun RewardPanel(strings: Strings, busy: Boolean, onChoose: (Command.SuccessRewardOption) -> Unit) {
    CenteredPanel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GameCard(Modifier.fillMaxWidth().testTag("tag_reward_panel")) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconPlate(GameIcons.Trophy, container = Tokens.SecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(strings[Key.REWARD_TITLE], style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(Tokens.InnerGap.dp))
                    Text(strings[Key.REWARD_BODY], style = AppType.Prose, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(Tokens.BlockGap.dp))
            // 二选一：按钮收窄，长说明留在上方正文里
            GameButton(
                text = strings[Key.REWARD_WORK_SHORT],
                onClick = { onChoose(Command.SuccessRewardOption.WORK) },
                enabled = !busy,
                icon = GameIcons.Ability,
                modifier = Modifier.width(Tokens.ButtonChoiceWidth.dp),
                testTag = "tag_reward_work",
            )
            Spacer(Modifier.height(Tokens.InnerGap.dp))
            GameButton(
                text = strings[Key.REWARD_TRUST_SHORT],
                onClick = { onChoose(Command.SuccessRewardOption.TRUST) },
                enabled = !busy,
                tone = ButtonTone.TONAL,
                icon = GameIcons.Trust,
                modifier = Modifier.width(Tokens.ButtonChoiceWidth.dp),
                testTag = "tag_reward_trust",
            )
        }
    }
}

@Composable
private fun PendingPanel(strings: Strings, state: GameState) {
    CenteredPanel {
        SystemNotice(
            title = strings[Key.WB_WARNING_LABEL],
            body = state.phase.name,
            icon = GameIcons.Warning,
            tone = NoticeTone.INFO,
        )
    }
}

/** 能量格参考长度：工作能力以 50 为目标，其余属性用 10 格。 */
private const val WORK_REFERENCE = 50
private const val SMALL_REFERENCE = 10
