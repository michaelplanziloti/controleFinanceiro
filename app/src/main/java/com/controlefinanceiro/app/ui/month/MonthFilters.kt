package com.controlefinanceiro.app.ui.month

import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import com.controlefinanceiro.app.domain.BudgetCalculator

/** Payday of the salary that pays for an entry, like the sheet's "Pago dia 15" and "Pago dia 30". */
enum class PayDay(val label: String) { DAY15("Dia 15"), DAY30("Dia 30") }

/**
 * Filters of the month's lists; each one left null (or false) does not filter. They combine: an
 * entry shows when it passes all of them.
 */
data class MonthFilters(
    val day: PayDay? = null,
    /** true: only paid ("deram baixa"); false: only still open. */
    val paid: Boolean? = null,
    val categoryId: Long? = null,
    val paymentMethodId: Long? = null,
    val cardId: Long? = null,
    /** Only entries that spent more than their target. */
    val overTarget: Boolean = false,
) {
    /** How many filters are on, for the badge on the button. */
    val count get() = listOfNotNull(day, paid, categoryId, paymentMethodId, cardId).size + if (overTarget) 1 else 0
    val active get() = count > 0

    /**
     * Whether [expense] passes. An entry belongs to a payday when its due day says so ("15",
     * "30", "15/30"), when something was paid on that day, or when one of its [items] is of that day.
     */
    fun matches(expense: ExpenseEntity, items: List<SubcategoryItemEntity>, plannedCents: Long): Boolean {
        if (day != null && !onDay(expense, items, day)) return false
        if (paid != null && expense.isPaid != paid) return false
        if (categoryId != null && expense.categoryId != categoryId) return false
        if (paymentMethodId != null && expense.paymentMethodId != paymentMethodId) return false
        if (cardId != null && expense.cardId != cardId) return false
        if (overTarget && BudgetCalculator.actual(expense) <= plannedCents) return false
        return true
    }

    /** The purchases of a row that the day filter keeps (all of them without it). */
    fun items(items: List<SubcategoryItemEntity>): List<SubcategoryItemEntity> =
        if (day == null) items else items.filter { it.onDay15 == (day == PayDay.DAY15) }

    private fun onDay(expense: ExpenseEntity, items: List<SubcategoryItemEntity>, day: PayDay): Boolean {
        val number = if (day == PayDay.DAY15) "15" else "30"
        val due = expense.dueDay.orEmpty().split('/', ',', ' ', '-').map { it.trim() }
        val paidThatDay = if (day == PayDay.DAY15) expense.paidDay15Cents else expense.paidDay30Cents
        return number in due || paidThatDay != 0L || items.any { it.onDay15 == (day == PayDay.DAY15) }
    }
}
