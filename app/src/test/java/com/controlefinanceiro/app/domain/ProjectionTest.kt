package com.controlefinanceiro.app.domain

import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionTest {
    private fun row(description: String, block: ExpenseBlock, planned: Long, paid: Long = planned, subcategory: Long? = null, total: Long? = null) =
        ExpenseEntity(
            monthId = 1, block = block, description = description, plannedCents = planned, paidDay15Cents = paid, isPaid = paid > 0,
            subcategoryId = subcategory, installmentTotalCents = total,
        )

    @Test
    fun projectsLikeANewMonthCopyingTheLatest() {
        val template = listOf(
            row("Aluguel", ExpenseBlock.FIXED, 200_000),
            row("Presente", ExpenseBlock.PERSONAL, 0, paid = 15_000),
            row("Viagem", ExpenseBlock.EXTRA, 0, paid = 90_000),
            row("Mercado", ExpenseBlock.PERSONAL, 80_000, paid = 20_000, subcategory = 3),
            row("Celular (1/3)", ExpenseBlock.COUPLE, 3_334, total = 10_000),
            row("Mala (2/2)", ExpenseBlock.COUPLE, 33_000),
        )
        val next = Projection.expenses(template, monthsAhead = 1)
        assertEquals(listOf("Aluguel", "Mercado", "Celular (1/3)"), next.map { it.description })
        assertEquals(listOf(200_000L, 80_000L, 3_333L), next.map { it.plannedCents })
        assertEquals(0L, next.sumOf { it.paidDay15Cents + it.paidDay30Cents })
        // Two months later the installments of Celular are over (1 + 2 = 3, the last one).
        assertEquals(3_333L, Projection.expenses(template, monthsAhead = 2).first { it.description.startsWith("Celular") }.plannedCents)
        assertEquals(listOf("Aluguel", "Mercado"), Projection.expenses(template, monthsAhead = 3).map { it.description })
        assertEquals(13, Projection.monthsBetween(2026, 11, 2027, 12))
    }
}
