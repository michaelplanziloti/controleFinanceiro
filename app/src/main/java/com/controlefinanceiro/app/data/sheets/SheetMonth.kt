package com.controlefinanceiro.app.data.sheets

import com.controlefinanceiro.app.data.db.ExpenseBlock
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
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
) {
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
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.text(name: String): String? =
        if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }

    private fun JSONObject.cents(name: String): Long = (optDouble(name, 0.0).takeIf { !it.isNaN() } ?: 0.0).times(100).roundToLong()
}

/** Name used to match the spreadsheet's text to the app's lists: no case, accents or extra spaces. */
fun String.nameKey(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().replace(Regex("\\s+"), " ")

/** "Cartão Santander" in the spreadsheet is the card "Santander" paid with "Cartão de crédito". */
fun cardFromPayment(payment: String): String? {
    val key = payment.nameKey()
    if (!key.startsWith("cartao ") || key == "cartao de credito" || key == "cartao de debito") return null
    return payment.trim().substring("Cartão ".length).trim().takeIf { it.isNotEmpty() }
}
