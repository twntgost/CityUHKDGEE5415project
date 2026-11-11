package com.example.hrrepayment.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression suite for 技术文档 §2. Covers the three defects reported in the P1
 * review: interest used to round UP instead of HALF_UP, and huge inputs used to
 * wrap around into a small positive number that was then accepted.
 */
class MoneyTest {

    // ------------------------------------------------------------------ parsing

    @Test
    fun `parseYuan accepts integer, one-decimal and two-decimal forms`() {
        assertEquals(Money(12300L), Money.parseYuan("123").getOrThrow())
        assertEquals(Money(12340L), Money.parseYuan("123.4").getOrThrow())
        assertEquals(Money(12345L), Money.parseYuan("123.45").getOrThrow())
    }

    @Test
    fun `parseYuan rejects more than two decimals, signs and empty input`() {
        assertTrue(Money.parseYuan("").isFailure)
        assertTrue(Money.parseYuan("   ").isFailure)
        assertTrue(Money.parseYuan("-5").isFailure)
        assertTrue(Money.parseYuan("123.456").isFailure)
        // 千分位是界面显示格式，现在允许读回（见 parseYuan 文档）
        assertEquals(Money(123_400L), Money.parseYuan("1,234").getOrThrow())
        assertTrue(Money.parseYuan("1e5").isFailure)
    }

    @Test
    fun `parseYuan accepts the largest representable amount and rejects one cent more`() {
        assertEquals(Money.MAX, Money.parseYuan("92233720368547758.07").getOrThrow())
        assertTrue(Money.parseYuan("92233720368547758.08").isFailure)
    }

    @Test
    fun `parseYuan rejects values that used to wrap around into a small number`() {
        // 0xFFFFFFFFFFFFFFFF-ish: Long.MAX_VALUE * 2 + 16 cents. The old code read
        // this as a wrapped-around, still non-negative Long and accepted it.
        assertTrue(Money.parseYuan("184467440737095516.16").isFailure)
        assertTrue(Money.parseYuan("18446744073709551615").isFailure)
        assertTrue(Money.parseYuan("99999999999999999999999999").isFailure)
    }

    // --------------------------------------------------------------- arithmetic

    @Test
    fun `arithmetic refuses negative balances and detects overflow`() {
        assertFailsWith<IllegalArgumentException> { Money(-1L) }
        assertFailsWith<IllegalArgumentException> { Money(100L) - Money(101L) }
        assertFailsWith<ArithmeticException> { Money.MAX + Money(1L) }
        assertFailsWith<ArithmeticException> { Money.MAX.timesInt(2) }
    }

    @Test
    fun `timesInt multiplies cents exactly`() {
        assertEquals(Money(18_000_000L), Money(600_000L).timesInt(30))
        assertEquals(Money.ZERO, Money(600_000L).timesInt(0))
    }

    // ----------------------------------------------------------------- interest

    @Test
    fun `applyPerMille rounds HALF_UP, not up`() {
        // 1 分 × 9% = 0.09 分 → 0 分. This is the exact case the review reproduced.
        assertEquals(Money.ZERO, Money(1L).applyPerMille(90))
        // 1 分 × 50% = 0.5 分 → HALF_UP → 1 分.
        assertEquals(Money(1L), Money(1L).applyPerMille(500))
        // 15 分 × 0.1% = 1.5 分 → HALF_UP → 2 分 (exactly-half must round away from zero).
        assertEquals(Money(2L), Money(1500L).applyPerMille(1))
        // 1499/1000 = 1.499 分 → 1 分.
        assertEquals(Money(1L), Money(1499L).applyPerMille(1))
        // 33 分 × 5% = 1.65 分 → 2 分.
        assertEquals(Money(2L), Money(33L).applyPerMille(50))
        // 600,000.00 元 × 9% = 54,000.00 元.
        assertEquals(Money(5_400_000L), Money(60_000_000L).applyPerMille(90))
        // 600,000.00 元 × 18% = 108,000.00 元.
        assertEquals(Money(10_800_000L), Money(60_000_000L).applyPerMille(180))
        assertEquals(Money.ZERO, Money(600_000L).applyPerMille(0))
    }

    @Test
    fun `applyPerMille rejects overflow instead of wrapping`() {
        assertEquals(Money.MAX, Money.MAX.applyPerMille(1000)) // exactly 100%
        assertFailsWith<ArithmeticException> { Money.MAX.applyPerMille(1001) }
    }

    // -------------------------------------------------------------------- odds

    @Test
    fun `applyOddsTenths includes the stake and rounds HALF_UP`() {
        // 5,000 分 × 1.9 = 9,500 分 gross return (principal included).
        assertEquals(Money(950_000L), Money(500_000L).applyOddsTenths(19))
        // 1 分 × 1.5 = 1.5 分 → 2 分.
        assertEquals(Money(2L), Money(1L).applyOddsTenths(15))
        // 1 分 × 1.4 = 1.4 分 → 1 分.
        assertEquals(Money(1L), Money(1L).applyOddsTenths(14))
        assertEquals(Money.ZERO, Money.ZERO.applyOddsTenths(19))
        assertEquals(Money.ZERO, Money(500_000L).applyOddsTenths(0))
    }

    @Test
    fun `applyOddsTenths rejects overflow`() {
        assertFailsWith<ArithmeticException> { Money.MAX.applyOddsTenths(20) }
    }

    // ------------------------------------------------------------------ format

    @Test
    fun `formatYuan always prints two fractional digits`() {
        assertEquals("0.00", Money.ZERO.formatYuan())
        assertEquals("6,000.00", Money(600_000L).formatYuan())
        assertEquals("0.01", Money(1L).formatYuan())
        assertEquals("123,456,789.99", Money(12_345_678_999L).formatYuan())
        assertEquals("92,233,720,368,547,758.07", Money.MAX.formatYuan())
    }
}
