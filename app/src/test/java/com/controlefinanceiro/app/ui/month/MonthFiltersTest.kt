package com.controlefinanceiro.app.ui.month

import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthFiltersTest {
    private fun row(due: String? = null, paid15: Long = 0, paid30: Long = 0, isPaid: Boolean = false, card: Long? = null, planned: Long = 0) =
        ExpenseEntity(
            id = 1, monthId = 1, block = ExpenseBlock.FIXED, description = "x", dueDay = due, paidDay15Cents = paid15,
            paidDay30Cents = paid30, isPaid = isPaid, cardId = card, plannedCents = planned,
        )

    private fun MonthFilters.ok(e: ExpenseEntity, items: List<SubcategoryItemEntity> = emptyList()) = matches(e, items, e.plannedCents)

    @Test
    fun payDayByDueDayPaymentOrItems() {
        val day15 = MonthFilters(day = PayDay.DAY15)
        val day30 = MonthFilters(day = PayDay.DAY30)
        assertTrue(day15.ok(row(due = "15")))
        assertFalse(day30.ok(row(due = "15")))
        // "15/30" is paid on both days.
        assertTrue(day15.ok(row(due = "15/30")) && day30.ok(row(due = "15/30")))
        // No due day: what was paid on each day decides.
        assertTrue(day30.ok(row(paid30 = 100)))
        assertFalse(day15.ok(row(paid30 = 100)))
        // A subcategory row: its purchases decide.
        val items = listOf(SubcategoryItemEntity(expenseId = 1, description = "Feira", amountCents = 10, onDay15 = false))
        assertTrue(day30.ok(row(), items))
        assertFalse(day15.ok(row(), items))
        assertEquals(1, day30.items(items).size)
        assertEquals(0, day15.items(items).size)
    }

    @Test
    fun filtersCombine() {
        val f = MonthFilters(day = PayDay.DAY15, paid = false, cardId = 7)
        assertTrue(f.ok(row(due = "15", card = 7)))
        assertFalse(f.ok(row(due = "15", card = 7, isPaid = true)))
        assertFalse(f.ok(row(due = "15", card = 8)))
        assertFalse(f.ok(row(due = "30", card = 7)))
        assertEquals(3, f.count)
        assertFalse(MonthFilters().active)
    }

    @Test
    fun overTarget() {
        val f = MonthFilters(overTarget = true)
        assertTrue(f.ok(row(paid15 = 120, planned = 100)))
        assertFalse(f.ok(row(paid15 = 100, planned = 100)))
    }
}
