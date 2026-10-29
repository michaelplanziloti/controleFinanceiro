package com.controlefinanceiro.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Vacation pay of a CLT employee (prototype). The month counts as 30 days, as in the payroll.
 *
 * - Days taken: gross / 30 x days, plus one third (constitutional 1/3).
 * - Days sold (abono pecuniário, up to 10): same value plus one third, free of INSS and IRRF.
 * - INSS and IRRF are charged on the days taken plus their third, apart from the month's salary.
 * - The first half of the 13th salary can be paid with the vacation, with no discounts now.
 * - It is paid up to 2 days before the start, so the salary of the months of the vacation only
 *   pays the days worked.
 */
object Vacation {
    const val MAX_SOLD = 10

    data class Input(
        val grossCents: Long,
        val daysTaken: Int,
        val daysSold: Int = 0,
        val dependents: Int = 0,
        val advance13th: Boolean = false,
        val start: LocalDate? = null,
    )

    /** The salary of a month the vacation goes through: only the days worked in it. */
    data class MonthSalary(val month: YearMonth, val vacationDays: Int, val workedDays: Int, val grossCents: Long)

    data class Result(
        val vacationCents: Long,
        val vacationThirdCents: Long,
        val soldCents: Long,
        val soldThirdCents: Long,
        val advance13thCents: Long,
        val inssCents: Long,
        val irrfCents: Long,
        val start: LocalDate?,
        /** Last day off. */
        val end: LocalDate?,
        /** First day back at work. */
        val back: LocalDate?,
        /** Latest day the company must pay. */
        val payBy: LocalDate?,
        val salaries: List<MonthSalary>,
        /** Rules the input breaks, to show next to the result. */
        val warnings: List<String>,
    ) {
        val grossTotalCents get() = vacationCents + vacationThirdCents + soldCents + soldThirdCents + advance13thCents
        val discountsCents get() = inssCents + irrfCents
        val netCents get() = grossTotalCents - discountsCents
    }

    fun calculate(input: Input): Result {
        val day = input.grossCents / 30.0
        val vacation = Math.round(day * input.daysTaken)
        val vacationThird = Math.round(vacation / 3.0)
        val sold = Math.round(day * input.daysSold)
        val soldThird = Math.round(sold / 3.0)
        val inss = Payroll.inss(vacation + vacationThird)
        val irrf = Payroll.irrf(vacation + vacationThird, inss, input.dependents)

        val warnings = buildList {
            if (input.daysSold > MAX_SOLD) add("Só dá para vender até $MAX_SOLD dias (1/3 das férias).")
            if (input.daysTaken + input.daysSold > 30) add("Dias tirados e vendidos passam de 30.")
            if (input.daysTaken in 1..4) add("Cada período de férias precisa ter pelo menos 5 dias.")
            if (input.daysTaken in 5..13 && input.daysTaken + input.daysSold < 30) {
                add("Se dividir as férias, um dos períodos precisa ter pelo menos 14 dias.")
            }
        }

        val start = input.start
        val end = start?.plusDays(input.daysTaken - 1L)
        val salaries = if (start == null || end == null || input.daysTaken <= 0) emptyList() else {
            generateSequence(YearMonth.from(start)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(end)) }.map { ym ->
                val from = maxOf(start, ym.atDay(1))
                val to = minOf(end, ym.atEndOfMonth())
                val off = (to.toEpochDay() - from.toEpochDay() + 1).toInt()
                val worked = (30 - off).coerceAtLeast(0)
                MonthSalary(ym, off, worked, Math.round(day * worked))
            }.toList()
        }
        return Result(
            vacationCents = vacation,
            vacationThirdCents = vacationThird,
            soldCents = sold,
            soldThirdCents = soldThird,
            advance13thCents = if (input.advance13th) input.grossCents / 2 else 0,
            inssCents = inss,
            irrfCents = irrf,
            start = start,
            end = end,
            back = end?.plusDays(1),
            payBy = start?.minusDays(2),
            salaries = salaries,
            warnings = warnings,
        )
    }
}
