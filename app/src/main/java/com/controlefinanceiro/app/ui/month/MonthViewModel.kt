package com.controlefinanceiro.app.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.SessionStore
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
import com.controlefinanceiro.app.data.db.SubcategoryEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import com.controlefinanceiro.app.domain.BudgetCalculator
import com.controlefinanceiro.app.domain.Installments
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

/** An item or entry ([title], [amountCents]) that took the month's planned or spent total past the income. */
data class BudgetAlert(val title: String, val amountCents: Long, val incomeCents: Long, val lines: List<Line>) {
    /** [name] is "Previsto" or "Realizado", as in the "Renda do mês" card. */
    data class Line(val name: String, val totalCents: Long)
}

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

    /** Search in every month; results only load while something is typed. */
    val searchQuery = MutableStateFlow("")
    val searchResults: StateFlow<List<SearchHit>> = searchQuery.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList())
        else combine(months, repo.observeAllExpenses(userId), repo.observeAllItems(userId)) { monthList, expenses, items ->
            searchMonths(query, monthList, expenses, items)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Saves an entry; a new one in installments also goes to the later months that exist. */
    fun saveExpense(expense: ExpenseEntity) = viewModelScope.launch {
        val state = state.value
        val alert = state.detail?.let { detail ->
            // A new entry gets a placeholder id so it can be told apart from the others.
            val saved = if (expense.id == 0L) expense.copy(id = -1) else expense
            val after = detail.expenses.filter { it.id != saved.id } + saved
            overBudget(state, detail.expenses, after, saved.description, BudgetCalculator.actual(saved).takeIf { it > 0 } ?: saved.plannedCents)
        }
        repo.saveExpense(expense, newPurchase = expense.id == 0L)
        if (alert != null) budgetAlert.value = alert
    }
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

    /** Set when the last saved item went over a budget; the screen shows it until dismissed. */
    val budgetAlert = MutableStateFlow<BudgetAlert?>(null)

    fun dismissBudgetAlert() {
        budgetAlert.value = null
    }

    /** Saves an item in the open month; a chosen subcategory decides the row it adds up in. */
    fun saveItem(subcategoryId: Long?, item: SubcategoryItemEntity, previous: SubcategoryItemEntity?) = viewModelScope.launch {
        val before = state.value
        val monthId = before.detail?.month?.id ?: return@launch
        val rowId = subcategoryId?.let { repo.rowFor(it, monthId) } ?: item.expenseId
        val alert = overBudget(before, rowId, subcategoryId, item.copy(expenseId = rowId), previous)
        if (previous != null && previous.expenseId != rowId) {
            repo.deleteItem(previous)
            repo.saveItem(item.copy(id = 0, expenseId = rowId))
        } else {
            repo.saveItem(item.copy(expenseId = rowId), newPurchase = previous == null)
        }
        if (alert != null) budgetAlert.value = alert
    }

    /** [overBudget] for an item: its row gains the item's value (and loses the previous one when edited). */
    private fun overBudget(
        state: MonthUiState, rowId: Long, subcategoryId: Long?, item: SubcategoryItemEntity, previous: SubcategoryItemEntity?,
    ): BudgetAlert? {
        val detail = state.detail ?: return null
        // A row created just now for the subcategory is not in the state yet.
        val row = detail.expenses.firstOrNull { it.id == rowId }
            ?: state.reference.subcategories.firstOrNull { it.id == subcategoryId }?.let { sub ->
                ExpenseEntity(
                    id = rowId, monthId = detail.month.id, block = sub.block, description = sub.name, plannedCents = sub.plannedCents,
                    subcategoryId = sub.id, personId = if (sub.block == ExpenseBlock.PERSONAL) sub.personId ?: state.reference.people.firstOrNull()?.id else null,
                )
            }
            ?: return null
        fun ExpenseEntity.plus(cents: Long) = copy(paidDay15Cents = paidDay15Cents + cents)
        val beforeRows = detail.expenses.filter { it.id != rowId } + row
        val afterRows = beforeRows.map {
            var e = it
            if (previous != null && e.id == previous.expenseId) e = e.plus(-previous.amountCents)
            if (e.id == rowId) e = e.plus(item.amountCents)
            e
        }
        return overBudget(state, beforeRows, afterRows, Installments.sheetDescription(item), item.amountCents)
    }

    /**
     * Like the "Renda do mês" card: the month's planned total (Previsto) or spent total (Realizado)
     * that was within the income with [beforeRows] and passes it with [afterRows]. [title] and
     * [amountCents] describe what caused it.
     */
    private fun overBudget(
        state: MonthUiState, beforeRows: List<ExpenseEntity>, afterRows: List<ExpenseEntity>, title: String, amountCents: Long,
    ): BudgetAlert? {
        val detail = state.detail ?: return null
        val settings = state.reference.settings ?: BudgetSettingsEntity(userId = userId)
        val before = BudgetCalculator.summarize(detail.incomes, detail.allowances, beforeRows, state.reference.people, settings)
        val after = BudgetCalculator.summarize(detail.incomes, detail.allowances, afterRows, state.reference.people, settings)
        val income = after.incomeCents
        if (income <= 0) return null
        val lines = listOfNotNull(
            BudgetAlert.Line("Previsto", after.plannedCents).takeIf { before.plannedCents <= income && after.plannedCents > income },
            BudgetAlert.Line("Realizado", after.actualCents).takeIf { before.actualCents <= income && after.actualCents > income },
        )
        return lines.takeIf { it.isNotEmpty() }?.let { BudgetAlert(title, amountCents, income, it) }
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
