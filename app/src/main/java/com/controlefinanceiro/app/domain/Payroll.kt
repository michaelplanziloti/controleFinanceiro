package com.controlefinanceiro.app.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Payroll taxes of a CLT employee in 2026. Money in cents. */
object Payroll {
    /** INSS 2026 (Portaria Interministerial MPS/MF 13/2026): each rate applies to its band, up to the ceiling. */
    private val INSS_BANDS = listOf(162_100L to 0.075, 290_284L to 0.09, 435_427L to 0.12, 847_555L to 0.14)

    /** IRRF monthly table (upper limit of the band, rate, amount to deduct). */
    private val IRRF_BANDS = listOf(
        Triple(242_880L, 0.0, 0L),
        Triple(282_665L, 0.075, 18_216L),
        Triple(375_105L, 0.15, 39_416L),
        Triple(466_468L, 0.225, 67_549L),
        Triple(Long.MAX_VALUE, 0.275, 90_873L),
    )

    /** Deduction per dependent. */
    const val DEPENDENT_CENTS = 18_959L

    /** Simplified discount, used instead of INSS and dependents when it lowers the tax more. */
    const val SIMPLIFIED_CENTS = 60_720L

    fun inss(baseCents: Long): Long {
        var previous = 0L
        var total = 0.0
        for ((limit, rate) in INSS_BANDS) {
            if (baseCents <= previous) break
            total += (min(baseCents, limit) - previous) * rate
            previous = limit
        }
        return total.roundToLong()
    }

    /** Tax of the progressive table alone, on a base already net of deductions. */
    private fun table(baseCents: Long): Long {
        val (_, rate, deduct) = IRRF_BANDS.first { baseCents <= it.first }
        return max(0L, (baseCents * rate).roundToLong() - deduct)
    }

    /**
     * IRRF on [grossCents] of taxable income, with the 2026 reduction (Lei 15.270/2025): no tax up
     * to R$ 5.000, a reduction that shrinks up to R$ 7.350, none above. The legal deductions
     * (INSS and dependents) or the simplified discount are used, whichever leaves less tax.
     */
    fun irrf(grossCents: Long, inssCents: Long, dependents: Int = 0): Long {
        val legal = grossCents - inssCents - dependents * DEPENDENT_CENTS
        val simplified = grossCents - SIMPLIFIED_CENTS
        val tax = table(max(0L, min(legal, simplified)))
        val reduction = when {
            grossCents <= 500_000 -> 31_289L
            grossCents <= 735_000 -> max(0L, (97_862 - 0.133145 * grossCents).roundToLong())
            else -> 0L
        }
        return tax - min(tax, reduction)
    }
}
