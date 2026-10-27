package com.controlefinanceiro.app.domain

import com.controlefinanceiro.app.data.db.AllowanceEntity
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.IncomeEntity
import com.controlefinanceiro.app.data.db.PersonEntity

/** The four budget groups of the 50/30/20 rule (after tithe) plus tithe itself. */
enum class BudgetGroup(val label: String) {
    TITHE("Dízimo"),
    FIXED("Fixo"),
    VARIABLE("Variável"),
    INVESTMENT("Investimento"),
}

data class GroupLine(
    val group: BudgetGroup,
    val recommendedCents: Long,
    val plannedCents: Long,
    val actualCents: Long,
) {
    val differenceCents: Long get() = plannedCents - actualCents
}

data class PersonLine(
    val person: PersonEntity,
    val incomeCents: Long,
    /** Share of the couple's income, 0.0 to 1.0. */
    val incomeShare: Double,
    val coupleShareCents: Long,
    val allowanceCents: Long,
    val spentCents: Long,
) {
    val balanceCents: Long get() = allowanceCents - spentCents
}

data class MonthSummary(
    val incomeCents: Long,
    val groups: List<GroupLine>,
    val extraPlannedCents: Long,
    val extraActualCents: Long,
    val people: List<PersonLine>,
    val coupleActualCents: Long,
    val byPaymentMethod: Map<Long?, Pair<Long, Long>>,
    /** Planned to actual per card, only for expenses linked to a card. */
    val byCard: Map<Long, Pair<Long, Long>>,
    val byCategory: Map<Long?, Pair<Long, Long>>,
) {
    val plannedCents: Long get() = groups.sumOf { it.plannedCents }
    val actualCents: Long get() = groups.sumOf { it.actualCents }
    val recommendedCents: Long get() = groups.sumOf { it.recommendedCents }

    /** Income minus what the budget actually spent (extras are outside the budget). */
    val surplusCents: Long get() = incomeCents - actualCents

    val savingsRate: Double
        get() {
            if (incomeCents == 0L) return 0.0
            val invested = groups.first { it.group == BudgetGroup.INVESTMENT }.actualCents
            return (invested + surplusCents).toDouble() / incomeCents
        }

    val totalAllowanceCents: Long get() = people.sumOf { it.allowanceCents }
    val totalPersonalSpentCents: Long get() = people.sumOf { it.spentCents }
}

/**
 * Same rules as the "Controle Financeiro 2026" spreadsheet:
 * - recommended tithe = income x tithe %, the other groups = (income - tithe) x group %
 * - planned variable = couple variable expenses + allowances (each person's items use their allowance)
 * - actual = paid on day 30 + paid on day 15
 * - couple expenses are split between people proportionally to their income (equal split when unknown)
 */
object BudgetCalculator {

    fun resolvedPlanned(expense: ExpenseEntity, incomeCents: Long, settings: BudgetSettingsEntity): Long =
        if (expense.autoPlanned && expense.block == ExpenseBlock.TITHE) percentOf(incomeCents, settings.titheBp)
        else expense.plannedCents

    fun actual(expense: ExpenseEntity): Long = expense.paidDay30Cents + expense.paidDay15Cents

    fun summarize(
        incomes: List<IncomeEntity>,
        allowances: List<AllowanceEntity>,
        expenses: List<ExpenseEntity>,
        people: List<PersonEntity>,
        settings: BudgetSettingsEntity,
    ): MonthSummary {
        val income = incomes.sumOf { it.amountCents }
        val afterTithe = income - percentOf(income, settings.titheBp)
        fun planned(block: ExpenseBlock) =
            expenses.filter { it.block == block }.sumOf { resolvedPlanned(it, income, settings) }
        fun actual(block: ExpenseBlock) = expenses.filter { it.block == block }.sumOf { actual(it) }

        val allowanceTotal = allowances.sumOf { it.amountCents }
        val groups = listOf(
            GroupLine(BudgetGroup.TITHE, percentOf(income, settings.titheBp), planned(ExpenseBlock.TITHE), actual(ExpenseBlock.TITHE)),
            GroupLine(BudgetGroup.FIXED, percentOf(afterTithe, settings.fixedBp), planned(ExpenseBlock.FIXED), actual(ExpenseBlock.FIXED)),
            GroupLine(
                BudgetGroup.VARIABLE,
                percentOf(afterTithe, settings.variableBp),
                planned(ExpenseBlock.COUPLE) + allowanceTotal,
                actual(ExpenseBlock.COUPLE) + actual(ExpenseBlock.PERSONAL),
            ),
            GroupLine(BudgetGroup.INVESTMENT, percentOf(afterTithe, settings.investmentBp), planned(ExpenseBlock.INVESTMENT), actual(ExpenseBlock.INVESTMENT)),
        )

        val budgetActual = groups.sumOf { it.actualCents }
        val extraActual = actual(ExpenseBlock.EXTRA)
        val personalSpent = expenses.filter { it.block == ExpenseBlock.PERSONAL }.groupBy { it.personId }
            .mapValues { (_, list) -> list.sumOf { actual(it) } }
        val coupleActual = budgetActual + extraActual - personalSpent.values.sum()

        val incomeByPerson = incomes.filter { it.personId != null }.groupBy { it.personId }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        val personIncomeTotal = people.sumOf { incomeByPerson[it.id] ?: 0L }
        val lines = people.map { person ->
            val personIncome = incomeByPerson[person.id] ?: 0L
            val share = when {
                people.isEmpty() -> 0.0
                personIncomeTotal == 0L -> 1.0 / people.size
                else -> personIncome.toDouble() / personIncomeTotal
            }
            PersonLine(
                person = person,
                incomeCents = personIncome,
                incomeShare = share,
                coupleShareCents = Math.round(coupleActual * share),
                allowanceCents = allowances.firstOrNull { it.personId == person.id }?.amountCents ?: 0L,
                spentCents = personalSpent[person.id] ?: 0L,
            )
        }

        return MonthSummary(
            incomeCents = income,
            groups = groups,
            extraPlannedCents = planned(ExpenseBlock.EXTRA),
            extraActualCents = extraActual,
            people = lines,
            coupleActualCents = coupleActual,
            byPaymentMethod = expenses.groupBy { it.paymentMethodId }.mapValues { (_, list) ->
                list.sumOf { resolvedPlanned(it, income, settings) } to list.sumOf { actual(it) }
            },
            byCard = expenses.filter { it.cardId != null }.groupBy { it.cardId!! }.mapValues { (_, list) ->
                list.sumOf { resolvedPlanned(it, income, settings) } to list.sumOf { actual(it) }
            },
            byCategory = expenses.groupBy { it.categoryId }.mapValues { (_, list) ->
                list.sumOf { resolvedPlanned(it, income, settings) } to list.sumOf { actual(it) }
            },
        )
    }

    fun percentOf(cents: Long, basisPoints: Int): Long = Math.round(cents * basisPoints / 10_000.0)
}
