package com.example.hrrepayment.core.money

import kotlinx.serialization.Serializable
import java.math.BigInteger

/**
 * Money is a non-negative Long of cents (分). 技术文档 §2: “金额使用 Long 分，输入
 * 通过十进制解析；BigDecimal 执行利息/返还并 HALF_UP 到分，拒绝溢出与非法格式。
 * 不得使用 Double 存钱。”
 *
 * This file is the single place where currency rounding happens:
 *  - [parseYuan] is the only accepted path from user text to [Money];
 *  - [timesInt], [applyPerMille] and [applyOddsTenths] are the only multiplication
 *    helpers, and all three round HALF_UP (round half away from zero) at the cent.
 *
 * Overflow policy (review item #4): every entry point either returns
 * `Result.failure` (parsing) or throws [ArithmeticException] (arithmetic). Wrapping
 * around to a small positive number is never allowed.
 */
@Serializable
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    init {
        require(cents >= 0L) { "Money cannot be negative; got $cents" }
    }

    operator fun plus(other: Money): Money = Money(addExact(cents, other.cents))

    operator fun minus(other: Money): Money {
        require(cents >= other.cents) { "Money underflow: $cents - ${other.cents}" }
        return Money(cents - other.cents)
    }

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    override fun toString(): String = "Money(${formatYuan()})"

    /** `123456` → `"1234.56"`. Always two fractional digits, never a sign. */
    fun formatYuan(): String {
        val yuan = cents / 100
        val frac = cents % 100
        // 千分位逗号：1234567 → "12,345.67"（作者要求关于钱的数字用逗号分隔）
        return "%,d.%02d".format(yuan, frac)
    }

    companion object {
        val ZERO = Money(0L)

        /** 92_233_720_368_547_758.07 元 — the largest representable amount. */
        val MAX = Money(Long.MAX_VALUE)

        /** Longest accepted literal: 17 integer digits + '.' + 2 fractional digits. */
        private const val MAX_TEXT_LENGTH = 20

        private val YUAN_PATTERN = Regex("^[0-9]+(\\.[0-9]{1,2})?$")
        private val HUNDRED = BigInteger.valueOf(100L)

        /**
         * Parse a user-entered yuan string into cents.
         *
         * Accepted: `"123"`, `"123.4"`, `"123.45"`, and grouped forms such as
         * `"1,234.56"` — grouping separators are stripped before parsing so that
         * 金额从界面/存档读回时（界面统一显示千分位）仍能安全往返。
         * Rejected: blank input, a leading sign, exponents, and more than two
         * fractional digits. Values that cannot be represented as a non-negative
         * Long of cents (e.g. `"184467440737095516.16"`) fail instead of wrapping.
         */
        fun parseYuan(raw: String): Result<Money> {
            // 去掉千分位逗号：界面显示为 "1,234.56"，读回时要能解析
            val text = raw.trim().replace(",", "")
            if (text.isEmpty()) return Result.failure(IllegalArgumentException("金额不能为空"))
            if (text.length > MAX_TEXT_LENGTH) {
                return Result.failure(ArithmeticException("金额超出可表示上限：$text"))
            }
            if (!YUAN_PATTERN.matches(text)) {
                return Result.failure(IllegalArgumentException("金额格式不合法：$text"))
            }
            return try {
                val parts = text.split('.')
                val yuan = BigInteger(parts[0])
                val frac = parts.getOrNull(1)?.padEnd(2, '0') ?: "00"
                val cents = yuan.multiply(HUNDRED).add(BigInteger(frac))
                Result.success(toCentsExact(cents))
            } catch (e: ArithmeticException) {
                Result.failure(e)
            }
        }

        /**
         * Exact conversion from arbitrary-precision cents. Rejects negatives and
         * anything above [Long.MAX_VALUE]; `longValueExact` also catches wrap-around.
         */
        internal fun toCentsExact(cents: BigInteger): Money {
            if (cents.signum() < 0) {
                throw ArithmeticException("Money cannot be negative; got $cents")
            }
            return try {
                Money(cents.longValueExact())
            } catch (e: ArithmeticException) {
                throw ArithmeticException("金额溢出：$cents 分超出 Long 上限")
            }
        }

        private fun addExact(a: Long, b: Long): Long {
            val sum = a + b
            // Both operands are non-negative, so a negative sum can only be overflow.
            if (sum < 0L) throw ArithmeticException("Money overflow: $a + $b")
            return sum
        }
    }
}

/** Multiply by a non-negative integer (e.g. 3× monthly salary for the annual bonus). */
fun Money.timesInt(multiplier: Int): Money {
    require(multiplier >= 0) { "multiplier must be non-negative, got $multiplier" }
    if (cents == 0L || multiplier == 0) return Money.ZERO
    return Money.toCentsExact(cents.toBigInteger().multiply(multiplier.toBigInteger()))
}

/**
 * Apply a rate expressed in tenths of a percent (per-mille). An 18% rate is 180,
 * a 9% rate is 90. The result is rounded HALF_UP to the nearest cent:
 * `Money(1).applyPerMille(90) == 0` (0.09 分), while `Money(33).applyPerMille(50)`
 * (1.65 分) rounds to 2 分.
 */
fun Money.applyPerMille(ratePerMille: Int): Money {
    require(ratePerMille >= 0) { "rate must be non-negative, got $ratePerMille" }
    if (cents == 0L || ratePerMille == 0) return Money.ZERO
    return roundHalfUp(
        numerator = cents.toBigInteger().multiply(ratePerMille.toBigInteger()),
        denominator = THOUSAND,
    )
}

/**
 * Multiply by an odds multiplier stored in tenths (1.9 → 19) including the
 * principal (赔率含本金). HALF_UP at the cent, same as interest.
 */
fun Money.applyOddsTenths(oddsTenths: Int): Money {
    require(oddsTenths >= 0) { "odds must be non-negative, got $oddsTenths" }
    if (cents == 0L || oddsTenths == 0) return Money.ZERO
    return roundHalfUp(
        numerator = cents.toBigInteger().multiply(oddsTenths.toBigInteger()),
        denominator = BigInteger.TEN,
    )
}

private val THOUSAND = BigInteger.valueOf(1000L)

/**
 * `numerator / denominator` with HALF_UP (round half away from zero). Both inputs
 * are non-negative; an exactly-half remainder rounds up. Overflow is rejected.
 */
private fun roundHalfUp(numerator: BigInteger, denominator: BigInteger): Money {
    val quotient = numerator.divideAndRemainder(denominator)
    val twiceRemainder = quotient[1].shiftLeft(1)
    val rounded = if (twiceRemainder >= denominator) {
        quotient[0].add(BigInteger.ONE)
    } else {
        quotient[0]
    }
    return Money.toCentsExact(rounded)
}
