package com.controlefinanceiro.app.ui.vacation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.VacationEntity
import com.controlefinanceiro.app.domain.Vacation
import com.controlefinanceiro.app.ui.common.MONTH_NAMES
import com.controlefinanceiro.app.ui.common.shortName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class VacationUiState(
    val vacations: List<VacationEntity> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val months: List<MonthEntity> = emptyList(),
)

/** What a vacation pays, from what was saved. */
fun VacationEntity.result(): Vacation.Result = Vacation.calculate(
    Vacation.Input(grossCents, daysTaken, daysSold, dependents, advance13th, startDate?.let(LocalDate::ofEpochDay)),
)

class VacationViewModel(private val userId: Long, private val repo: FinanceRepository) : ViewModel() {
    val state: StateFlow<VacationUiState> = combine(
        repo.observeVacations(userId), repo.observePeople(userId), repo.observeMonths(userId),
    ) { vacations, people, months -> VacationUiState(vacations, people, months) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VacationUiState())

    /** A short message for the screen (e.g. after posting the pay as income). */
    val message = MutableStateFlow<String?>(null)

    fun save(vacation: VacationEntity) = viewModelScope.launch { repo.saveVacation(vacation.copy(userId = userId)) }
    fun delete(vacation: VacationEntity) = viewModelScope.launch { repo.deleteVacation(vacation) }

    /** Saves and posts the net pay as income in the month it is paid (and the 13th advance apart). */
    fun post(vacation: VacationEntity) = viewModelScope.launch {
        val r = vacation.result()
        val payDay = r.payBy ?: return@launch
        val id = repo.saveVacation(vacation.copy(userId = userId))
        val who = state.value.people.firstOrNull { it.id == vacation.personId }?.let { " ${shortName(it.name)}" }.orEmpty()
        val lines = listOf("Férias$who" to r.netCents - r.advance13thCents, "13º 1ª parcela$who" to r.advance13thCents)
        val ok = repo.postVacation(vacation.copy(id = id, userId = userId), userId, payDay.year, payDay.monthValue, lines)
        val month = "${MONTH_NAMES[payDay.monthValue - 1]} ${payDay.year}"
        message.value = if (ok) "Lançado como renda em $month" else "Crie o mês de $month primeiro"
    }
}
