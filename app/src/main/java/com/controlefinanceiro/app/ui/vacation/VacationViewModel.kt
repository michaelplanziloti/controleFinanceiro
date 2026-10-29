package com.controlefinanceiro.app.ui.vacation

import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.db.FundKind
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.VacationEntity
import com.controlefinanceiro.app.domain.Vacation
import com.controlefinanceiro.app.ui.common.MONTH_NAMES
import com.controlefinanceiro.app.ui.common.shortName
import com.controlefinanceiro.app.ui.fund.FundReference
import com.controlefinanceiro.app.ui.fund.FundTotals
import com.controlefinanceiro.app.ui.fund.FundViewModel
import com.controlefinanceiro.app.ui.fund.PayLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class VacationUiState(
    val vacations: List<VacationEntity> = emptyList(),
    /** The open vacation, its pay and what it is spent on. */
    val vacation: VacationEntity? = null,
    val totals: FundTotals = FundTotals(emptyList(), emptyList(), emptyList()),
    val people: List<PersonEntity> = emptyList(),
    val months: List<MonthEntity> = emptyList(),
    val reference: FundReference = FundReference(),
)

/** What a vacation pays, from what was saved. */
fun VacationEntity.result(): Vacation.Result = Vacation.calculate(
    Vacation.Input(grossCents, daysTaken, daysSold, dependents, advance13th, startDate?.let(LocalDate::ofEpochDay)),
)

/** Short name of the months that get money kept: "nov e dez". */
fun Vacation.Result.reserveMonths(): String =
    reserves.map { MONTH_NAMES[it.month.monthValue - 1].take(3).lowercase() }.distinct().joinToString(" e ").ifEmpty { "os meses das férias" }

/** The pay of a vacation as lines of the money card; what is kept for the months comes off, leaving what is free. */
fun VacationEntity.payLines(): List<PayLine> {
    val r = result()
    val days = "$daysTaken dias" + if (daysSold > 0) " + $daysSold vendidos" else ""
    return listOfNotNull(
        PayLine("Férias líquidas", "$days · toque para ver o cálculo", r.netCents - r.advance13thCents),
        PayLine("13º, 1ª parcela", "Adiantado com as férias", r.advance13thCents).takeIf { r.advance13thCents > 0 },
        PayLine("Guardar para os meses", "Vai para ${r.reserveMonths()}, no lugar do salário dos dias de férias", -r.reserveCents).takeIf { r.reserveCents > 0 },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class VacationViewModel(userId: Long, repo: FinanceRepository) : FundViewModel(userId, repo) {
    private val selectedId = MutableStateFlow<Long?>(null)
    private val vacations = repo.observeVacations(userId)

    /** The picked vacation, or the one going on or coming next, or the latest one. */
    private val active = combine(vacations, selectedId) { list, id ->
        val today = LocalDate.now().toEpochDay()
        list.firstOrNull { it.id == id }
            ?: list.filter { v -> v.startDate != null && v.startDate + v.daysTaken > today }.minByOrNull { it.startDate!! }
            ?: list.firstOrNull()
    }

    private val detail = active.flatMapLatest { vacation ->
        rows(FundKind.VACATION, vacation?.id).map { (incomes, expenses) -> vacation to FundTotals(vacation?.payLines().orEmpty(), incomes, expenses) }
    }

    val state: StateFlow<VacationUiState> = combine(
        vacations, detail, repo.observePeople(userId), repo.observeMonths(userId), reference,
    ) { list, (vacation, totals), people, months, ref -> VacationUiState(list, vacation, totals, people, months, ref) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VacationUiState())

    fun select(id: Long) {
        selectedId.value = id
    }

    /** Moves to the vacation before (-1) or after (+1), in date order. */
    fun step(delta: Int) {
        val ordered = state.value.vacations.sortedWith(compareBy<VacationEntity> { it.startDate ?: Long.MAX_VALUE }.thenBy { it.createdAt })
        val index = ordered.indexOfFirst { it.id == state.value.vacation?.id }
        ordered.getOrNull(index + delta)?.let { select(it.id) }
    }

    fun save(vacation: VacationEntity) = viewModelScope.launch { select(repo.saveVacation(vacation.copy(userId = userId))) }

    fun delete(vacation: VacationEntity) = viewModelScope.launch {
        selectedId.value = null
        repo.deleteVacation(vacation)
    }

    /**
     * Saves and puts the money kept for each month as income in that month, so its smaller salary
     * does not leave it short. The free part stays in the Férias tab.
     */
    fun post(vacation: VacationEntity) = viewModelScope.launch {
        val r = vacation.result()
        if (r.reserves.isEmpty()) return@launch
        val id = repo.saveVacation(vacation.copy(userId = userId))
        val who = state.value.people.firstOrNull { it.id == vacation.personId }?.let { " ${shortName(it.name)}" }.orEmpty()
        val lines = r.reserves.map { Triple(it.month, "Férias$who (${it.days} dias)", it.cents) }
        val missing = repo.postVacation(vacation.copy(id = id, userId = userId), userId, lines)
        fun name(m: java.time.YearMonth) = "${MONTH_NAMES[m.monthValue - 1]} ${m.year}"
        message.value = if (missing.isEmpty()) "Lançado em ${r.reserves.joinToString(" e ") { name(it.month) }}"
        else "Crie ${missing.joinToString(" e ") { name(it) }} primeiro"
    }
}
