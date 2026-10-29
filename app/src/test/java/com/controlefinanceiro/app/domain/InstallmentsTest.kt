package com.controlefinanceiro.app.domain

import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallmentsTest {
    @Test
    fun splitsTotalWithLeftoverOnFirst() {
        assertEquals(3_334, Installments.share(10_000, 3, 1))
        assertEquals(3_333, Installments.share(10_000, 3, 2))
        assertEquals(3_333, Installments.share(10_000, 3, 3))
        assertEquals(30_000, Installments.total(15_000, 2, typedIsTotal = false))
        assertEquals(30_000, Installments.total(30_000, 2, typedIsTotal = true))
    }

    @Test
    fun nextInstallmentUntilTheLast() {
        val first = SubcategoryItemEntity(id = 7, expenseId = 1, description = "Tênis", amountCents = 3_334, installmentCount = 3, installmentTotalCents = 10_000)
        val second = Installments.next(first, expenseId = 2)!!
        assertEquals(0L, second.id)
        assertEquals(2L, second.expenseId)
        assertEquals(2, second.installmentNumber)
        assertEquals(3_333, second.amountCents)
        val third = Installments.next(second, expenseId = 3)!!
        assertEquals(3, third.installmentNumber)
        assertNull(Installments.next(third, expenseId = 4))
        assertNull(Installments.next(first.copy(installmentCount = 1), expenseId = 2))
    }

    @Test
    fun descriptionInTheSheet() {
        val item = SubcategoryItemEntity(expenseId = 1, description = "Tênis", amountCents = 15_000, installmentNumber = 1, installmentCount = 2)
        assertEquals("Tênis (1/2)", Installments.sheetDescription(item))
        assertEquals(Triple("Tênis", 1, 2), Installments.parse("Tênis (1/2)"))
        assertEquals(Triple("Geladeira nova", 10, 12), Installments.parse("Geladeira nova ( 10 / 12 )"))
        assertEquals(Triple("Pão (3/2)", 1, 1), Installments.parse("Pão (3/2)"))
        assertEquals(Triple("Feira", 1, 1), Installments.parse("Feira"))
    }
}
