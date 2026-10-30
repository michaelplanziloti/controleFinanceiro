package com.controlefinanceiro.app.ui.plr

import androidx.lifecycle.viewModelScope
import com.controlefinanceiro.app.data.FinanceRepository
import com.controlefinanceiro.app.data.db.FundKind
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.PlrEntity
import com.controlefinanceiro.app.domain.Plr
import com.controlefinanceiro.app.ui.common.MONTH_NAMES
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.shortName
import com.controlefinanceiro.app.ui.fund.FundReference
import com.controlefinanceiro.app.ui.fund.FundTotals
import com.controlefinanceiro.app.ui.fund.FundViewModel
import com.controlefinanceiro.app.ui.fund.PayLine
import com.controlefinanceiro.app.ui.fund.withTithe
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

data class PlrUiState(
    val plrs: List<PlrEntity> = emptyList(),
    val plr: PlrEntity? = null,
    val totals: FundTotals = FundTotals(emptyList(), emptyList(), emptyList()),
    val people: List<PersonEntity> = emptyList(),
    val months: List<MonthEntity> = emptyList(),
    val reference: FundReference = FundReference(),
)

/**
 * PLR of the same person paid earlier in the same year (registered here), which the yearly table
 * adds to this one, plus what was typed as received outside the app.
 */
fun PlrEntity.earlierInYear(all: List<PlrEntity>): Long {
    val date = payDate ?: return earlierCents
    val year = LocalDate.ofEpochDay(date).year
    return earlierCents + all.filter { other ->
        other.id != id && other.personId == personId && other.payDate != null &&
            LocalDate.ofEpochDay(other.payDate).year == year &&
            (other.payDate < date || (other.payDate == date && other.createdAt < createdAt))
    }.sumOf { it.grossCents }
}

fun PlrEntity.result(all: List<PlrEntity>): Plr.Result = Plr.calculate(grossCents, earlierInYear(all), otherDiscountsCents)

/**
 * Tithe of the PLR and its extra money. Once the PLR is posted as income in a month, that month's
 * tithe already counts it, so here only the extra money is left.
 */
fun FundTotals.plrTithe(plr: PlrEntity, r: Plr.Result, titheBp: Int): FundTotals =
    if (plr.postedMonthId == null) withTithe(titheBp, r.netCents)
    else withTithe(titheBp, 0, "o da PLR vai no dízimo do mês em que foi lançada")

@OptIn(ExperimentalCoroutinesApi::class)
class PlrViewModel(userId: Long, repo: FinanceRepository) : FundViewModel(userId, repo) {
    private val selectedId = MutableStateFlow<Long?>(null)
    private val plrs = repo.observePlrs(userId)

    /** The picked PLR, or the next one to be paid, or the latest one. */
    private val active = combine(plrs, selectedId) { list, id ->
        val today = LocalDate.now().toEpochDay()
        list.firstOrNull { it.id == id }
            ?: list.filter { it.payDate != null && it.payDate >= today }.minByOrNull { it.payDate!! }
            ?: list.firstOrNull()
    }

    private val detail = combine(active, plrs, titheBp) { plr, all, bp -> Triple(plr, all, bp) }.flatMapLatest { (plr, all, bp) ->
        rows(FundKind.PLR, plr?.id).map { (incomes, expenses) ->
            val r = plr?.result(all)
            val pay = r?.let {
                listOf(PayLine("PLR líquida", "Bruto ${Money.format(it.grossCents)} · IR ${Money.format(it.taxCents)} · toque para ver o cálculo", it.netCents))
            }.orEmpty()
            val totals = FundTotals(pay, incomes, expenses)
            plr to if (plr == null || r == null) totals else totals.plrTithe(plr, r, bp)
        }
    }

    val state: StateFlow<PlrUiState> = combine(
        plrs, detail, repo.observePeople(userId), repo.observeMonths(userId), reference,
    ) { list, (plr, totals), people, months, ref -> PlrUiState(list, plr, totals, people, months, ref) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlrUiState())

    override val payLabel get() = state.value.plr?.title?.ifBlank { null } ?: "PLR"

    fun select(id: Long) {
        selectedId.value = id
    }

    /** Moves to the PLR before (-1) or after (+1), in date order. */
    fun step(delta: Int) {
        val ordered = state.value.plrs.sortedWith(compareBy<PlrEntity> { it.payDate ?: Long.MAX_VALUE }.thenBy { it.createdAt })
        val index = ordered.indexOfFirst { it.id == state.value.plr?.id }
        ordered.getOrNull(index + delta)?.let { select(it.id) }
    }

    fun save(plr: PlrEntity) = viewModelScope.launch { select(repo.savePlr(plr.copy(userId = userId))) }

    fun delete(plr: PlrEntity) = viewModelScope.launch {
        selectedId.value = null
        repo.deletePlr(plr)
    }

    /** Saves and posts the net value as income in the month it is paid. */
    fun post(plr: PlrEntity) = viewModelScope.launch {
        val date = plr.payDate?.let(LocalDate::ofEpochDay) ?: return@launch
        val id = repo.savePlr(plr.copy(userId = userId))
        val saved = plr.copy(id = id, userId = userId)
        val net = saved.result(state.value.plrs.filter { it.id != id } + saved).netCents
        val who = state.value.people.firstOrNull { it.id == plr.personId }?.let { " ${shortName(it.name)}" }.orEmpty()
        val ok = repo.postPlr(saved, userId, date.year, date.monthValue, "${plr.title.ifBlank { "PLR" }}$who", net)
        val month = "${MONTH_NAMES[date.monthValue - 1]} ${date.year}"
        message.value = if (ok) "Lançado como renda em $month" else "Crie o mês de $month primeiro"
    }
}
