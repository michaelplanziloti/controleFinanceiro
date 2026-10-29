package com.controlefinanceiro.app.ui.year

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.db.AllowanceEntity
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.IncomeEntity
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.domain.BudgetCalculator
import com.controlefinanceiro.app.domain.MonthSummary
import com.controlefinanceiro.app.domain.Projection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class PersonYear(val person: PersonEntity, val allowanceCents: Long, val spentCents: Long)

/**
 * One calendar month of the year. A month after the current one is [future]: its bills are not
 * paid yet, so it counts by what is planned. A [projected] month was not created yet and is
 * estimated from the latest month (see [Projection]).
 */
data class MonthPoint(
    /** 1..12 */
    val month: Int,
    val monthId: Long? = null,
    val incomeCents: Long = 0,
    /** Spent inside the budget (tithe, fixed, variable, investments), like the month screen. */
    val actualCents: Long = 0,
    val investedCents: Long = 0,
    val extraCents: Long = 0,
    /** What the budget will have spent once everything planned is paid. */
    val expectedSpentCents: Long = 0,
    val expectedInvestedCents: Long = 0,
    /** Per block: spent, or for a month still to come, the larger of planned and spent. Extras included. */
    val byBlock: Map<ExpenseBlock, Long> = emptyMap(),
    val future: Boolean = false,
    val projected: Boolean = false,
) {
    val exists get() = monthId != null
    val hasData get() = exists || projected

    /** Spending shown in the charts: what was spent, or for a month still to come, what is expected. */
    val spentCents get() = if (future) expectedSpentCents else actualCents
    val surplusCents get() = incomeCents - spentCents

    /** Invested plus what was left, over the income. */
    val savingsRate: Double?
        get() = if (!hasData || incomeCents <= 0) null
        else ((if (future) expectedInvestedCents else investedCents) + surplusCents).toDouble() / incomeCents
}

data class YearUiState(
    val year: Int = LocalDate.now().year,
    val months: List<Pair<MonthEntity, MonthSummary>> = emptyList(),
    /** Always 12, January first. */
    val points: List<MonthPoint> = (1..12).map { MonthPoint(it) },
    /** The year before, for comparison. */
    val previous: List<MonthPoint> = (1..12).map { MonthPoint(it) },
    val people: List<PersonYear> = emptyList(),
    val categories: List<Pair<String, Long>> = emptyList(),
    val cards: List<Pair<String, Long>> = emptyList(),
) {
    /** Months that already happened: what really took place. */
    private val done get() = points.filter { it.hasData && !it.future }
    val incomeCents get() = done.sumOf { it.incomeCents }
    val actualCents get() = done.sumOf { it.actualCents }
    val investedCents get() = done.sumOf { it.investedCents }
    val surplusCents get() = incomeCents - actualCents
    val hasData get() = points.any { it.hasData }
    val hasFuture get() = points.any { it.hasData && it.future }
    val allFuture get() = hasData && done.isEmpty()

    /** The surplus so far plus what the months still to come should leave. */
    val projectedSurplusCents get() = points.filter { it.hasData }.sumOf { it.surplusCents }

    /** Invested plus what was left, over the income of the months so far (same rule as the month). */
    val savingsRate: Double? get() = if (incomeCents > 0) (investedCents + surplusCents).toDouble() / incomeCents else null

    val byBlock: Map<ExpenseBlock, Long>
        get() = ExpenseBlock.entries.associateWith { block -> points.sumOf { it.byBlock[block] ?: 0 } }.filterValues { it > 0 }

    /** Index of the first month still to come, where charts switch to the projection; null when none. */
    val firstFuture: Int? get() = points.indexOfFirst { it.future && it.hasData }.takeIf { it >= 0 }

    val cumulativeSurplus: List<Long?> get() = cumulative(points)
    val previousCumulative: List<Long?> get() = cumulative(previous)
    val hasPrevious get() = previous.any { it.hasData && !it.future }

    /**
     * Surplus of the year before over the same months as [surplusCents] (all of it for a past
     * year), or null when that year has no data.
     */
    val previousSameTimeCents: Long?
        get() {
            if (!hasPrevious) return null
            val months = done.map { it.month }.toSet()
            return previous.filter { it.month in months && it.hasData }.sumOf { it.surplusCents }
        }

    private companion object {
        /**
         * Surplus added up month by month. Months before the first and after the last with data are
         * null; a month missing in between keeps the total so far.
         */
        fun cumulative(points: List<MonthPoint>): List<Long?> {
            val first = points.indexOfFirst { it.hasData }
            val last = points.indexOfLast { it.hasData }
            var sum = 0L
            return points.mapIndexed { i, p -> if (first < 0 || i < first || i > last) null else { sum += p.surplusCents; sum } }
        }
    }
}

class YearViewModel(private val userId: Long, private val repo: FinanceRepository) : ViewModel() {
    private val year = MutableStateFlow(LocalDate.now().year)

    private data class Reference(
        val people: List<PersonEntity>,
        val categories: Map<Long, String>,
        val cards: Map<Long, String>,
        val settings: BudgetSettingsEntity,
    )

    private data class Data(
        val months: List<MonthEntity>,
        val incomes: List<IncomeEntity>,
        val allowances: List<AllowanceEntity>,
        val expenses: List<ExpenseEntity>,
    )

    private val reference = combine(
        repo.observePeople(userId), repo.observeCategories(userId), repo.observeCards(userId), repo.observeSettings(userId),
    ) { people, categories, cards, settings ->
        Reference(people, categories.associate { it.id to it.name }, cards.associate { it.id to it.name }, settings ?: BudgetSettingsEntity(userId = userId))
    }

    // Every month of the user: the projection starts from the latest one, and the year before is compared.
    private val data = combine(
        repo.observeMonths(userId), repo.observeAllIncomes(userId), repo.observeAllAllowances(userId), repo.observeAllExpenses(userId),
    ) { months, incomes, allowances, expenses -> Data(months, incomes, allowances, expenses) }

    val state: StateFlow<YearUiState> = combine(year, data, reference) { y, d, ref -> build(y, d, ref) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearUiState())

    private fun build(y: Int, d: Data, ref: Reference): YearUiState {
        val incomes = d.incomes.groupBy { it.monthId }
        val allowances = d.allowances.groupBy { it.monthId }
        val expenses = d.expenses.groupBy { it.monthId }
        fun summarize(monthId: Long, exp: List<ExpenseEntity> = expenses[monthId].orEmpty()) =
            BudgetCalculator.summarize(incomes[monthId].orEmpty(), allowances[monthId].orEmpty(), exp, ref.people, ref.settings)

        val yearMonths = d.months.filter { it.year == y }.sortedBy { it.month }
        val summaries = yearMonths.map { it to summarize(it.id) }
        val yearExpenses = yearMonths.flatMap { expenses[it.id].orEmpty() }

        val peopleYear = ref.people.map { person ->
            val lines = summaries.mapNotNull { (_, s) -> s.people.firstOrNull { it.person.id == person.id } }
            PersonYear(person, lines.sumOf { it.allowanceCents }, lines.sumOf { it.spentCents })
        }
        val byCategory = yearExpenses.groupBy { it.categoryId }
            .map { (id, list) -> (ref.categories[id] ?: "Sem categoria") to list.sumOf { BudgetCalculator.actual(it) } }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
        val byCard = yearExpenses.filter { it.cardId != null }.groupBy { it.cardId }
            .mapNotNull { (id, list) -> ref.cards[id]?.let { it to list.sumOf { e -> BudgetCalculator.actual(e) } } }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
        return YearUiState(y, summaries, points(y, d, ref, ::summarize), points(y - 1, d, ref, ::summarize), peopleYear, byCategory, byCard)
    }

    /** The 12 months of [year]: created ones as they are, later ones not created yet projected. */
    private fun points(year: Int, d: Data, ref: Reference, summarize: (Long, List<ExpenseEntity>) -> MonthSummary): List<MonthPoint> {
        val today = LocalDate.now()
        val todayKey = today.year * 100 + today.monthValue
        val byKey = d.months.associateBy { it.year * 100 + it.month }
        val sorted = d.months.sortedBy { it.year * 100 + it.month }
        val expenses = d.expenses.groupBy { it.monthId }
        return (1..12).map { m ->
            val key = year * 100 + m
            val future = key > todayKey
            val month = byKey[key]
            if (month != null) {
                val rows = expenses[month.id].orEmpty()
                val summary = summarize(month.id, rows)
                return@map point(m, month.id, rows, summary, ref, future, projected = false)
            }
            // Only months still to come are projected, up to five years ahead.
            if (!future || year > today.year + 5) return@map MonthPoint(m)
            val template = sorted.lastOrNull { it.year * 100 + it.month < key } ?: return@map MonthPoint(m)
            val ahead = Projection.monthsBetween(template.year, template.month, year, m)
            val rows = Projection.expenses(expenses[template.id].orEmpty(), ahead)
            point(m, null, rows, summarize(template.id, rows), ref, future = true, projected = true)
        }
    }

    private fun point(m: Int, monthId: Long?, rows: List<ExpenseEntity>, summary: MonthSummary, ref: Reference, future: Boolean, projected: Boolean): MonthPoint {
        fun planned(e: ExpenseEntity) = BudgetCalculator.resolvedPlanned(e, summary.incomeCents, ref.settings)
        val invested = rows.filter { it.block == ExpenseBlock.INVESTMENT }
        return MonthPoint(
            month = m,
            monthId = monthId,
            incomeCents = summary.incomeCents,
            actualCents = summary.actualCents,
            investedCents = invested.sumOf { BudgetCalculator.actual(it) },
            extraCents = if (future) rows.filter { it.block == ExpenseBlock.EXTRA }.sumOf { maxOf(planned(it), BudgetCalculator.actual(it)) } else summary.extraActualCents,
            expectedSpentCents = summary.expectedSpentCents,
            expectedInvestedCents = invested.sumOf { maxOf(planned(it), BudgetCalculator.actual(it)) },
            byBlock = rows.groupBy { it.block }.mapValues { (_, list) ->
                list.sumOf { if (future) maxOf(planned(it), BudgetCalculator.actual(it)) else BudgetCalculator.actual(it) }
            },
            future = future,
            projected = projected,
        )
    }

    fun step(delta: Int) {
        year.value += delta
    }
}
