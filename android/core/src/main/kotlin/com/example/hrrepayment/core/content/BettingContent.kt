package com.example.hrrepayment.core.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BetOption(val id: String, val text: String)

@Serializable
data class BettingMarket(
    val id: String,
    val question: String,
    val openWorkdayIndex: Int,
    val settleWorkdayIndex: Int,
    val resultNewsId: String,
    val options: List<BetOption>,
    val correctOptionId: String?,
    val cancelled: Boolean = false,
)

@Serializable
data class BettingBundle(
    val schemaVersion: Int = 1,
    val answerPolicy: String,
    val markets: List<BettingMarket>,
    val news: List<NewsContent>,
    val rulesVersion: String = com.example.hrrepayment.core.model.GameState.EXPECTED_RULES_VERSION,
    val salaryCapMultiplier: Int = 3,
    val luckUsePolicy: String = "opt_in_all_non_forced",
)

object BettingContentLoader {
    fun load(bytes: ByteArray): BettingBundle {
        val bundle = Json.decodeFromString<BettingBundle>(bytes.decodeToString())
        require(bundle.schemaVersion == 1 && bundle.answerPolicy == "sports_news_broadcast_result")
        require(bundle.rulesVersion == com.example.hrrepayment.core.model.GameState.EXPECTED_RULES_VERSION)
        require(bundle.salaryCapMultiplier == 3 && bundle.luckUsePolicy == "opt_in_all_non_forced")
        require(bundle.markets.map { it.id }.distinct().size == bundle.markets.size)
        require(bundle.news.map { it.id }.distinct().size == bundle.news.size)
        val news = bundle.news.associateBy { it.id }
        bundle.markets.forEach { market ->
            require(market.id.isNotBlank() && market.question.isNotBlank())
            require(market.openWorkdayIndex in 0..250 && market.settleWorkdayIndex == market.openWorkdayIndex + 1)
            require(market.options.size == 2 && market.options.map { it.id }.distinct().size == 2)
            require(market.options.all { it.id.isNotBlank() && it.text.isNotBlank() })
            require(if (market.cancelled) market.correctOptionId == null else market.options.any { it.id == market.correctOptionId })
            require(news[market.resultNewsId]?.workdayIndex == market.settleWorkdayIndex)
        }
        return bundle
    }
}

class BettingContentRef(private val base: ContentRef, private val bundle: BettingBundle) : ContentRef by base {
    /**
     * Identity stays language-independent: the betting bundle has a `zh` and an `en`
     * variant, and switching language must never make an existing save incompatible.
     * The canonical hash therefore comes from the manifest, not from the bytes.
     */
    override val contentHash: String = base.contentHash
    override fun bettingMarkets(): List<BettingMarket> = bundle.markets
    override fun newsForWorkday(index: Int): List<NewsContent> = bundle.news.filter { it.workdayIndex == index }.sortedBy { it.order }
}
