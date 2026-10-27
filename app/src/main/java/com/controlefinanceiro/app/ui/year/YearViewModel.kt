package com.controlefinanceiro.app.ui.year

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.domain.BudgetCalculator
import com.controlefinanceiro.app.domain.MonthSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class PersonYear(val person: PersonEntity, val allowanceCents: Long, val spentCents: Long)

data class YearUiState(
    val year: Int = LocalDate.now().year,
    val months: List<Pair<MonthEntity, MonthSummary>> = emptyList(),
    val people: List<PersonYear> = emptyList(),
    val categories: List<Pair<String, Long>> = emptyList(),
) {
    val incomeCents get() = months.sumOf { it.second.incomeCents }
    val plannedCents get() = months.sumOf { it.second.plannedCents }
    val actualCents get() = months.sumOf { it.second.actualCents }
    val extraCents get() = months.sumOf { it.second.extraActualCents }
    val surplusCents get() = incomeCents - actualCents
}

@OptIn(ExperimentalCoroutinesApi::class)
class YearViewModel(private val userId: Long, private val repo: FinanceRepository) : ViewModel() {
    private val year = MutableStateFlow(LocalDate.now().year)

    private val reference = combine(
        repo.observePeople(userId), repo.observeCategories(userId), repo.observeSettings(userId),
    ) { people, categories, settings -> Triple(people, categories, settings ?: BudgetSettingsEntity(userId = userId)) }

    val state: StateFlow<YearUiState> = year.flatMapLatest { y ->
        combine(
            repo.observeYear(userId, y),
            repo.observeYearIncomes(userId, y),
            repo.observeYearAllowances(userId, y),
            repo.observeYearExpenses(userId, y),
            reference,
        ) { months, incomes, allowances, expenses, (people, categories, settings) ->
            val summaries = months.map { month ->
                month to BudgetCalculator.summarize(
                    incomes.filter { it.monthId == month.id },
                    allowances.filter { it.monthId == month.id },
                    expenses.filter { it.monthId == month.id },
                    people,
                    settings,
                )
            }
            val peopleYear = people.map { person ->
                val lines = summaries.mapNotNull { (_, s) -> s.people.firstOrNull { it.person.id == person.id } }
                PersonYear(person, lines.sumOf { it.allowanceCents }, lines.sumOf { it.spentCents })
            }
            val byCategory = expenses.groupBy { it.categoryId }
                .map { (id, list) -> (categories.firstOrNull { it.id == id }?.name ?: "Sem categoria") to list.sumOf { BudgetCalculator.actual(it) } }
                .filter { it.second != 0L }
                .sortedByDescending { it.second }
            YearUiState(y, summaries, peopleYear, byCategory)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearUiState())

    fun step(delta: Int) {
        year.value += delta
    }
}
