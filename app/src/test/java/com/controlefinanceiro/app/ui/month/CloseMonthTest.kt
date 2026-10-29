package com.controlefinanceiro.app.ui.month

import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CloseMonthTest {
    private fun row(id: Long, block: ExpenseBlock, planned: Long, paid: Long, subcategory: Long? = null) =
        ExpenseEntity(id = id, monthId = 1, block = block, description = "r$id", plannedCents = planned, paidDay15Cents = paid, subcategoryId = subcategory)

    @Test
    fun equalizesOnlySubcategoriesThatDiffer() {
        val rows = listOf(
            row(1, ExpenseBlock.COUPLE, 70_000, 5_000, subcategory = 9), // left over
            row(2, ExpenseBlock.FIXED, 40_000, 44_000, subcategory = 8), // went over
            row(3, ExpenseBlock.COUPLE, 10_000, 10_000, subcategory = 7), // already matches
            row(4, ExpenseBlock.COUPLE, 60_000, 0), // not a subcategory
        )
        assertEquals(listOf(1L, 2L), targetsToEqualize(rows).map { it.id })
    }

    @Test
    fun extraWeighsTheLargerOfPlannedAndPaid() {
        assertEquals(30_000L, extraValue(row(5, ExpenseBlock.EXTRA, 0, 30_000)))
        assertEquals(25_000L, extraValue(row(6, ExpenseBlock.EXTRA, 25_000, 0)))
    }
}
