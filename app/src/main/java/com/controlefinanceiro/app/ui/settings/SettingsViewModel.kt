package com.controlefinanceiro.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.AuthRepository
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.SessionStore
import com.controlefinanceiro.app.data.SheetsConfig
import com.controlefinanceiro.app.data.ThemeMode
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.CardEntity
import com.controlefinanceiro.app.data.db.CategoryEntity
import com.controlefinanceiro.app.data.db.PaymentMethodEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.SubcategoryEntity
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
    val subcategories: List<SubcategoryEntity> = emptyList(),
    val budget: BudgetSettingsEntity? = null,
    val sheets: SheetsConfig = SheetsConfig(),
    val sheetsStatus: String? = null,
)

class SettingsViewModel(
    private val userId: Long,
    private val repo: FinanceRepository,
    private val auth: AuthRepository,
    private val session: SessionStore,
    private val syncSheets: () -> Unit,
) : ViewModel() {

    private val methodsAndCards = combine(repo.observePaymentMethods(userId), repo.observeCards(userId), repo.observeSubcategories(userId)) { methods, cards, subs ->
        Triple(methods, cards, subs)
    }

    private val sheets = combine(session.sheetsConfig(userId), session.sheetsStatus(userId)) { config, status -> config to status }

    val state: StateFlow<SettingsUiState> = combine(
        combine(session.themeMode, repo.observePeople(userId), repo.observeCategories(userId)) { theme, people, categories ->
            Triple(theme, people, categories)
        },
        methodsAndCards,
        repo.observeSettings(userId),
        sheets,
    ) { (theme, people, categories), (methods, cards, subs), budget, (sheetsConfig, sheetsStatus) ->
        SettingsUiState(
            theme, people, categories, methods, cards, subs, budget ?: BudgetSettingsEntity(userId = userId),
            sheetsConfig, sheetsStatus,
        )
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

    fun saveSubcategory(sub: SubcategoryEntity) = run { repo.saveSubcategory(sub.copy(userId = userId)) }
    fun deleteSubcategory(sub: SubcategoryEntity) = run { repo.deleteSubcategory(sub) }

    fun saveBudget(settings: BudgetSettingsEntity) = run { repo.saveSettings(settings) }

    fun saveSheets(config: SheetsConfig) = run {
        session.setSheetsConfig(userId, config)
        session.setSheetsStatus(userId, if (config.enabled) "Conferindo a planilha..." else "Desligado")
        if (config.enabled) syncSheets()
    }

    fun syncSheetsNow() = run {
        session.setSheetsStatus(userId, "Conferindo a planilha...")
        syncSheets()
    }

    fun logout() = run { auth.logout() }
}
