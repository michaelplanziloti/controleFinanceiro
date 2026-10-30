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

class FundPaysEntryTest {
    private fun entry(planned: Long, paid15: Long = 0, fund: Long = 0, block: com.controlefinanceiro.app.data.db.ExpenseBlock = com.controlefinanceiro.app.data.db.ExpenseBlock.FIXED) =
        com.controlefinanceiro.app.data.db.ExpenseEntity(monthId = 1, block = block, description = "IPVA", plannedCents = planned, paidDay15Cents = paid15, fundPaidCents = fund)

    @Test
    fun leftToPayIsWhatTheMonthStillOwes() {
        assertEquals(200_000L, leftToPay(entry(200_000)))
        assertEquals(150_000L, leftToPay(entry(200_000, paid15 = 50_000)))
        // Nothing left (or no target): the whole target is offered.
        assertEquals(200_000L, leftToPay(entry(200_000, paid15 = 200_000)))
    }

    @Test
    fun paidByPlrComesOffTheMonthTarget() {
        val settings = com.controlefinanceiro.app.data.db.BudgetSettingsEntity(userId = 1)
        assertEquals(50_000L, com.controlefinanceiro.app.domain.BudgetCalculator.resolvedPlanned(entry(200_000, fund = 150_000), 0, settings))
        assertEquals(0L, com.controlefinanceiro.app.domain.BudgetCalculator.resolvedPlanned(entry(200_000, fund = 250_000), 0, settings))
    }

    @Test
    fun onlyOpenEntriesCanBePaid() {
        val tithe = entry(1, block = com.controlefinanceiro.app.data.db.ExpenseBlock.TITHE)
        val paid = entry(1).copy(isPaid = true)
        val row = entry(1).copy(subcategoryId = 3)
        val open = entry(1)
        assertEquals(listOf(open), payableEntries(listOf(tithe, paid, row, open)))
    }
}

class FundPaidActualTest {
    @Test
    fun paidValuesCoveredByPlrLeaveTheMonthButStayOnTheCard() {
        val e = com.controlefinanceiro.app.data.db.ExpenseEntity(
            monthId = 1, block = com.controlefinanceiro.app.data.db.ExpenseBlock.EXTRA, description = "IPVA",
            plannedCents = 200_000, paidDay15Cents = 200_000, fundPaidCents = 150_000,
        )
        assertEquals(50_000L, com.controlefinanceiro.app.domain.BudgetCalculator.actual(e))
        assertEquals(200_000L, com.controlefinanceiro.app.domain.BudgetCalculator.billedActual(e))
    }
}
