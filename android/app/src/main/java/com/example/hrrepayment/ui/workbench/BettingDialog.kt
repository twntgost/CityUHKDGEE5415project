package com.example.hrrepayment.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens

/**
 * 竞猜弹窗（作者 2026-10-11 定）。
 *
 * 作者要求：**不要把博彩界面放在新闻界面里**，单独做一个弹窗式窗口，
 * **下注或取消之后就消失**。
 *
 * 数值规则与交互一律沿用原实现（[BettingPanel] 一行未改）：滑动到底才提交、
 * 最低 500、1.3 倍递增、月薪三倍上限、快照冻结赔率、用尽现金二次确认。
 * 这里只做外壳：居中圆角卡片 + 顶部标题 + 取消按钮，回调完成后由调用方关闭。
 */
@Composable
fun BettingDialog(
    strings: Strings,
    state: GameState,
    content: ContentRef,
    busy: Boolean,
    onPlaceBet: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(Tokens.CornerPanel.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
                .semantics { this.testTag = "tag_bet_dialog" },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    GameIcons.Bet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = strings[Key.BET_SECTION],
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = strings[Key.BET_OPTIONAL_HINT],
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { this.testTag = "tag_bet_cancel" },
                ) {
                    Text(strings[Key.COMMON_CANCEL], color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(10.dp))
            // 下注成功后由调用方关闭弹窗；取消按钮同理。规则与滑动手势不变。
            BettingPanel(
                strings = strings,
                state = state,
                content = content,
                busy = busy,
                onPlace = { marketId, optionId, amount ->
                    onPlaceBet(marketId, optionId, amount)
                    onDismiss()
                },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
