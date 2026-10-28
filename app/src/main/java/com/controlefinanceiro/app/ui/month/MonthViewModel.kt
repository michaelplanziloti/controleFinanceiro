package com.controlefinanceiro.app.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.SessionStore
import com.controlefinanceiro.app.data.db.AllowanceEntity
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.CardEntity
import com.controlefinanceiro.app.data.db.CategoryEntity
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.IncomeEntity
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.PaymentMethodEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.SubcategoryEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import com.controlefinanceiro.app.domain.BudgetCalculator
import com.controlefinanceiro.app.domain.MonthSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ReferenceData(
    val people: List<PersonEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val paymentMethods: List<PaymentMethodEntity> = emptyList(),
    val cards: List<CardEntity> = emptyList(),
    val settings: BudgetSettingsEntity? = null,
    val subcategories: List<SubcategoryEntity> = emptyList(),
)

data class MonthDetail(
    val month: MonthEntity,
    val incomes: List<IncomeEntity>,
    val allowances: List<AllowanceEntity>,
    val expenses: List<ExpenseEntity>,
    val items: List<SubcategoryItemEntity> = emptyList(),
)

data class MonthUiState(
    val loading: Boolean = true,
    val months: List<MonthEntity> = emptyList(),
    val detail: MonthDetail? = null,
    val reference: ReferenceData = ReferenceData(),
    val summary: MonthSummary? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(
    private val userId: Long,
    private val repo: FinanceRepository,
    private val session: SessionStore,
    private val syncSheets: suspend () -> Unit,
) : ViewModel() {

    val hideValues: StateFlow<Boolean> =
        session.hideValues(userId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleHideValues() = viewModelScope.launch { session.setHideValues(userId, !hideValues.value) }

    /** The overview cards in the user's order; cards missing from the saved order keep their default place at the end. */
    val cardOrder: StateFlow<List<String>> = session.cardOrder(userId)
        .map { saved -> saved.filter { it in DEFAULT_CARD_ORDER } + DEFAULT_CARD_ORDER.filterNot { it in saved } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_CARD_ORDER)

    fun saveCardOrder(order: List<String>) = viewModelScope.launch { session.setCardOrder(userId, order) }

    companion object {
        /** Keys of the cards at the top of the month, in their original order. */
        val DEFAULT_CARD_ORDER = listOf("overview", "cards", "tracker", "usage", "groups", "couple")
    }

    /** True while a pull-to-refresh check of the spreadsheet runs. */
    val refreshing = MutableStateFlow(false)

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        try {
            syncSheets()
        } finally {
            refreshing.value = false
        }
    }

    private val selectedMonthId = MutableStateFlow<Long?>(null)

    private val reference = combine(
        repo.observePeople(userId),
        repo.observeCategories(userId),
        repo.observePaymentMethods(userId),
        combine(repo.observeCards(userId), repo.observeSubcategories(userId)) { cards, subs -> cards to subs },
        repo.observeSettings(userId),
    ) { people, categories, methods, (cards, subs), settings -> ReferenceData(people, categories, methods, cards, settings, subs) }

    private val months = repo.observeMonths(userId)

    /** Selected month, or the one left open last time, or the current calendar month, or the most recent one. */
    private val activeMonth = combine(months, selectedMonthId, session.lastMonth(userId)) { list, selected, last ->
        val today = LocalDate.now()
        list.firstOrNull { it.id == selected }
            ?: list.firstOrNull { it.id == last }
            ?: list.firstOrNull { it.year == today.year && it.month == today.monthValue }
            ?: list.firstOrNull()
    }

    private val detail = activeMonth.flatMapLatest { month ->
        if (month == null) flowOf(null)
        else combine(
            repo.observeIncomes(month.id),
            repo.observeAllowances(month.id),
            repo.observeExpenses(month.id),
            repo.observeItems(month.id),
        ) { incomes, allowances, expenses, items -> MonthDetail(month, incomes, allowances, expenses, items) }
    }

    val state: StateFlow<MonthUiState> = combine(months, detail, reference) { monthList, detail, ref ->
        val settings = ref.settings ?: BudgetSettingsEntity(userId = userId)
        MonthUiState(
            loading = false,
            months = monthList,
            detail = detail,
            reference = ref.copy(settings = settings),
            summary = detail?.let { BudgetCalculator.summarize(it.incomes, it.allowances, it.expenses, ref.people, settings) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthUiState())

    fun selectMonth(id: Long) {
        selectedMonthId.value = id
        viewModelScope.launch { session.setLastMonth(userId, id) }
    }

    /** Moves to the neighbour month in the list; [step] is -1 (older) or +1 (newer). */
    fun step(step: Int) {
        val current = state.value
        val ordered = current.months.sortedBy { it.year * 100 + it.month }
        val index = ordered.indexOfFirst { it.id == current.detail?.month?.id }
        ordered.getOrNull(index + step)?.let { selectMonth(it.id) }
    }

    fun createMonth(year: Int, month: Int, copyPrevious: Boolean) = viewModelScope.launch {
        selectMonth(repo.createMonth(userId, year, month, copyPrevious))
    }

    fun deleteMonth(month: MonthEntity) = viewModelScope.launch {
        selectedMonthId.value = null
        repo.deleteMonth(month)
    }

    fun saveExpense(expense: ExpenseEntity) = viewModelScope.launch { repo.saveExpense(expense) }
    fun deleteExpense(expense: ExpenseEntity) = viewModelScope.launch { repo.deleteExpense(expense) }
    fun restoreExpense(expense: ExpenseEntity) = viewModelScope.launch { repo.restoreExpense(expense) }
    fun togglePaid(expense: ExpenseEntity) = viewModelScope.launch { repo.togglePaid(expense) }

    /** Marks every expense paid with its planned value on day 15 or day 30 (the other day is cleared). */
    fun payAll(expenses: List<ExpenseEntity>, onDay15: Boolean) = viewModelScope.launch {
        val withItems = state.value.detail?.items.orEmpty().map { it.expenseId }.toSet()
        repo.updateExpenses(
            expenses.map {
                // A row with items keeps the sum of its items; it is only marked as paid.
                if (it.id in withItems) return@map it.copy(isPaid = true)
                val planned = resolvedPlanned(it)
                it.copy(
                    paidDay15Cents = if (onDay15) planned else 0,
                    paidDay30Cents = if (onDay15) 0 else planned,
                    isPaid = true,
                )
            }
        )
    }

    /** Puts back expenses exactly as they were (undo of [payAll]). */
    fun restoreExpenses(expenses: List<ExpenseEntity>) = viewModelScope.launch { repo.updateExpenses(expenses) }

    fun saveSubcategory(sub: SubcategoryEntity) = viewModelScope.launch { repo.saveSubcategory(sub.copy(userId = userId)) }
    fun deleteSubcategory(sub: SubcategoryEntity) = viewModelScope.launch { repo.deleteSubcategory(sub) }
    fun setTracked(ids: List<Long>) = viewModelScope.launch { repo.setTracked(userId, ids) }

    /** Saves an item in the open month; a chosen subcategory decides the row it adds up in. */
    fun saveItem(subcategoryId: Long?, item: SubcategoryItemEntity, previous: SubcategoryItemEntity?) = viewModelScope.launch {
        val monthId = state.value.detail?.month?.id ?: return@launch
        val rowId = subcategoryId?.let { repo.rowFor(it, monthId) } ?: item.expenseId
        if (previous != null && previous.expenseId != rowId) {
            repo.deleteItem(previous)
            repo.saveItem(item.copy(id = 0, expenseId = rowId))
        } else {
            repo.saveItem(item.copy(expenseId = rowId))
        }
    }

    fun deleteItem(item: SubcategoryItemEntity) = viewModelScope.launch { repo.deleteItem(item) }
    fun restoreItem(item: SubcategoryItemEntity) = viewModelScope.launch { repo.saveItem(item) }

    fun saveIncome(income: IncomeEntity) = viewModelScope.launch { repo.saveIncome(income) }
    fun deleteIncome(income: IncomeEntity) = viewModelScope.launch { repo.deleteIncome(income) }

    fun setAllowance(personId: Long, amountCents: Long) = viewModelScope.launch {
        state.value.detail?.month?.let { repo.setAllowance(it.id, personId, amountCents) }
    }

    fun resolvedPlanned(expense: ExpenseEntity): Long {
        val s = state.value
        return BudgetCalculator.resolvedPlanned(
            expense, s.summary?.incomeCents ?: 0, s.reference.settings ?: BudgetSettingsEntity(userId = userId),
        )
    }
}
