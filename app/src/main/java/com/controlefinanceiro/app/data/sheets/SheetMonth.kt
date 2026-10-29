package com.controlefinanceiro.app.data.sheets

import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.domain.Installments
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlin.math.roundToLong

/** A month tab as the Apps Script reads it (doGet ?aba=Out26). Money in cents. */
data class SheetMonth(
    val year: Int,
    val month: Int,
    val editedAt: Long,
    /** True when the tab's last change was a send from the app ("pelo app"). */
    val lastChangeFromApp: Boolean,
    val incomes: List<Income>,
    val allowances: List<Allowance>,
    val expenses: List<Expense>,
    /**
     * Purchases of subcategories, listed in the tab away from the main area; null when the tab has
     * no such area (an old script, or a tab the app never wrote items to), so the app's are kept.
     */
    val items: List<Item>? = emptyList(),
) {
    /** [subcategory] is the description of the row it adds up in (e.g. "Mercado"). */
    data class Item(
        val subcategory: String,
        val description: String,
        val date: Long?,
        val amountCents: Long,
        val onDay15: Boolean,
        /** Read from a "(1/2)" after the description; 1 of 1 when there is none. */
        val installmentNumber: Int = 1,
        val installmentCount: Int = 1,
    )

    data class Income(val source: String, val person: String?, val amountCents: Long)
    data class Allowance(val person: String, val amountCents: Long)
    data class Expense(
        val block: ExpenseBlock,
        val description: String,
        val category: String?,
        val payment: String?,
        val person: String?,
        val dueDay: String?,
        val plannedCents: Long,
        val autoPlanned: Boolean,
        val paid30Cents: Long,
        val paid15Cents: Long,
        val paid: Boolean,
    )
}

/** One month tab in the spreadsheet and when it was last edited there (doGet without ?aba). */
data class SheetTab(val name: String, val year: Int, val month: Int, val editedAt: Long)

/** Parses the script's JSON replies. */
object SheetsReader {

    fun tabs(json: JSONObject): List<SheetTab> = json.getJSONArray("abas").objects().map {
        SheetTab(it.getString("aba"), it.getInt("year"), it.getInt("month"), it.optLong("editedAt"))
    }

    fun month(json: JSONObject, tab: SheetTab): SheetMonth = SheetMonth(
        year = tab.year,
        month = tab.month,
        editedAt = json.optLong("editedAt", tab.editedAt),
        lastChangeFromApp = json.optString("origem") == "pelo app",
        incomes = json.getJSONArray("incomes").objects().map {
            SheetMonth.Income(it.text("source").orEmpty(), it.text("person"), it.cents("amount"))
        },
        allowances = json.getJSONArray("allowances").objects().mapNotNull { a ->
            a.text("person")?.let { SheetMonth.Allowance(it, a.cents("amount")) }
        },
        expenses = json.getJSONArray("expenses").objects().mapNotNull { e ->
            val block = runCatching { ExpenseBlock.valueOf(e.getString("block")) }.getOrNull() ?: return@mapNotNull null
            SheetMonth.Expense(
                block = block,
                description = e.text("description") ?: return@mapNotNull null,
                category = e.text("category"),
                payment = e.text("payment"),
                person = e.text("person"),
                dueDay = e.text("dueDay"),
                plannedCents = e.cents("planned"),
                autoPlanned = e.optBoolean("autoPlanned"),
                paid30Cents = e.cents("paid30"),
                paid15Cents = e.cents("paid15"),
                paid = e.optBoolean("paid"),
            )
        },
        items = items(json),
    )

    private fun items(json: JSONObject): List<SheetMonth.Item>? = json.optJSONArray("items")?.objects()?.mapNotNull { i ->
        val subcategory = i.text("subcategory") ?: return@mapNotNull null
        val (description, number, count) = Installments.parse(i.text("description") ?: subcategory)
        SheetMonth.Item(
            subcategory = subcategory,
            description = description,
            date = i.text("date")?.let(::parseDate),
            amountCents = i.cents("amount"),
            onDay15 = i.optInt("day", 15) != 30,
            installmentNumber = number,
            installmentCount = count,
        )
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.text(name: String): String? =
        if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }

    private fun JSONObject.cents(name: String): Long = (optDouble(name, 0.0).takeIf { !it.isNaN() } ?: 0.0).times(100).roundToLong()
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

/** "28/10/2026" as epoch day, or null when it is not a date. */
fun parseDate(text: String): Long? = runCatching { LocalDate.parse(text.trim(), DATE_FORMAT).toEpochDay() }.getOrNull()

fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)

/** Name used to match the spreadsheet's text to the app's lists: no case, accents or extra spaces. */
fun String.nameKey(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().replace(Regex("\\s+"), " ")

/** "Cartão Santander" in the spreadsheet is the card "Santander" paid with "Cartão de crédito". */
fun cardFromPayment(payment: String): String? {
    val key = payment.nameKey()
    if (!key.startsWith("cartao ") || key == "cartao de credito" || key == "cartao de debito") return null
    return payment.trim().substring("Cartão ".length).trim().takeIf { it.isNotEmpty() }
}
