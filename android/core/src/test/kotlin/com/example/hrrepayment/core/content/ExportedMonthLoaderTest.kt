package com.example.hrrepayment.core.content

import com.example.hrrepayment.core.engine.TestContent
import com.example.hrrepayment.core.i18n.Lang
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExportedMonthLoaderTest {
    private val json = Json { encodeDefaults = true }

    private fun load(
        month: MonthContent = TestContent.month(),
        changeManifest: (ContentManifest) -> ContentManifest = { it },
        openAsset: (String) -> Unit = {},
    ): ExportedContentRef {
        val bytes = json.encodeToString(month).encodeToByteArray()
        val sha = ExportedMonthLoader.sha256Hex(bytes)
        val manifest = changeManifest(ContentManifest(
            contentVersion = month.contentVersion,
            rulesVersion = month.rulesVersion,
            contentHash = sha,
            files = mapOf("zh" to ContentFileEntry(name = "month1.json", sha256 = sha, bytes = bytes.size.toLong())),
            workdayCount = 6,
            candidateCount = month.candidates.size,
        ))
        return ExportedMonthLoader.load(
            monthBytes = bytes,
            manifestBytes = json.encodeToString(manifest).encodeToByteArray(),
            lang = Lang.ZH,
            openAsset = openAsset,
            expectedWorkdayCount = 6,
        )
    }

    @Test fun validContentLoadsAndChecksDistinctAvatars() {
        val opened = mutableListOf<String>()
        val content = load(openAsset = { opened += it })
        assertEquals(64, content.contentHash.length)
        assertEquals(listOf("avatar/avatar_01.png"), opened)
        assertEquals(2, content.workdayCandidates(5).size)
    }

    @Test fun rejectsHashMismatch() {
        assertFailsWith<ContentValidationException> {
            load(changeManifest = { it.copy(files = mapOf("zh" to it.files.getValue("zh").copy(sha256 = "wrong"))) })
        }
    }

    @Test fun rejectsByteMismatch() {
        assertFailsWith<ContentValidationException> {
            load(changeManifest = { it.copy(files = mapOf("zh" to it.files.getValue("zh").copy(bytes = 1))) })
        }
    }

    @Test fun rejectsMissingLanguageFile() {
        assertFailsWith<ContentValidationException> { load(changeManifest = { it.copy(files = emptyMap()) }) }
    }

    @Test fun rejectsVersions() {
        assertFailsWith<ContentValidationException> { load(TestContent.month().copy(contentVersion = "old")) }
        assertFailsWith<ContentValidationException> { load(TestContent.month().copy(rulesVersion = "old")) }
        assertFailsWith<ContentValidationException> { load(changeManifest = { it.copy(contentVersion = "old") }) }
    }

    @Test fun rejectsWorkdayCountAndIndices() {
        val month = TestContent.month()
        assertFailsWith<ContentValidationException> { load(month.copy(workdays = month.workdays.dropLast(1))) }
        for (index in listOf(0, 6)) {
            assertFailsWith<ContentValidationException> {
                load(month.copy(workdays = month.workdays.dropLast(1) + month.workdays.last().copy(index = index)))
            }
        }
    }

    @Test fun rejectsCandidateCardinalityAndForeignKeys() {
        val month = TestContent.month()
        for (ids in listOf(listOf("missing", month.candidates[1].id), listOf(month.candidates[0].id))) {
            assertFailsWith<ContentValidationException> {
                load(month.copy(workdays = listOf(month.workdays.first().copy(candidateIds = ids)) + month.workdays.drop(1)))
            }
        }
    }

    @Test fun rejectsInvalidCandidateFields() {
        val month = TestContent.month()
        val first = month.candidates.first()
        for (invalid in listOf(
            first.copy(id = month.candidates[1].id),
            first.copy(workdayIndex = 1),
            first.copy(gender = "unknown"),
            first.copy(genderLabel = "wrong"),
            first.copy(successBps = -1),
            first.copy(successBps = 10001),
            first.copy(avatarAsset = ""),
        )) {
            assertFailsWith<ContentValidationException> {
                load(month.copy(candidates = listOf(invalid) + month.candidates.drop(1)))
            }
        }
    }

    @Test fun rejectsMissingAvatar() {
        assertFailsWith<ContentValidationException> {
            load(openAsset = { throw java.io.IOException("missing") })
        }
    }
}
