package com.controlefinanceiro.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlrTest {
    @Test
    fun exemptUpTheFirstBand() {
        assertEquals(0L, Plr.calculate(821_440).taxCents)
    }

    @Test
    fun taxOfOnePlr() {
        // R$ 20.000 x 27,5% - 3.166,58 = 2.333,42
        val r = Plr.calculate(2_000_000)
        assertEquals(233_342L, r.taxCents)
        assertEquals(1_766_658L, r.netCents)
    }

    @Test
    fun secondPlrOfTheYearCountsTheFirst() {
        // First R$ 8.000 (exempt), then R$ 12.000: the year adds up to R$ 20.000, so the second pays all of it.
        val first = Plr.calculate(800_000)
        val second = Plr.calculate(1_200_000, earlierCents = 800_000)
        assertEquals(0L, first.taxCents)
        assertEquals(233_342L, second.taxCents)
        // Alone, R$ 12.000 would pay less: (12.000 x 15%) - 1.360,35 = 439,65.
        assertEquals(43_965L, Plr.calculate(1_200_000).taxCents)
    }

    @Test
    fun otherDiscountsComeOffTheNet() {
        assertEquals(1_000_000L - 43_965L - 5_000L, Plr.calculate(1_000_000 + 200_000, otherDiscountsCents = 5_000).netCents - 200_000)
    }
}
