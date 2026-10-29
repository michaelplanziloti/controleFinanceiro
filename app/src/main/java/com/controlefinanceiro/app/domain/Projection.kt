package com.controlefinanceiro.app.domain

import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity

/**
 * A month not created yet, as "Novo mês" would make it copying the latest month: the recurring
 * bills and the subcategory rows by their targets, and the installments that are still running.
 * Nothing in it is paid.
 */
object Projection {
    private val RECURRING = setOf(ExpenseBlock.TITHE, ExpenseBlock.FIXED, ExpenseBlock.COUPLE, ExpenseBlock.INVESTMENT)

    /** Entries of the month [monthsAhead] months after the one of [template]. */
    fun expenses(template: List<ExpenseEntity>, monthsAhead: Int): List<ExpenseEntity> = template.mapNotNull { e ->
        val (_, number, count) = Installments.parse(e.description)
        val planned = when {
            count > 1 -> {
                val k = number + monthsAhead
                if (k > count) return@mapNotNull null
                e.installmentTotalCents?.let { Installments.share(it, count, k) } ?: e.plannedCents
            }
            e.block in RECURRING || e.subcategoryId != null -> e.plannedCents
            else -> return@mapNotNull null
        }
        e.copy(plannedCents = planned, paidDay15Cents = 0, paidDay30Cents = 0, isPaid = false)
    }

    /** Months from (y1, m1) to (y2, m2). */
    fun monthsBetween(y1: Int, m1: Int, y2: Int, m2: Int): Int = (y2 * 12 + m2) - (y1 * 12 + m1)
}
