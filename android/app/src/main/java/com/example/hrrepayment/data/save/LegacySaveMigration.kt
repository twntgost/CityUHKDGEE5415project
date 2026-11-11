package com.example.hrrepayment.data.save

import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.model.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LegacyMarketContract(
    val id: String,
    val options: List<String>,
    val correctOptionId: String?,
    val openWorkdayIndex: Int,
    val settleWorkdayIndex: Int,
)

@Serializable
data class LegacyContentContract(
    val contentHash: String,
    val candidateDays: Map<String, Int>,
    val workdayDates: List<String>,
    val markets: List<LegacyMarketContract>,
)

/**
 * Upgrades an old save in place.
 *
 * Two generations are supported, and **both keep the original file** (the repository
 * writes a `.bak` before the upgraded state is persisted):
 *
 *  - **schema 1 / rules 1.2 / story-v2-full-content** — the pre-prerelease format.
 *    Only accepted when the *content contract* still matches (same workdays, same
 *    candidates, same markets), which is why [LegacyContentContract] ships in the APK.
 *  - **schema 2 / rules 2.0 / story-v3-review-full** — the v2.0 prerelease format.
 *    The 3.0 rule change (25-point review bonus, 90% cap, two fraud categories) only
 *    alters future probability rolls, so every counter, attribute and date carries
 *    over untouched; only the version stamps and the content fingerprint are rebound.
 *
 * A save the migration does not recognise returns `null`, which the repository
 * surfaces as [SaveSnapshot.Incompatible] — never as a silent reset.
 */
class LegacySaveMigration(private val content: ContentRef, bytes: ByteArray) {

    private val contract = Json.decodeFromString<LegacyContentContract>(bytes.decodeToString())

    private val legacyV1Compatible = runCatching {
        require(contract.contentHash.matches(Regex("[0-9a-f]{64}")))
        require(contract.workdayDates.size == 252 && contract.candidateDays.size == 504)
        contract.workdayDates.forEachIndexed { index, date -> require(content.workdayInfo(index).date == date) }
        contract.candidateDays.forEach { (id, day) -> require(content.candidate(id).workdayIndex == day) }
        require(contract.markets.size == content.bettingMarkets().size)
        contract.markets.forEach { old ->
            val current = content.bettingMarkets().single { it.id == old.id }
            require(current.options.map { it.id }.toSet() == old.options.toSet())
            require(current.correctOptionId == old.correctOptionId)
            require(current.openWorkdayIndex == old.openWorkdayIndex && current.settleWorkdayIndex == old.settleWorkdayIndex)
        }
    }.isSuccess

    fun migrate(state: GameState): GameState? = migrateV1(state) ?: migrateV2(state)

    // ------------------------------------------------------------- schema 1 → 3

    private fun migrateV1(state: GameState): GameState? {
        if (!legacyV1Compatible || state.saveSchemaVersion != 1 || state.rulesVersion != "1.2" ||
            state.contentVersion != LEGACY_V1_CONTENT_VERSION || state.contentHash != contract.contentHash
        ) return null
        val valid = runCatching {
            require(state.saveId.matches(Regex("[A-Za-z0-9_-]+")))
            require(state.browsedCandidateIndex in 0..1)
            require(state.stats.globalSuccessCount >= 0 && state.stats.globalFailureCount >= 0)
            require(state.stats.globalSuccessCount + state.stats.globalFailureCount <= state.position.current.index + 1)
            require(state.acceptedBetCount in 0..41)
            require(state.settledBetMarketIds.all { id -> contract.markets.any { it.id == id } })
            state.activeBet?.let { bet ->
                val market = contract.markets.single { it.id == bet.marketId }
                require(bet.optionId in market.options && bet.settleWorkdayIndex == market.settleWorkdayIndex)
                require(bet.amount.cents > 0 && bet.oddsTenths > 0)
            }
        }.isSuccess
        if (!valid) return null
        return rebind(state).copy(
            selectedCandidateId = null,
            markedStatements = emptyMap(),
            useLuckOnSubmission = false,
            promotionSalaryApplied = state.stats.promotionDelta,
            revision = state.revision + 1,
        )
    }

    // ------------------------------------------------------------- schema 2 → 3

    private fun migrateV2(state: GameState): GameState? {
        if (state.saveSchemaVersion != 2 || state.rulesVersion != "2.0") return null
        val valid = runCatching {
            require(state.position.current.index in 0 until content.totalWorkdays)
            state.activeBet?.let { bet -> require(content.bettingMarkets().any { it.id == bet.marketId }) }
            require(state.betOptionOrder.keys.all { id -> content.bettingMarkets().any { it.id == id } })
            require(state.stats.globalSuccessCount >= 0 && state.stats.globalFailureCount >= 0)
        }.isSuccess
        if (!valid) return null
        return rebind(state).copy(
            // The dossier could not exist before v3, so it starts empty rather than
            // inventing placements the player never saw.
            hired = emptyList(),
            lastResultCandidateId = null,
            revision = state.revision + 1,
        )
    }

    /** Re-point the version stamps and the content fingerprint at the current build. */
    private fun rebind(state: GameState): GameState = state.copy(
        saveSchemaVersion = GameState.CURRENT_SCHEMA_VERSION,
        rulesVersion = GameState.EXPECTED_RULES_VERSION,
        contentVersion = content.contentVersion,
        contentHash = content.contentHash,
    )

    companion object {
        const val LEGACY_V1_CONTENT_VERSION = "story-v2-full-content"
    }
}
