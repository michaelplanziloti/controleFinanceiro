package com.controlefinanceiro.app.ui.fund

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.db.FundExpenseEntity
import com.controlefinanceiro.app.data.db.FundIncomeEntity
import com.controlefinanceiro.app.data.db.FundKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** What the Férias and PLR tabs share: their extra money and spending, and the lists to pick from. */
abstract class FundViewModel(protected val userId: Long, protected val repo: FinanceRepository) : ViewModel() {
    protected val reference: Flow<FundReference> = combine(
        repo.observeCategories(userId), repo.observePaymentMethods(userId), repo.observeCards(userId),
    ) { categories, methods, cards -> FundReference(categories, methods, cards) }

    /** Extra money and spending of one vacation or PLR (empty when there is none). */
    protected fun rows(kind: FundKind, ownerId: Long?): Flow<Pair<List<FundIncomeEntity>, List<FundExpenseEntity>>> =
        if (ownerId == null) flowOf(emptyList<FundIncomeEntity>() to emptyList())
        else combine(repo.observeFundIncomes(kind, ownerId), repo.observeFundExpenses(kind, ownerId)) { i, e -> i to e }

    /** A short message for the screen (e.g. after posting the pay as income). */
    val message = MutableStateFlow<String?>(null)

    fun saveIncome(income: FundIncomeEntity) = viewModelScope.launch { repo.saveFundIncome(income) }
    fun deleteIncome(income: FundIncomeEntity) = viewModelScope.launch { repo.deleteFundIncome(income) }
    fun saveExpense(expense: FundExpenseEntity) = viewModelScope.launch { repo.saveFundExpense(expense) }
    fun deleteExpense(expense: FundExpenseEntity) = viewModelScope.launch { repo.deleteFundExpense(expense) }

    /** Marks paid with its planned value, or back to unpaid (keeping the value typed). */
    fun togglePaid(expense: FundExpenseEntity) = viewModelScope.launch {
        repo.saveFundExpense(
            if (expense.isPaid) expense.copy(isPaid = false)
            else expense.copy(isPaid = true, paidCents = if (expense.paidCents == 0L) expense.plannedCents else expense.paidCents),
        )
    }
}
