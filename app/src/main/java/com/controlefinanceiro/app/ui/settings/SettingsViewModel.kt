package com.controlefinanceiro.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.AuthRepository
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.SessionStore
import com.controlefinanceiro.app.data.ThemeMode
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.CardEntity
import com.controlefinanceiro.app.data.db.CategoryEntity
import com.controlefinanceiro.app.data.db.PaymentMethodEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val people: List<PersonEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val paymentMethods: List<PaymentMethodEntity> = emptyList(),
    val cards: List<CardEntity> = emptyList(),
    val budget: BudgetSettingsEntity? = null,
)

class SettingsViewModel(
    private val userId: Long,
    private val repo: FinanceRepository,
    private val auth: AuthRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val methodsAndCards = combine(repo.observePaymentMethods(userId), repo.observeCards(userId)) { methods, cards -> methods to cards }

    val state: StateFlow<SettingsUiState> = combine(
        session.themeMode,
        repo.observePeople(userId),
        repo.observeCategories(userId),
        methodsAndCards,
        repo.observeSettings(userId),
    ) { theme, people, categories, (methods, cards), budget ->
        SettingsUiState(theme, people, categories, methods, cards, budget ?: BudgetSettingsEntity(userId = userId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setTheme(mode: ThemeMode) = run { session.setTheme(mode) }

    fun addPerson(name: String) = run { if (name.isNotBlank()) repo.addPerson(userId, name, state.value.people.size) }
    fun deletePerson(person: PersonEntity) = run { repo.deletePerson(person) }

    fun addCategory(name: String) = run { if (name.isNotBlank()) repo.addCategory(userId, name) }
    fun deleteCategory(category: CategoryEntity) = run { repo.deleteCategory(category) }

    fun addPaymentMethod(name: String) = run { if (name.isNotBlank()) repo.addPaymentMethod(userId, name) }
    fun deletePaymentMethod(method: PaymentMethodEntity) = run { repo.deletePaymentMethod(method) }

    fun addCard(name: String) = run { if (name.isNotBlank()) repo.addCard(userId, name) }
    fun deleteCard(card: CardEntity) = run { repo.deleteCard(card) }

    fun saveBudget(settings: BudgetSettingsEntity) = run { repo.saveSettings(settings) }

    fun logout() = run { auth.logout() }
}
