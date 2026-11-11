package com.example.hrrepayment.core.content

import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.WorkdayRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * One language variant of the exported runtime bundle.
 *
 * The content pack is split per language (`month1.json` / `month1.en.json`) instead
 * of carrying both languages inline: the files stay human-diffable, the Android
 * loader only parses the language actually in use, and the *identity* of the pack
 * is a single language-independent hash ([ContentManifest.contentHash]) so switching
 * the language can never invalidate a save.
 */
@Serializable
data class ContentFileEntry(
    val name: String,
    val sha256: String,
    val bytes: Long = 0L,
)

/**
 * 导出内容的 manifest（`assets/content/manifest.json`，由创作仓库导出脚本生成）。
 * 只建模需要校验的字段，其余忽略。
 */
@Serializable
data class ContentManifest(
    val contentVersion: String,
    val rulesVersion: String,
    /** Language-independent identity of the bundle; bound into every save. */
    val contentHash: String,
    /** language code → file entry ("zh" / "en"). */
    val files: Map<String, ContentFileEntry> = emptyMap(),
    val monthIndex: Int = 0,
    val workdayCount: Int,
    val candidateCount: Int,
) {
    fun fileFor(lang: Lang): ContentFileEntry? = files[lang.code]
}

/** 内容校验失败：装载即抛，绝不带着坏内容运行（技术文档 P2 合同）。 */
class ContentValidationException(message: String) : IllegalStateException(message)

/**
 * 导出月份的结构校验（纯函数，JVM 可测）。
 *
 * 规则：
 *  - 恰好 [expectedWorkdayCount] 个工作日，索引 0..N-1 且无重复；
 *  - 每个工作日恰好 2 名候选人；
 *  - candidateIds 外键与 candidate.workdayIndex 双向一致（每个候选人恰好被引用一次）；
 *  - 候选人 id 唯一；
 *  - gender 只能 male/female，genderLabel 与之一致；
 *  - successBps 在 0..10000；
 *  - 姓名/简历号/头像路径非空（存在性由装载器用 assets 校验）；
 *  - 造假类型只能是两类之一（背景造假 / 项目造假及其英文对应）。
 */
object MonthContentValidator {

    /** The two fraud categories the player can reason about (v3 rule). */
    val FRAUD_TYPES_ZH = setOf("背景造假", "项目造假")
    val FRAUD_TYPES_EN = setOf("Background fraud", "Project fraud")

    fun isValidFraudType(value: String): Boolean =
        value in FRAUD_TYPES_ZH || value in FRAUD_TYPES_EN

    fun validate(month: MonthContent, expectedWorkdayCount: Int = WorkdayRef.TOTAL_WORKDAYS) {
        val prefix = "内容校验失败："

        require(month.workdays.size == expectedWorkdayCount) {
            "$prefix 工作日数量应为 $expectedWorkdayCount，实际 ${month.workdays.size}"
        }
        val indices = month.workdays.map { it.index }
        require(indices.toSet().size == indices.size) {
            "$prefix 工作日索引重复：$indices"
        }
        require(indices.toSet() == (0 until expectedWorkdayCount).toSet()) {
            "$prefix 工作日索引应为 0..${expectedWorkdayCount - 1}，实际 $indices"
        }

        val byId = month.candidates.associateBy { it.id }
        require(month.candidates.size == expectedWorkdayCount * 2) {
            "$prefix 内容应有 ${expectedWorkdayCount * 2} 份简历，实际 ${month.candidates.size}"
        }
        require(byId.size == month.candidates.size) {
            "$prefix 候选人 id 重复：${month.candidates.groupBy { it.id }.filterValues { it.size > 1 }.keys}"
        }

        val referenced = mutableSetOf<String>()
        for (day in month.workdays) {
            require(day.demand?.let { it.skill.isNotBlank() && it.experience.isNotBlank() && it.task.isNotBlank() } == true) {
                "$prefix 工作日 ${day.index} 缺少岗位需求"
            }
            require(day.candidateIds.size == 2) {
                "$prefix 工作日 ${day.index} 应恰好 2 名候选人，实际 ${day.candidateIds.size}"
            }
            for (id in day.candidateIds) {
                require(id !in referenced) { "$prefix 候选人 $id 被多个工作日引用" }
                referenced += id
                val candidate = byId[id]
                    ?: throw IllegalArgumentException("$prefix 工作日 ${day.index} 引用了未知候选人 $id")
                require(candidate.workdayIndex == day.index) {
                    "$prefix 候选人 $id 的 workdayIndex=${candidate.workdayIndex} " +
                        "与其所在工作日 ${day.index} 不一致"
                }
            }
        }
        val orphans = month.candidates.map { it.id } - referenced
        require(orphans.isEmpty()) { "$prefix 未被任何工作日引用的候选人：$orphans" }

        for (candidate in month.candidates) {
            require(candidate.gender == "male" || candidate.gender == "female") {
                "$prefix 候选人 ${candidate.id} 的性别只能是 male/female，实际 '${candidate.gender}'"
            }
            val expectedLabels = if (candidate.gender == "male") setOf("男", "Male") else setOf("女", "Female")
            require(candidate.genderLabel in expectedLabels) {
                "$prefix 候选人 ${candidate.id} 的 genderLabel='${candidate.genderLabel}' " +
                    "与性别 ${candidate.gender} 不一致"
            }
            require(candidate.successBps in 0..10_000) {
                "$prefix 候选人 ${candidate.id} 的 successBps 应在 0..10000，实际 ${candidate.successBps}"
            }
            require(candidate.name.isNotBlank()) { "$prefix 候选人 ${candidate.id} 姓名为空" }
            require(candidate.resumeNo.isNotBlank()) { "$prefix 候选人 ${candidate.id} 简历号为空" }
            require(candidate.avatarAsset.isNotBlank()) { "$prefix 候选人 ${candidate.id} 头像路径为空" }
            require(isValidFraudType(candidate.fraudType)) {
                "$prefix 候选人 ${candidate.id} 的造假类型只能是背景造假/项目造假，实际 '${candidate.fraudType}'"
            }
            val statements = candidate.statements
            require(statements.size in 5..7 && statements.map { it.id }.distinct().size == statements.size) {
                "$prefix 候选人 ${candidate.id} 的陈述数量或ID异常"
            }
            require(statements.all { it.id.isNotBlank() && it.text.isNotBlank() && it.field in setOf("educationSkills", "workExperience") })
            require(statements.singleOrNull { it.id == candidate.lieStatementId }?.text == candidate.uniqueLie) {
                "$prefix 候选人 ${candidate.id} 的唯一假话与陈述映射不一致"
            }
            val evidenceIds = candidate.evidence.map { it.id }
            require(candidate.evidence.size in 2..3 && evidenceIds.distinct().size == evidenceIds.size)
            require(candidate.evidence.all { it.id.isNotBlank() && it.title.isNotBlank() && it.body.isNotBlank() })
            require(candidate.supportingEvidenceIds.isNotEmpty() && candidate.supportingEvidenceIds.all { it in evidenceIds })
        }

        require(month.openingAvatarAsset.isNotBlank()) { "$prefix 开场头像路径为空" }
    }
}

/**
 * 导出月份装载：解析 + manifest 对账 + 结构校验 + 头像存在性（经 [openAsset]）。
 *
 * 纯 Kotlin（无 Android 依赖），app 层的 `AssetContentLoader` 只是薄封装；
 * 因此全部校验可在 JVM 单元测试中真实执行。任何一项失败都抛
 * [ContentValidationException]——装载失败宁可起不来，也不能带着坏内容运行。
 */
object ExportedMonthLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(
        monthBytes: ByteArray,
        manifestBytes: ByteArray,
        lang: Lang = Lang.ZH,
        openAsset: (path: String) -> Unit,
        expectedWorkdayCount: Int = WorkdayRef.TOTAL_WORKDAYS,
    ): ExportedContentRef {
        val month = try {
            json.decodeFromString(MonthContent.serializer(), monthBytes.decodeToString())
        } catch (e: SerializationException) {
            throw ContentValidationException("month1.json 解析失败：${e.message}")
        } catch (e: IllegalArgumentException) {
            throw ContentValidationException("month1.json 内容不合法：${e.message}")
        }
        val manifest = try {
            json.decodeFromString(ContentManifest.serializer(), manifestBytes.decodeToString())
        } catch (e: SerializationException) {
            throw ContentValidationException("manifest.json 解析失败：${e.message}")
        } catch (e: IllegalArgumentException) {
            throw ContentValidationException("manifest.json 内容不合法：${e.message}")
        }

        val sha = sha256Hex(monthBytes)
        val entry = manifest.fileFor(lang)
            ?: throw ContentValidationException("manifest 缺少 ${lang.code} 语言的内容文件条目")

        ensure(manifest.contentVersion == month.contentVersion, "manifest.contentVersion=${manifest.contentVersion} 与 month1.json 的 ${month.contentVersion} 不一致")
        ensure(month.contentVersion == GameState.EXPECTED_CONTENT_VERSION, "contentVersion=${month.contentVersion} 与构建期望 ${GameState.EXPECTED_CONTENT_VERSION} 不一致")
        ensure(manifest.rulesVersion == month.rulesVersion, "manifest.rulesVersion=${manifest.rulesVersion} 与 month1.json 的 ${month.rulesVersion} 不一致")
        ensure(month.rulesVersion == GameState.EXPECTED_RULES_VERSION, "rulesVersion=${month.rulesVersion} 与构建期望 ${GameState.EXPECTED_RULES_VERSION} 不一致")
        ensure(month.lang == lang.code, "内容文件语言 ${month.lang} 与请求的 ${lang.code} 不一致")
        ensure(entry.sha256 == sha, "${entry.name} 的 sha256=${entry.sha256} 与实际 $sha 不一致")
        if (entry.bytes != 0L) ensure(entry.bytes == monthBytes.size.toLong(), "${entry.name} 的 bytes=${entry.bytes} 与实际 ${monthBytes.size} 不一致")
        ensure(manifest.workdayCount == expectedWorkdayCount, "manifest.workdayCount=${manifest.workdayCount} 应为 $expectedWorkdayCount")
        ensure(manifest.candidateCount == month.candidates.size, "manifest.candidateCount=${manifest.candidateCount} 与实际 ${month.candidates.size} 不一致")
        ensure(manifest.contentHash.length == 64, "manifest.contentHash 必须是 sha256 十六进制串")

        try {
            MonthContentValidator.validate(month, expectedWorkdayCount)
        } catch (e: IllegalArgumentException) {
            throw ContentValidationException(e.message ?: "结构校验失败")
        }

        // 头像存在性：manifest 的 sha256 只覆盖内容 JSON，引用的头像是独立文件
        val avatarPaths = (month.candidates.map { it.avatarAsset } + month.openingAvatarAsset).distinct()
        for (path in avatarPaths) {
            try {
                openAsset(path)
            } catch (e: Exception) {
                throw ContentValidationException("头像资源 $path 无法打开：${e.message}")
            }
        }

        return ExportedContentRef(month, contentHash = manifest.contentHash)
    }

    private fun fail(detail: String): String = "manifest 对账失败：$detail"

    private fun ensure(condition: Boolean, detail: String) {
        if (!condition) throw ContentValidationException(fail(detail))
    }

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
