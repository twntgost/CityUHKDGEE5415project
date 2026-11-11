package com.example.hrrepayment.data.save

import com.example.hrrepayment.core.content.*
import com.example.hrrepayment.core.model.*
import com.example.hrrepayment.core.money.Money
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Assert.*
import java.io.File

class LegacySaveMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val assets = File("../app/src/main/assets")
    private val bytes = File(assets, "content/legacy-v1.json").readBytes()
    private val contract = Json.decodeFromString<LegacyContentContract>(bytes.decodeToString())
    private val content: ContentRef by lazy {
        val base = ExportedMonthLoader.load(
            monthBytes = File(assets, "content/month1.json").readBytes(),
            manifestBytes = File(assets, "content/manifest.json").readBytes(),
            lang = com.example.hrrepayment.core.i18n.Lang.ZH,
            openAsset = { require(File(assets, it).isFile) },
        )
        val betting = File(assets, "content/betting.json").readBytes()
        BettingContentRef(base, BettingContentLoader.load(betting))
    }
    private fun legacy() = GameState.newGame("legacy", 2026).copy(
        saveSchemaVersion = 1, rulesVersion = "1.2", contentVersion = "story-v2-full-content",
        contentHash = contract.contentHash, revision = 9, phase = Phase.Recruiting,
        position = CalendarPosition(WorkdayRef(2)), cash = Money(780_000), debt = Money(59_000_000),
        stats = Stats(globalSuccessCount = 3), attributes = Attributes(work = 2, luck = 3, luckPeak = 7),
        browsedCandidateIndex = 1, selectedCandidateId = "obsolete",
        activeBet = BetSnapshot(contract.markets.first().id, contract.markets.first().options.first(),
            Money(50_000), 7, 26, Money(500_000), contract.markets.first().settleWorkdayIndex), acceptedBetCount = 1)

    @Test fun knownRuntimeMappingsPermitMigrationWithoutChangingAccountsResultsOrBetTerms() {
        val migration = LegacySaveMigration(content, bytes)
        for (phase in listOf(Phase.Recruiting, Phase.ResultAck, Phase.SuccessReward,
            Phase.MonthIncome, Phase.Repayment, Phase.InterestCheck, Phase.MonthlySkill, Phase.EventNotice, Phase.Ended)) {
            val old = legacy().copy(phase = phase, lastResultSuccess = true,
                fixedEventsSeen = setOf("chapter2_success"), endingId = if (phase == Phase.Ended) "bankruptcy" else null)
            val upgraded = migration.migrate(old)!!
            assertEquals(old.cash, upgraded.cash)
            assertEquals(old.debt, upgraded.debt)
            assertEquals(old.attributes, upgraded.attributes)
            assertEquals(old.stats, upgraded.stats)
            assertEquals(old.phase, upgraded.phase)
            // v3 起状态里不再保存句子，只保存结构化结果，语言切换才不会锁死文本。
            assertEquals(old.lastResultSuccess, upgraded.lastResultSuccess)
            assertEquals(old.fixedEventsSeen, upgraded.fixedEventsSeen)
            assertEquals(old.activeBet, upgraded.activeBet)
            assertEquals(old.position, upgraded.position)
            assertEquals(old.monthlySalary, upgraded.monthlySalary)
            assertEquals(old.nextMonthlySalary, upgraded.nextMonthlySalary)
            assertEquals(old.endingId, upgraded.endingId)
            assertEquals(10L, upgraded.revision)
            assertEquals(content.contentHash, upgraded.contentHash)
            assertEquals(GameState.CURRENT_SCHEMA_VERSION, upgraded.saveSchemaVersion)
            assertEquals(GameState.EXPECTED_RULES_VERSION, upgraded.rulesVersion)
            assertNull(upgraded.selectedCandidateId)
            assertTrue(upgraded.markedStatements.isEmpty())
            assertNull(migration.migrate(upgraded))
        }
    }

    @Test fun unknownVersionsHashesAndChangedAnswerMappingsCannotMigrate() {
        val migration = LegacySaveMigration(content, bytes)
        for (old in listOf(legacy().copy(saveSchemaVersion = 99), legacy().copy(rulesVersion = "1.1"),
            legacy().copy(contentHash = "unknown"), legacy().copy(acceptedBetCount = 42))) assertNull(migration.migrate(old))
        val wrong = contract.copy(markets = contract.markets.mapIndexed { index, market ->
            if (index == 0) market.copy(correctOptionId = market.options.first()) else market })
        assertNull(LegacySaveMigration(content, Json.encodeToString(wrong).encodeToByteArray()).migrate(legacy()))
    }

    @Test fun migrationWritesOneOriginalBackupAndIsIdempotentAfterRepositoryRestart() = runBlocking {
        val file = File(temporary.root, "save.json")
        val old = legacy()
        val original = Json.encodeToString(SaveEnvelope(state = old, revision = old.revision))
        file.writeText(original)
        val migration = LegacySaveMigration(content, bytes)
        val expectations = SaveExpectations.current(content.contentHash)
        val first = SaveRepository.create(file, expectations, migration = migration::migrate)
        val loaded = try { first.read() as SaveSnapshot.Loaded } finally { first.close() }
        val backup = File(temporary.root, "save-v1-legacy.json.bak")
        assertEquals(original, backup.readText())
        val second = SaveRepository.create(file, expectations, migration = migration::migrate)
        val restored = try { second.read() as SaveSnapshot.Loaded } finally { second.close() }
        assertEquals(loaded.state, restored.state)
        assertEquals(1, temporary.root.listFiles()!!.count { it.extension == "bak" })
        assertFalse(temporary.root.listFiles()!!.any { it.extension == "tmp" })
    }
}
