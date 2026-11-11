package com.example.hrrepayment.data.content

import android.content.Context
import com.example.hrrepayment.core.content.BettingContentLoader
import com.example.hrrepayment.core.content.BettingContentRef
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.content.ContentValidationException
import com.example.hrrepayment.core.content.ExportedContentRef
import com.example.hrrepayment.core.content.ExportedMonthLoader
import com.example.hrrepayment.core.content.MonthContent
import com.example.hrrepayment.core.content.NewsContent
import com.example.hrrepayment.core.content.WorkdayContent
import com.example.hrrepayment.core.content.CandidateContent
import com.example.hrrepayment.core.content.BettingMarket
import com.example.hrrepayment.core.i18n.Lang
import kotlinx.serialization.json.Json

/**
 * 从 `assets/content/` 装载运行时内容，按语言选择文件：
 *
 *  - `month1.json` / `month1.en.json`
 *  - `betting.json` / `betting.en.json`
 *  - `manifest.json`（两语言共用的版本、数量与**语言无关**的 contentHash）
 *
 * Android 端只做 JSON 解析 + manifest 对账 + 结构校验 + 头像存在性检查（**不在
 * Android 端引入任何 Excel 依赖**）。全部校验逻辑在 core 的 [ExportedMonthLoader]
 * （纯 Kotlin，JVM 可测），装载失败直接抛出。
 */
object AssetContentLoader {

    private const val MANIFEST_PATH = "content/manifest.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context, lang: Lang = Lang.DEFAULT): ContentRef {
        val manifestBytes = context.assets.open(MANIFEST_PATH).use { it.readBytes() }
        val manifest = json.decodeFromString<ManifestView>(manifestBytes.decodeToString())
        val entry = manifest.files[lang.code]
            ?: throw ContentValidationException("manifest 缺少 ${lang.code} 内容文件")
        val monthBytes = context.assets.open("content/${entry.name}").use { it.readBytes() }
        val base = ExportedMonthLoader.load(
            monthBytes = monthBytes,
            manifestBytes = manifestBytes,
            lang = lang,
            openAsset = { path ->
                context.assets.open(path).use { stream ->
                    if (stream.read() == -1) error("$path 为空文件")
                }
            },
        )
        val bettingPath = if (lang == Lang.EN) "content/betting.en.json" else "content/betting.json"
        val bettingBytes = context.assets.open(bettingPath).use { it.readBytes() }
        return BettingContentRef(base, BettingContentLoader.load(bettingBytes))
    }

    /** Mirrors [com.example.hrrepayment.core.content.ContentManifest] for path lookup. */
    @kotlinx.serialization.Serializable
    private data class ManifestView(
        val files: Map<String, Entry> = emptyMap(),
    ) {
        @kotlinx.serialization.Serializable
        data class Entry(val name: String, val sha256: String = "", val bytes: Long = 0L)
    }
}

/**
 * Content reference whose language can be swapped while the app is running.
 *
 * The engine and every screen keep the same [ContentRef] instance for the whole
 * session; only the delegate behind it changes. That is what makes “Switch Language”
 * work mid-run without rebuilding the object graph or touching the save file:
 * the save stores a language-independent content hash and no rendered sentences.
 */
class SwappableContentRef(initial: ContentRef) : ContentRef {

    @Volatile
    private var delegate: ContentRef = initial

    fun swap(next: ContentRef) {
        delegate = next
    }

    private val current: ContentRef get() = delegate

    override val contentVersion: String get() = current.contentVersion
    override val contentHash: String get() = current.contentHash
    override val openingTitle: String get() = current.openingTitle
    override val openingBody: String get() = current.openingBody
    override val openingAvatarAsset: String get() = current.openingAvatarAsset
    override val totalWorkdays: Int get() = current.totalWorkdays
    override val daysPerMonth: Int get() = current.daysPerMonth
    override fun workdayInfo(index: Int): WorkdayContent = current.workdayInfo(index)
    override fun workdayCandidates(workdayIndex: Int): List<CandidateContent> =
        current.workdayCandidates(workdayIndex)

    override fun candidate(id: String): CandidateContent = current.candidate(id)
    override fun newsForWorkday(index: Int): List<NewsContent> = current.newsForWorkday(index)
    override fun bettingMarkets(): List<BettingMarket> = current.bettingMarkets()
    override fun workdayDisplayDate(index: Int): String = current.workdayDisplayDate(index)

    /** Convenience for the loader; keeps the delegate construction in one place. */
    fun loadAndSwap(context: Context, lang: Lang) {
        swap(AssetContentLoader.load(context, lang))
    }
}
