package com.controlefinanceiro.app.ui.fund

import com.controlefinanceiro.app.data.db.FundIncomeEntity
import com.controlefinanceiro.app.data.db.FundKind
import com.controlefinanceiro.app.data.db.PlrEntity
import com.controlefinanceiro.app.domain.Plr
import com.controlefinanceiro.app.ui.plr.plrTithe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FundTitheTest {
    private fun extra(cents: Long) = FundIncomeEntity(kind = FundKind.PLR, ownerId = 1, source = "Extra", amountCents = cents)
    private fun tithe(t: FundTotals) = t.pay.firstOrNull { it.label == "Dízimo" }?.cents

    @Test
    fun titheFollowsPayAndExtraMoney() {
        val pay = listOf(PayLine("PLR líquida", "", 1_000_000))
        val t = FundTotals(pay, listOf(extra(200_000)), emptyList()).withTithe(1000, 1_000_000)
        assertEquals(-120_000L, tithe(t))
        assertEquals(1_200_000 - 120_000L, t.moneyCents)
        // More extra money, more tithe.
        assertEquals(-140_000L, tithe(FundTotals(pay, listOf(extra(400_000)), emptyList()).withTithe(1000, 1_000_000)))
    }

    @Test
    fun postedPlrPaysItsTitheInTheMonth() {
        val r = Plr.calculate(1_000_000, 0, 0)
        val pay = listOf(PayLine("PLR líquida", "", r.netCents))
        val plr = PlrEntity(id = 1, userId = 1, grossCents = 1_000_000)
        assertEquals(-Math.round(r.netCents / 10.0), tithe(FundTotals(pay, emptyList(), emptyList()).plrTithe(plr, r, 1000)))
        assertNull(tithe(FundTotals(pay, emptyList(), emptyList()).plrTithe(plr.copy(postedMonthId = 5), r, 1000)))
        assertEquals(-10_000L, tithe(FundTotals(pay, listOf(extra(100_000)), emptyList()).plrTithe(plr.copy(postedMonthId = 5), r, 1000)))
    }
}
