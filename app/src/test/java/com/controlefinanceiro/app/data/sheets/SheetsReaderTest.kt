package com.controlefinanceiro.app.data.sheets

import com.controlefinanceiro.app.data.db.ExpenseBlock
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetsReaderTest {

    @Test
    fun readsTheTabList() {
        val tabs = SheetsReader.tabs(JSONObject("""{"ok":true,"abas":[{"aba":"Nov26","year":2026,"month":11,"editedAt":1700}]}"""))
        assertEquals(listOf(SheetTab("Nov26", 2026, 11, 1700)), tabs)
    }

    @Test
    fun readsAMonthTab() {
        val json = JSONObject(
            """
            {"ok":true,"aba":"Nov26","editedAt":42,
             "incomes":[{"source":"j&j","person":"julia","amount":3647},{"source":"Bônus","person":null,"amount":0.1}],
             "allowances":[{"person":"Michael","amount":841.5}],
             "expenses":[
               {"block":"TITHE","description":"Dízimo","category":"Dízimo","payment":"À vista/Pix","dueDay":"15/30","planned":0,"autoPlanned":true,"paid30":558,"paid15":738.8,"paid":false},
               {"block":"COUPLE","description":"Mercado","category":null,"payment":"Cartão Santander","planned":1500,"paid30":0,"paid15":1261.71,"paid":true},
               {"block":"PERSONAL","description":"Unha","person":"Julia","category":"Beleza","payment":"","planned":35,"paid30":0,"paid15":35,"paid":false},
               {"block":"BOGUS","description":"x","planned":1}
             ]}
            """.trimIndent()
        )
        val month = SheetsReader.month(json, SheetTab("Nov26", 2026, 11, 0))
        assertEquals(42, month.editedAt)
        assertEquals(SheetMonth.Income("j&j", "julia", 364_700), month.incomes[0])
        assertEquals(SheetMonth.Income("Bônus", null, 10), month.incomes[1])
        assertEquals(listOf(SheetMonth.Allowance("Michael", 84_150)), month.allowances)
        assertEquals(3, month.expenses.size)
        val tithe = month.expenses[0]
        assertEquals(ExpenseBlock.TITHE, tithe.block)
        assertTrue(tithe.autoPlanned)
        assertEquals(73_880, tithe.paid15Cents)
        val market = month.expenses[1]
        assertNull(market.category)
        assertEquals(126_171, market.paid15Cents)
        assertTrue(market.paid)
        assertNull(month.expenses[2].payment)
        assertEquals("Julia", month.expenses[2].person)
    }

    @Test
    fun matchesNamesAndCards() {
        assertEquals("cartao itau", "  Cartão  Itaú ".nameKey())
        assertEquals("Itaú", cardFromPayment("Cartão Itaú"))
        assertEquals("Nubank", cardFromPayment("cartao Nubank"))
        assertNull(cardFromPayment("Cartão de crédito"))
        assertNull(cardFromPayment("À vista/Pix"))
    }
}
