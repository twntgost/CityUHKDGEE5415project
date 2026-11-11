package com.example.hrrepayment.ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.engine.GameEngine
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.ui.components.SlideToConfirmBar

/**
 * 飞人体育下注面板。
 *
 * 数值规则与 v2.0 完全一致（最低 500、1.3 倍递增、月薪三倍上限、现金上限、
 * 快照冻结赔率、滑动到底才提交、用尽现金二次确认），本轮只改了入口位置与外观：
 * 它现在挂在新页面板底部，默认折叠。
 */
@Composable
fun BettingPanel(
    strings: Strings,
    state: GameState,
    content: ContentRef,
    busy: Boolean,
    onPlace: (String, String, String) -> Unit,
) {
    val market = content.bettingMarkets().firstOrNull {
        it.openWorkdayIndex == state.position.current.index && it.id !in state.settledBetMarketIds
    } ?: return

    var amount by remember(market.id) { mutableStateOf("500") }
    var option by remember(market.id) { mutableStateOf<String?>(null) }
    var dangerToken by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    val parsed = Money.parseYuan(amount).getOrNull()
    val cap = GameEngine.effectiveBetCap(state)
    val valid = parsed != null && parsed.cents >= GameEngine.MIN_BET_CENTS &&
        parsed <= state.cash && parsed <= cap && option != null
    val token = "${state.saveId}:${state.revision}:${market.id}:$option:$amount:$attempt:$busy"
    val order = state.betOptionOrder[market.id] ?: market.options.map { it.id }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 4.dp)
            .testTag("tag_bet_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = market.question,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(strings.format(Key.BET_DATES, content.workdayInfo(market.openWorkdayIndex).date,
            content.workdayInfo(market.settleWorkdayIndex).date),
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("tag_bet_dates"))
        Text(strings[Key.BET_LOCKED_UNTIL], style = MaterialTheme.typography.bodySmall)
        state.betting2017Base?.let { base ->
            Text(strings.format(Key.BET_CAP_ORIGIN, base.formatYuan()),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("tag_bet_cap_origin"))
        }
        Text(strings.format(Key.BET_RULE_CAP, state.bettingCap.formatYuan()),
            style = MaterialTheme.typography.bodySmall)
        Text(
            text = strings.format(Key.BET_MIN_CAP, cap.formatYuan()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = strings[Key.BET_SALARY_RULE],
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val oddsTenths = GameEngine.INITIAL_ODDS_TENTHS + maxOf(state.attributes.luckPeak, state.attributes.luck)
        Text(
            text = strings.format(Key.BET_ODDS, formatOdds(oddsTenths)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        order.mapNotNull { id -> market.options.find { it.id == id } }.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = option == item.id,
                    onClick = { option = item.id },
                    enabled = !busy,
                    modifier = Modifier.testTag("tag_bet_option_${item.id}"),
                )
                Spacer(Modifier.width(4.dp))
                Text(item.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            enabled = !busy,
            label = { Text(strings[Key.BET_AMOUNT_LABEL]) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("tag_bet_amount"),
        )
        if (parsed != null && parsed <= state.cash) {
            Text(
                text = strings.format(
                    Key.BET_AFTER,
                    (state.cash - parsed).formatYuan(),
                    state.debt.formatYuan(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        SlideToConfirmBar(
            text = strings[Key.BET_SLIDE],
            stateToken = token,
            onConfirmRequested = {
                if (parsed == state.cash && (state.debt.cents > 0 || state.specialLoanDue > Money.ZERO)) dangerToken = token
                else option?.let { onPlace(market.id, it, amount) }
            },
            enabled = valid && !busy,
            testTag = "tag_bet_confirm",
        )
    }

    if (dangerToken != null) {
        AlertDialog(
            onDismissRequest = { dangerToken = null; attempt++ },
            title = { Text(strings[Key.BET_DANGER_TITLE]) },
            text = { Text(strings[Key.BET_DANGER_BODY]) },
            confirmButton = {
                TextButton(
                    enabled = !busy && dangerToken == token,
                    onClick = {
                        dangerToken = null
                        option?.let { onPlace(market.id, it, amount) }
                    },
                ) { Text(strings[Key.BET_DANGER_CONFIRM]) }
            },
            dismissButton = {
                TextButton(onClick = { dangerToken = null; attempt++ }) { Text(strings[Key.COMMON_CANCEL]) }
            },
        )
    }
}

/** `19` → `1.9`, `25` → `2.5` (decimal odds kept as tenths in the save). */
internal fun formatOdds(tenths: Int): String = "${tenths / 10}.${tenths % 10}"
