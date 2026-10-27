package com.controlefinanceiro.app.domain

import com.controlefinanceiro.app.data.db.AllowanceEntity
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.IncomeEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.ui.common.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalculatorTest {
    private val julia = PersonEntity(id = 1, userId = 1, name = "Julia")
    private val michael = PersonEntity(id = 2, userId = 1, name = "Michael")
    private val settings = BudgetSettingsEntity(userId = 1)

    private fun expense(block: ExpenseBlock, planned: Long, d30: Long = 0, d15: Long = 0, person: Long? = null, auto: Boolean = false) =
        ExpenseEntity(monthId = 1, block = block, description = block.name, plannedCents = planned,
            paidDay30Cents = d30, paidDay15Cents = d15, personId = person, autoPlanned = auto)

    private val summary = BudgetCalculator.summarize(
        incomes = listOf(
            IncomeEntity(monthId = 1, personId = 1, source = "Julia", amountCents = 600_000),
            IncomeEntity(monthId = 1, personId = 2, source = "Michael", amountCents = 400_000),
        ),
        allowances = listOf(
            AllowanceEntity(monthId = 1, personId = 1, amountCents = 50_000),
            AllowanceEntity(monthId = 1, personId = 2, amountCents = 40_000),
        ),
        expenses = listOf(
            expense(ExpenseBlock.TITHE, 0, d30 = 40_000, d15 = 60_000, auto = true),
            expense(ExpenseBlock.FIXED, 300_000, d15 = 290_000),
            expense(ExpenseBlock.COUPLE, 80_000, d15 = 50_000),
            expense(ExpenseBlock.PERSONAL, 20_000, d15 = 15_000, person = 1),
            expense(ExpenseBlock.PERSONAL, 10_000, d30 = 12_000, person = 2),
            expense(ExpenseBlock.INVESTMENT, 100_000, d30 = 100_000),
            expense(ExpenseBlock.EXTRA, 200_000, d15 = 200_000),
        ),
        people = listOf(julia, michael),
        settings = settings,
    )

    private fun group(g: BudgetGroup) = summary.groups.first { it.group == g }

    @Test
    fun recommendedFollowsTitheThenFiftyThirtyTwenty() {
        assertEquals(100_000, group(BudgetGroup.TITHE).recommendedCents)
        assertEquals(450_000, group(BudgetGroup.FIXED).recommendedCents)
        assertEquals(270_000, group(BudgetGroup.VARIABLE).recommendedCents)
        assertEquals(180_000, group(BudgetGroup.INVESTMENT).recommendedCents)
    }

    @Test
    fun autoTitheIsTenPercentOfIncome() {
        assertEquals(100_000, group(BudgetGroup.TITHE).plannedCents)
        assertEquals(100_000, group(BudgetGroup.TITHE).actualCents)
    }

    @Test
    fun variableUsesCoupleExpensesPlusAllowances() {
        assertEquals(80_000 + 90_000, group(BudgetGroup.VARIABLE).plannedCents)
        assertEquals(50_000 + 15_000 + 12_000, group(BudgetGroup.VARIABLE).actualCents)
    }

    @Test
    fun extrasStayOutOfTheBudget() {
        assertEquals(567_000, summary.actualCents)
        assertEquals(200_000, summary.extraActualCents)
        assertEquals(433_000, summary.surplusCents)
        assertEquals(0.533, summary.savingsRate, 0.0001)
    }

    @Test
    fun allowancesAndCoupleSplit() {
        val j = summary.people.first { it.person.id == 1L }
        val m = summary.people.first { it.person.id == 2L }
        assertEquals(35_000, j.balanceCents)
        assertEquals(28_000, m.balanceCents)
        assertEquals(90_000, summary.totalAllowanceCents)
        assertEquals(740_000, summary.coupleActualCents)
        assertEquals(0.6, j.incomeShare, 0.0001)
        assertEquals(444_000, j.coupleShareCents)
        assertEquals(296_000, m.coupleShareCents)
    }

    @Test
    fun equalSplitWhenIncomeHasNoOwner() {
        val s = BudgetCalculator.summarize(
            listOf(IncomeEntity(monthId = 1, personId = null, source = "Casal", amountCents = 100_000)),
            emptyList(), emptyList(), listOf(julia, michael), settings,
        )
        assertTrue(s.people.all { it.incomeShare == 0.5 })
    }

    @Test
    fun parsesBrazilianMoney() {
        assertEquals(123_456L, Money.parse("1.234,56"))
        assertEquals(123_450L, Money.parse("1234,5"))
        assertEquals(123_456L, Money.parse("1234.56"))
        assertEquals(1_000L, Money.parse("R$ 10"))
        assertEquals(0L, Money.parse(""))
        assertNull(Money.parse("abc"))
        assertEquals("1234,56", Money.toInput(123_456))
    }

    @Test
    fun passwordHashRoundTrip() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("segredo123", salt)
        assertTrue(PasswordHasher.matches("segredo123", salt, hash))
        assertTrue(!PasswordHasher.matches("errada", salt, hash))
    }
}
