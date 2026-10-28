package com.controlefinanceiro.app.data.sheets

import com.controlefinanceiro.app.data.db.AllowanceEntity
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.CardEntity
import com.controlefinanceiro.app.data.db.CategoryEntity
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.IncomeEntity
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.PaymentMethodEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import com.controlefinanceiro.app.domain.BudgetCalculator
import kotlin.math.absoluteValue

/** Everything needed to rebuild one month tab of the spreadsheet. */
data class MonthSnapshot(
    val month: MonthEntity,
    val incomes: List<IncomeEntity>,
    val allowances: List<AllowanceEntity>,
    val expenses: List<ExpenseEntity>,
    val people: List<PersonEntity>,
    val categories: List<CategoryEntity>,
    val paymentMethods: List<PaymentMethodEntity>,
    val cards: List<CardEntity>,
    val settings: BudgetSettingsEntity,
    val items: List<SubcategoryItemEntity> = emptyList(),
)

/**
 * JSON sent to the Apps Script in sheets/Codigo.gs. Names are resolved here, so the script only
 * places values: a linked card is written as "Cartão Santander", like the spreadsheet does.
 */
object SheetsPayload {

    fun build(snapshot: MonthSnapshot, key: String): String {
        val income = snapshot.incomes.sumOf { it.amountCents }
        fun personName(id: Long?) = snapshot.people.firstOrNull { it.id == id }?.name
        fun payment(expense: ExpenseEntity) =
            snapshot.cards.firstOrNull { it.id == expense.cardId }?.let { "Cartão ${it.name}" }
                ?: snapshot.paymentMethods.firstOrNull { it.id == expense.paymentMethodId }?.name

        return Json.obj(
            "key" to key,
            "year" to snapshot.month.year,
            "month" to snapshot.month.month,
            // The tab version this data is based on; the script refuses the send if the tab changed since.
            "baseEditedAt" to snapshot.month.sheetVersion,
            "incomes" to snapshot.incomes.map {
                Json.obj("source" to it.source, "person" to personName(it.personId), "amount" to Money(it.amountCents))
            },
            "allowances" to snapshot.allowances.mapNotNull { allowance ->
                personName(allowance.personId)?.let { Json.obj("person" to it, "amount" to Money(allowance.amountCents)) }
            },
            "expenses" to snapshot.expenses.map { e ->
                Json.obj(
                    "block" to e.block.name,
                    "description" to e.description,
                    "category" to snapshot.categories.firstOrNull { it.id == e.categoryId }?.name,
                    "payment" to payment(e),
                    "person" to personName(e.personId),
                    "dueDay" to e.dueDay,
                    "planned" to Money(BudgetCalculator.resolvedPlanned(e, income, snapshot.settings)),
                    "autoPlanned" to e.autoPlanned,
                    "paid30" to Money(e.paidDay30Cents),
                    "paid15" to Money(e.paidDay15Cents),
                    "paid" to e.isPaid,
                )
            },
            "items" to snapshot.items.mapNotNull { item ->
                val row = snapshot.expenses.firstOrNull { it.id == item.expenseId } ?: return@mapNotNull null
                Json.obj(
                    "subcategory" to row.description,
                    "description" to item.description,
                    "date" to item.date?.let(::formatDate),
                    "amount" to Money(item.amountCents),
                    "day" to if (item.onDay15) 15 else 30,
                )
            },
        ).json
    }

    /** Cents written as an exact decimal number, e.g. 12345 -> 123.45. */
    @JvmInline
    value class Money(val cents: Long) {
        override fun toString(): String {
            val sign = if (cents < 0) "-" else ""
            val abs = cents.absoluteValue
            return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
        }
    }
}

/** Minimal JSON writer: enough for the payload, without pulling in a serialization library. */
internal object Json {
    /** Already encoded JSON, kept apart from plain strings so user text is never taken as JSON. */
    @JvmInline
    value class Raw(val json: String)

    fun obj(vararg fields: Pair<String, Any?>): Raw =
        Raw(fields.joinToString(",", "{", "}") { (name, value) -> "${string(name)}:${value(value)}" })

    private fun value(value: Any?): String = when (value) {
        null -> "null"
        is Raw -> value.json
        is String -> string(value)
        is Boolean, is Int, is Long -> value.toString()
        is SheetsPayload.Money -> value.toString()
        is List<*> -> value.joinToString(",", "[", "]") { value(it) }
        else -> error("Unsupported JSON value: $value")
    }

    fun string(text: String): String = buildString {
        append('"')
        for (c in text) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
