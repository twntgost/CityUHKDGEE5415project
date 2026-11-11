package com.example.hrrepayment.data.save

/**
 * The game offers exactly two save slots (design: 新建游戏 → 覆盖存档1/存档2,
 * 读取游戏 → 读取存档1/存档2).
 *
 * Each slot is its own DataStore file, so writing one slot can never corrupt or
 * block the other, and “overwrite slot 2” is a single atomic file write.
 */
enum class SaveSlot(val number: Int, val fileName: String) {
    SLOT_1(1, "save_slot1.json"),
    SLOT_2(2, "save_slot2.json"),
    ;

    companion object {
        val all: List<SaveSlot> = entries.toList()

        /** Slot used when the player taps “New Game” without choosing. */
        val DEFAULT: SaveSlot = SLOT_1

        fun byNumber(number: Int): SaveSlot? = entries.firstOrNull { it.number == number }
    }
}

/**
 * Lightweight summary of a slot, used by the two slot menus.
 *
 * Reading a slot for the menu must not require understanding the whole run, and an
 * unreadable slot must still be *shown* (as disabled) rather than hidden.
 */
data class SaveSlotSummary(
    val slot: SaveSlot,
    val snapshot: SaveSnapshot,
) {
    val isPlayable: Boolean get() = snapshot is SaveSnapshot.Loaded
    val isEmpty: Boolean get() = snapshot is SaveSnapshot.Empty

    /** Player-facing progress line, e.g. “第3月 · 第5个工作日”. */
    val loadedOrNull: SaveSnapshot.Loaded? get() = snapshot as? SaveSnapshot.Loaded
}
