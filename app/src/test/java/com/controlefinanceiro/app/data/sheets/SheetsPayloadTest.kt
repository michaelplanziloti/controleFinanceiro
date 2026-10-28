package com.controlefinanceiro.app.data.sheets

import com.controlefinanceiro.app.data.db.AllowanceEntity
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.CardEntity
import com.controlefinanceiro.app.data.db.CategoryEntity
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.IncomeEntity
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.PaymentMethodEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetsPayloadTest {
    private val julia = PersonEntity(id = 1, userId = 1, name = "Julia")
    private val snapshot = MonthSnapshot(
        month = MonthEntity(id = 9, userId = 1, year = 2026, month = 10),
        incomes = listOf(IncomeEntity(monthId = 9, personId = 1, source = "Salário", amountCents = 1_000_000)),
        allowances = listOf(AllowanceEntity(monthId = 9, personId = 1, amountCents = 84_150)),
        expenses = listOf(
            ExpenseEntity(monthId = 9, block = ExpenseBlock.TITHE, description = "Dízimo", autoPlanned = true),
            ExpenseEntity(
                monthId = 9, block = ExpenseBlock.COUPLE, description = "Mercado \"grande\"", categoryId = 3,
                paymentMethodId = 4, cardId = 5, plannedCents = 160_000, paidDay15Cents = 157_729, isPaid = true,
            ),
            ExpenseEntity(monthId = 9, block = ExpenseBlock.PERSONAL, description = "Reembolso", personId = 1, plannedCents = -9_100),
        ),
        people = listOf(julia),
        categories = listOf(CategoryEntity(id = 3, userId = 1, name = "Mercado")),
        paymentMethods = listOf(PaymentMethodEntity(id = 4, userId = 1, name = "Cartão de crédito")),
        cards = listOf(CardEntity(id = 5, userId = 1, name = "Santander")),
        settings = BudgetSettingsEntity(userId = 1),
    )

    private val json = SheetsPayload.build(snapshot, "chave-1")

    @Test
    fun namesAreResolvedAndCardsUseTheSpreadsheetLabel() {
        assertTrue(json.startsWith("""{"key":"chave-1","year":2026,"month":10,"""))
        assertTrue(json.contains(""""incomes":[{"source":"Salário","person":"Julia","amount":10000.00}]"""))
        assertTrue(json.contains(""""allowances":[{"person":"Julia","amount":841.50}]"""))
        assertTrue(json.contains(""""category":"Mercado","payment":"Cartão Santander""""))
        assertTrue(json.contains(""""person":"Julia""""))
    }

    @Test
    fun moneyIsExactAndTitheUsesTheIncome() {
        assertTrue(json.contains(""""planned":1000.00,"autoPlanned":true"""))
        assertTrue(json.contains(""""paid15":1577.29,"paid":true"""))
        assertTrue(json.contains(""""planned":-91.00"""))
        assertEquals("0.05", SheetsPayload.Money(5).toString())
        assertEquals("-0.05", SheetsPayload.Money(-5).toString())
    }

    @Test
    fun textIsEscapedNotTakenAsJson() {
        assertTrue(json.contains(""""description":"Mercado \"grande\"""""))
        assertEquals("\"{a}\\n\"", Json.string("{a}\n"))
    }

    @Test
    fun sendsTheTabVersionItKnows() {
        assertTrue(json.contains(""""baseEditedAt":null"""))
        val synced = SheetsPayload.build(snapshot.copy(month = snapshot.month.copy(sheetVersion = 1700)), "k")
        assertTrue(synced.contains(""""baseEditedAt":1700"""))
    }

    @Test
    fun readsTheScriptReply() {
        val ok = SheetsClient.parse("""{"ok":true,"aba":"Out26"}""")
        assertTrue(ok.ok)
        assertEquals("Out26", ok.tab)

        val refused = SheetsClient.parse("""{"ok":false,"erro":"Chave inválida"}""")
        assertFalse(refused.ok)
        assertEquals("Chave inválida", refused.error)

        val conflict = SheetsClient.parse("""{"ok":false,"conflito":true,"aba":"Nov26","editedAt":5}""")
        assertTrue(conflict.conflict)
        assertEquals(5, conflict.editedAt)

        val notScript = SheetsClient.parse("<!DOCTYPE html><html>Login</html>")
        assertFalse(notScript.ok)
        assertTrue(notScript.error!!.contains("não é do script"))
    }
}
