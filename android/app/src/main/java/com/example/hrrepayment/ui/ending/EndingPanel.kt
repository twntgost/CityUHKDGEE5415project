package com.example.hrrepayment.ui.ending

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hrrepayment.core.engine.GameEngine
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Story
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.ui.components.ButtonTone
import com.example.hrrepayment.ui.components.GameButton
import com.example.hrrepayment.ui.components.StatTile
import com.example.hrrepayment.ui.components.watermarkSeed
import com.example.hrrepayment.ui.components.watermarks
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens

/**
 * 结局界面：三种排版方向。
 *
 * 作者反馈“感受不到字体的设计感”，并要求结局更**史诗感**、参考豪华品牌中文标识的
 * 做法（细笔画、大字距、零装饰，不要书法体）。三种方案共用同一套字体
 * （[AppType.EpicTitle] 细宋 + 大字距、[AppType.EpicProse] 细宋正文），区别在舞台：
 *
 *  - [DARK_CURTAIN] 落幕：整屏深墨，像影院散场，情绪最重；
 *  - [STELE] 碑铭：纸本 + 细边框，结局名与拉丁副题组成标识式锁定，最“品牌”；
 *  - [POSTER] 卷首：沿用开始菜单的海报色带，首尾呼应。
 *
 * 定稿后改 [EndingStyle.DEFAULT] 一行即可全局切换。
 */
enum class EndingStyle {
    DARK_CURTAIN,
    STELE,
    POSTER,
    ;

    companion object {
        /** 作者 2026-10-11 选定：**碑铭**（细边框 + 双语标识锁定）。 */
        val DEFAULT: EndingStyle = STELE
    }
}

@Composable
fun EndingPanel(
    strings: Strings,
    state: GameState,
    onReturnMenu: () -> Unit,
    style: EndingStyle = EndingStyle.DEFAULT,
    modifier: Modifier = Modifier,
) {
    when (style) {
        EndingStyle.DARK_CURTAIN -> DarkCurtain(strings, state, onReturnMenu, modifier)
        EndingStyle.STELE -> Stele(strings, state, onReturnMenu, modifier)
        EndingStyle.POSTER -> Poster(strings, state, onReturnMenu, modifier)
    }
}

// ------------------------------------------------------------- 共用内容块

/**
 * 结局名的排版：字距是这一版的灵魂，但中文标题长度不一时，固定 5sp 会把
 * “深渊滚雪球·赌徒末路”顶成两行。所以按字数自适应——短标题摊得更开，
 * 长标题收一点，保证永远是一行。
 */
private fun titleStyle(title: String): androidx.compose.ui.text.TextStyle = when {
    title.length <= 6 -> AppType.EpicTitle.copy(fontSize = 44.sp, lineHeight = 56.sp, letterSpacing = 8.sp)
    title.length <= 9 -> AppType.EpicTitle.copy(fontSize = 35.sp, lineHeight = 46.sp, letterSpacing = 4.sp)
    else -> AppType.EpicTitle.copy(fontSize = 29.sp, lineHeight = 40.sp, letterSpacing = 2.sp)
}

private fun proseOf(strings: Strings, state: GameState): String =
    if (state.endingReason == "special_loan_overdue") strings[Key.LOAN_OVERDUE]
    else Story.endingBody(strings.lang, state.endingId, state.endingReason)

private fun prefixOf(strings: Strings, state: GameState): Pair<String, String>? =
    when (state.pendingEventId) {
        GameEngine.EVENT_OVERSEAS_OFFER ->
            Story.eventTitle(strings.lang, GameEngine.EVENT_OVERSEAS_OFFER) to
                Story.eventBody(strings.lang, GameEngine.EVENT_OVERSEAS_OFFER)
        GameEngine.EVENT_WINTER_COLD ->
            Story.eventTitle(strings.lang, GameEngine.EVENT_WINTER_COLD) to
                Story.eventBody(strings.lang, GameEngine.EVENT_WINTER_COLD)
        else -> null
    }

@Composable
private fun StatRow(state: GameState, strings: Strings, onDark: Boolean) {
    val label = if (onDark) Tokens.InverseOnSurface.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant
    val value = if (onDark) Tokens.InverseOnSurface else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf(
            strings[Key.ATTR_CASH] to state.cash.formatYuan(),
            strings[Key.ATTR_DEBT] to state.debt.formatYuan(),
            strings[Key.WB_TOTAL_HIRES] to state.stats.globalSuccessCount.toString(),
        ).forEach { (k, v) ->
            Column {
                Text(k, style = AppType.EpicKicker.copy(letterSpacing = 3.sp), color = label, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(v, style = AppType.EpicTitle.copy(fontSize = 20.sp, letterSpacing = 0.5.sp), color = value)
            }
        }
    }
}

// ---------------------------------------------------------------- 方案一 落幕

@Composable
private fun DarkCurtain(strings: Strings, state: GameState, onReturnMenu: () -> Unit, modifier: Modifier) {
    val accent = Tokens.endingAccent(state.endingId)
    Box(
        modifier
            .fillMaxSize()
            // 纯色底（作者要求不要渐变）+ 深色版暗纹
            .background(Color(0xFF211A12))
            .watermarks(watermarkSeed(301), onDark = true),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings[Key.ENDING_KICKER],
                style = AppType.EpicKicker,
                color = Tokens.InverseOnSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(18.dp))
            val titleDark = Story.endingTitle(strings.lang, state.endingId)
            Text(
                text = titleDark,
                style = titleStyle(titleDark),
                color = Tokens.InverseOnSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(14.dp))
            // 细金线（沿用天蓝点缀，但在深底上提亮）
            Box(
                Modifier
                    .width(56.dp)
                    .height(2.dp)
                    .background(accent.copy(alpha = 0.9f)),
            )
            Spacer(Modifier.height(28.dp))
            prefixOf(strings, state)?.let { (title, body) ->
                Text(
                    text = title,
                    style = AppType.EpicKicker,
                    color = accent,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = AppType.EpicProse.copy(color = Tokens.InverseOnSurface.copy(alpha = 0.82f)),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
            }
            Text(
                text = proseOf(strings, state),
                style = AppType.EpicProse,
                color = Tokens.InverseOnSurface.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 330.dp),
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = strings[Key.ENDING_GAME_OVER],
                style = AppType.EpicTitle.copy(fontSize = 20.sp, letterSpacing = 8.sp),
                color = Tokens.InverseOnSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(30.dp))
            StatRow(state, strings, onDark = true)
            Spacer(Modifier.height(28.dp))
            GameButton(
                text = strings[Key.ENDING_RETURN_HOME],
                onClick = onReturnMenu,
                tone = ButtonTone.OUTLINED,
                icon = GameIcons.Dossier,
                modifier = Modifier.width(Tokens.ButtonWidth.dp),
                testTag = "tag_return_home",
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------- 方案二 碑铭

@Composable
private fun Stele(strings: Strings, state: GameState, onReturnMenu: () -> Unit, modifier: Modifier) {
    val accent = Tokens.endingAccent(state.endingId)
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .watermarks(watermarkSeed(302)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.PageMargin.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 标识式锁定：细边框 + 大字距结局名 + 拉丁副题
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = strings[Key.ENDING_KICKER],
                    style = AppType.EpicKicker,
                    color = accent,
                )
                Spacer(Modifier.height(16.dp))
                val titleStele = Story.endingTitle(strings.lang, state.endingId)
                Text(
                    text = titleStele,
                    style = titleStyle(titleStele),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(40.dp).height(1.dp).background(accent.copy(alpha = 0.5f)))
                Spacer(Modifier.height(12.dp))
                // 拉丁副题：与中文名形成双语标识（英文界面下即结局名本身）
                Text(
                    text = Story.endingTitle(
                        if (strings.lang == com.example.hrrepayment.core.i18n.Lang.ZH) {
                            com.example.hrrepayment.core.i18n.Lang.EN
                        } else {
                            com.example.hrrepayment.core.i18n.Lang.ZH
                        },
                        state.endingId,
                    ),
                    style = AppType.EpicKicker.copy(fontSize = 12.sp, letterSpacing = 3.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(26.dp))
            prefixOf(strings, state)?.let { (title, body) ->
                Text(title, style = AppType.EpicKicker, color = accent)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = AppType.EpicProse,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(22.dp))
            }
            Text(
                text = proseOf(strings, state),
                style = AppType.EpicProse,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = strings[Key.ENDING_GAME_OVER],
                style = AppType.EpicTitle.copy(fontSize = 18.sp, letterSpacing = 8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            StatRow(state, strings, onDark = false)
            Spacer(Modifier.height(28.dp))
            GameButton(
                text = strings[Key.ENDING_RETURN_HOME],
                onClick = onReturnMenu,
                tone = ButtonTone.FILLED,
                icon = GameIcons.Dossier,
                modifier = Modifier.width(Tokens.ButtonWidth.dp),
                testTag = "tag_return_home",
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------- 方案三 卷首

@Composable
private fun Poster(strings: Strings, state: GameState, onReturnMenu: () -> Unit, modifier: Modifier) {
    val accent = Tokens.endingAccent(state.endingId)
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .watermarks(watermarkSeed(303)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            // 海报色带：与开始菜单同款，首尾呼应
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = Tokens.PageMargin.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(strings[Key.ENDING_KICKER], style = AppType.EpicKicker, color = accent)
                Spacer(Modifier.height(14.dp))
                val titlePoster = Story.endingTitle(strings.lang, state.endingId)
                Text(
                    text = titlePoster,
                    style = titleStyle(titlePoster),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = Story.endingTitle(
                        if (strings.lang == com.example.hrrepayment.core.i18n.Lang.ZH) {
                            com.example.hrrepayment.core.i18n.Lang.EN
                        } else {
                            com.example.hrrepayment.core.i18n.Lang.ZH
                        },
                        state.endingId,
                    ),
                    style = AppType.EpicKicker.copy(fontSize = 12.sp, letterSpacing = 3.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.PageMargin.dp, vertical = 22.dp),
            ) {
                prefixOf(strings, state)?.let { (title, body) ->
                    Text(title, style = AppType.EpicKicker, color = accent)
                    Spacer(Modifier.height(8.dp))
                    Text(body, style = AppType.EpicProse, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(22.dp))
                }
                Text(
                    text = proseOf(strings, state),
                    style = AppType.EpicProse,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = strings[Key.ENDING_GAME_OVER],
                    style = AppType.EpicTitle.copy(fontSize = 18.sp, letterSpacing = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                StatRow(state, strings, onDark = false)
                Spacer(Modifier.height(26.dp))
                GameButton(
                    text = strings[Key.ENDING_RETURN_HOME],
                    onClick = onReturnMenu,
                    tone = ButtonTone.TONAL,
                    icon = GameIcons.Dossier,
                    modifier = Modifier.fillMaxWidth(),
                    wide = true,
                    testTag = "tag_return_home",
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
