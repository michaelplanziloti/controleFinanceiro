package com.controlefinanceiro.app.domain

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * PLR (profit sharing) of a CLT employee. It pays no INSS, and its income tax is withheld apart from
 * the salary with its own yearly table: every PLR of the year adds up, so a second one is taxed
 * on the total minus the tax already withheld on the first. The 2026 salary tax reduction does
 * not apply to it. Money in cents.
 */
object Plr {
    /** Yearly PLR table in force in 2026 (upper limit, rate, amount to deduct). */
    private val BANDS = listOf(
        Triple(821_440L, 0.0, 0L),
        Triple(992_228L, 0.075, 61_608L),
        Triple(1_316_700L, 0.15, 136_035L),
        Triple(1_638_038L, 0.225, 234_785L),
        Triple(Long.MAX_VALUE, 0.275, 316_658L),
    )

    /** Tax of the yearly table on [yearCents], all PLR of the year added up. */
    fun yearTax(yearCents: Long): Long {
        val (_, rate, deduct) = BANDS.first { yearCents <= it.first }
        return max(0L, (yearCents * rate).roundToLong() - deduct)
    }

    data class Result(
        val grossCents: Long,
        val taxCents: Long,
        val otherDiscountsCents: Long,
        /** Rate of the band this PLR ends in. */
        val bandRate: Double,
    ) {
        val netCents get() = grossCents - taxCents - otherDiscountsCents
        val effectiveRate get() = if (grossCents > 0) taxCents.toDouble() / grossCents else 0.0
    }

    /** [earlierCents] is the PLR already received earlier in the same year. */
    fun calculate(grossCents: Long, earlierCents: Long = 0, otherDiscountsCents: Long = 0): Result {
        val tax = yearTax(earlierCents + grossCents) - yearTax(earlierCents)
        val rate = BANDS.first { earlierCents + grossCents <= it.first }.second
        return Result(grossCents, tax, otherDiscountsCents, rate)
    }
}
