package com.controlefinanceiro.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class VacationTest {
    @Test
    fun inssByBandsUpToTheCeiling() {
        assertEquals(36_860L, Payroll.inss(400_000))
        assertEquals(98_809L, Payroll.inss(847_555))
        assertEquals(98_809L, Payroll.inss(2_000_000))
    }

    @Test
    fun irrfWithThe2026Reduction() {
        // Up to R$ 5.000 there is no tax.
        assertEquals(0L, Payroll.irrf(500_000, Payroll.inss(500_000)))
        // Above R$ 7.350 the table applies in full: (7.900,80 x 27,5%) - 908,73.
        assertEquals(126_399L, Payroll.irrf(888_889, 98_809))
    }

    @Test
    fun twentyDaysTakenAndTenSold() {
        val r = Vacation.calculate(Vacation.Input(grossCents = 1_000_000, daysTaken = 20, daysSold = 10, start = LocalDate.of(2026, 12, 21)))
        assertEquals(666_667L, r.vacationCents)
        assertEquals(222_222L, r.vacationThirdCents)
        assertEquals(333_333L, r.soldCents)
        assertEquals(111_111L, r.soldThirdCents)
        // Sold days and their third pay no INSS or IRRF.
        assertEquals(98_809L, r.inssCents)
        assertEquals(126_399L, r.irrfCents)
        assertEquals(1_108_125L, r.netCents)
        assertEquals(LocalDate.of(2027, 1, 9), r.end)
        assertEquals(LocalDate.of(2027, 1, 10), r.back)
        assertEquals(LocalDate.of(2026, 12, 19), r.payBy)
        // 11 days off in December, 9 in January: each salary pays only the days worked.
        assertEquals(
            listOf(Vacation.MonthSalary(YearMonth.of(2026, 12), 11, 19, 633_333), Vacation.MonthSalary(YearMonth.of(2027, 1), 9, 21, 700_000)),
            r.salaries,
        )
        assertTrue(r.warnings.isEmpty())
        // The 20 days pay R$ 6.666,67, less 3/4 of the discounts (their share of days + third): R$ 4.977,61 to keep,
        // 11 days for December and 9 for January. The rest is free.
        assertEquals(497_761L, r.reserveCents)
        assertEquals(
            listOf(Vacation.Reserve(YearMonth.of(2026, 12), 11, 273_769), Vacation.Reserve(YearMonth.of(2027, 1), 9, 223_992)),
            r.reserves,
        )
        assertEquals(610_364L, r.freeCents)
    }

    @Test
    fun keepsMoneyForEachMonthOfTheVacation() {
        // 23/11 to 03/12: 8 days in November, 3 in December.
        val r = Vacation.calculate(Vacation.Input(grossCents = 300_000, daysTaken = 11, start = LocalDate.of(2026, 11, 23)))
        assertEquals(listOf(8, 3), r.reserves.map { it.days })
        assertEquals(r.reserveCents, r.reserves.sumOf { it.cents })
        assertEquals(110_000L, r.vacationCents)
        // No tax at this salary: the whole pay of the days is kept.
        assertEquals(110_000L - r.inssCents * 3 / 4, r.reserveCents)
    }

    @Test
    fun thirtyDaysWithThe13thAdvance() {
        val r = Vacation.calculate(Vacation.Input(grossCents = 300_000, daysTaken = 30, advance13th = true))
        assertEquals(400_000L, r.vacationCents + r.vacationThirdCents)
        assertEquals(36_860L, r.inssCents)
        assertEquals(0L, r.irrfCents)
        assertEquals(150_000L, r.advance13thCents)
        assertEquals(400_000L + 150_000L - 36_860L, r.netCents)
    }

    @Test
    fun warnsAboutBrokenRules() {
        assertEquals(1, Vacation.calculate(Vacation.Input(300_000, daysTaken = 15, daysSold = 12)).warnings.size)
        assertEquals(1, Vacation.calculate(Vacation.Input(300_000, daysTaken = 10)).warnings.size)
    }
}
