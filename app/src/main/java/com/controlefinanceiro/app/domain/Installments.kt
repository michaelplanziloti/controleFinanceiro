package com.controlefinanceiro.app.domain

import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity

/** A purchase split in monthly installments, like "Tênis" in 2x: one item per month, 1/2 then 2/2. */
object Installments {
    /** Value of installment [number] (1-based) of [count]; the first one takes the cents that do not split evenly. */
    fun share(totalCents: Long, count: Int, number: Int): Long {
        val base = totalCents / count
        return if (number == 1) base + totalCents % count else base
    }

    /** Whole value from what was typed: the value of each installment, or the total. */
    fun total(typedCents: Long, count: Int, typedIsTotal: Boolean): Long = if (typedIsTotal) typedCents else typedCents * count

    /** "1/2", or null for a purchase paid at once. */
    fun label(item: SubcategoryItemEntity): String? =
        if (item.installmentCount > 1) "${item.installmentNumber}/${item.installmentCount}" else null

    /** The installment that follows [item] in the next month, or null after the last one. */
    fun next(item: SubcategoryItemEntity, expenseId: Long, now: Long = System.currentTimeMillis()): SubcategoryItemEntity? {
        if (item.installmentNumber >= item.installmentCount) return null
        val number = item.installmentNumber + 1
        val total = item.installmentTotalCents ?: (item.amountCents * item.installmentCount)
        return item.copy(
            id = 0,
            expenseId = expenseId,
            // The purchase date stays the one of the first installment.
            amountCents = share(total, item.installmentCount, number),
            installmentNumber = number,
            installmentTotalCents = total,
            createdAt = now,
        )
    }

    /** An entry's description with its installment, like the spreadsheet writes it: "Jaqueta (1/3)". */
    fun describe(text: String, number: Int, count: Int): String = "${text.trim()} ($number/$count)"

    /** True for an entry whose description says it is one installment of several. */
    fun isInstallment(expense: ExpenseEntity): Boolean = parse(expense.description).third > 1

    /**
     * The entry of the next installment in [monthId], unpaid, or null after the last one. Its target
     * comes from the whole value when known, otherwise it repeats this installment's.
     */
    fun next(expense: ExpenseEntity, monthId: Long, now: Long = System.currentTimeMillis()): ExpenseEntity? {
        val (text, number, count) = parse(expense.description)
        if (count < 2 || number >= count) return null
        val total = expense.installmentTotalCents
        return expense.copy(
            id = 0,
            monthId = monthId,
            description = describe(text, number + 1, count),
            plannedCents = total?.let { share(it, count, number + 1) } ?: expense.plannedCents,
            autoPlanned = false,
            paidDay30Cents = 0,
            paidDay15Cents = 0,
            isPaid = false,
            createdAt = now,
        )
    }

    /** The spreadsheet shows the installment after the description: "Tênis (1/2)". */
    fun sheetDescription(item: SubcategoryItemEntity): String =
        label(item)?.let { "${item.description} ($it)" } ?: item.description

    private val SUFFIX = Regex("""^(.*?)\s*\(\s*(\d{1,3})\s*/\s*(\d{1,3})\s*\)\s*$""")

    /** Splits "Tênis (1/2)" into ("Tênis", 1, 2); a description without a valid suffix is 1 of 1. */
    fun parse(description: String): Triple<String, Int, Int> {
        val match = SUFFIX.matchEntire(description) ?: return Triple(description, 1, 1)
        val (text, number, count) = match.destructured
        val n = number.toInt()
        val c = count.toInt()
        return if (text.isNotBlank() && c in 2..999 && n in 1..c) Triple(text, n, c) else Triple(description, 1, 1)
    }
}
