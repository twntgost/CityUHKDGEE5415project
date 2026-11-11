package com.example.hrrepayment.presentation

import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.data.save.SaveSlot
import com.example.hrrepayment.data.save.SaveSlotSummary

/**
 * A player-facing message kept as a *key + arguments* rather than a rendered string.
 *
 * A banner raised before the language switch must read in the language chosen
 * afterwards — storing Chinese sentences in the UI state made that impossible.
 */
data class Banner(val key: Key, val args: List<Any?> = emptyList())

/** The three panels of the opening menu (design: 开始界面 / 新建游戏 / 读取游戏). */
enum class MenuPanel { START, NEW_GAME, LOAD_GAME }

sealed interface Screen {
    /** Boot: the slots have not been read yet. */
    data object Boot : Screen

    /** The poster menu, with the two slot summaries already read. */
    data class Menu(
        val panel: MenuPanel,
        val slots: List<SaveSlotSummary>,
        val busy: Boolean = false,
    ) : Screen

    /** A loaded run; [state] is the last successfully persisted snapshot. */
    data class Run(
        val slot: SaveSlot,
        val state: GameState,
        val lastSavedAtMillis: Long,
        val busy: Boolean = false,
    ) : Screen
}

/** One collected candidate in the 面试档案袋, resolved from the active content pack. */
data class DossierEntry(
    val candidateId: String,
    val name: String,
    val role: String,
    val avatarAsset: String,
    val workdayIndex: Int,
    /** 已离职：头像转灰 + 红字标注。 */
    val resigned: Boolean = false,
    /** 晋升次数：1 次绿框、2 次金框。 */
    val promotions: Int = 0,
)

/**
 * Whole-app UI state.
 *
 * [dossier] is an overlay rather than a screen: the dossier can be opened from the
 * menu *and* from a running day, and closing it must return to whichever was below.
 */
data class AppUiState(
    val screen: Screen = Screen.Boot,
    /** Non-null while the 面试档案袋 is open. */
    val dossier: List<DossierEntry>? = null,
    /** A slot that could not be used (corrupt / incompatible / IO). */
    val blockProblem: SaveProblem? = null,
    /** Which slot [blockProblem] refers to, so “delete and restart” acts on it. */
    val problemSlot: SaveSlot? = null,
    /** “New Game” tapped on an occupied slot: the overwrite dialog is showing. */
    val pendingOverwrite: SaveSlot? = null,
    val banner: Banner? = null,
) {
    val busy: Boolean
        get() = when (val s = screen) {
            is Screen.Menu -> s.busy
            is Screen.Run -> s.busy
            Screen.Boot -> false
        }

    val isDossierOpen: Boolean get() = dossier != null
}

/**
 * Why a slot cannot be used, and what the player may do about it.
 * [canRetry] is true only for transient problems; [canReset] is true when the file
 * is understood well enough that deleting it is a safe, offered choice.
 */
data class SaveProblem(
    val kind: Kind,
    val titleKey: Key,
    val messageKey: Key,
    val detail: String,
    val canRetry: Boolean,
    val canReset: Boolean,
) {
    enum class Kind { CORRUPTED, INCOMPATIBLE, IO_FAILURE }
}

// --------------------------------------------------------------- state helpers

internal fun AppUiState.asBusy(value: Boolean): AppUiState = when (val s = screen) {
    is Screen.Menu -> copy(screen = s.copy(busy = value))
    is Screen.Run -> copy(screen = s.copy(busy = value))
    Screen.Boot -> this
}

internal fun AppUiState.currentState(): GameState? = (screen as? Screen.Run)?.state

internal fun AppUiState.currentSlot(): SaveSlot? = (screen as? Screen.Run)?.slot
