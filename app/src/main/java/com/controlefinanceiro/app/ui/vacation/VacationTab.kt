package com.controlefinanceiro.app.ui.vacation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlefinanceiro.app.data.db.FundExpenseEntity
import com.controlefinanceiro.app.data.db.FundIncomeEntity
import com.controlefinanceiro.app.data.db.FundKind
import com.controlefinanceiro.app.data.db.VacationEntity
import com.controlefinanceiro.app.data.sheets.formatDate
import com.controlefinanceiro.app.ui.common.MONTH_NAMES
import com.controlefinanceiro.app.ui.common.shortName
import com.controlefinanceiro.app.ui.fund.FundCategoryCard
import com.controlefinanceiro.app.ui.fund.FundEmpty
import com.controlefinanceiro.app.ui.fund.FundExpensesCard
import com.controlefinanceiro.app.ui.fund.FundHero
import com.controlefinanceiro.app.ui.fund.FundMoneyCard
import com.controlefinanceiro.app.ui.fund.FundSheets
import com.controlefinanceiro.app.ui.fund.FundTopBar
import com.controlefinanceiro.app.ui.fund.countdown

/**
 * The "Férias" tab: like a month, but only for the vacation money. Its pay (calculated from the
 * salary, see the editor), extra money for it, and what it is spent on, apart from the months.
 */
@Composable
fun VacationTab(viewModel: VacationViewModel, contentPadding: PaddingValues, onMenu: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<VacationEntity?>(null) }
    var editingIncome by remember { mutableStateOf<FundIncomeEntity?>(null) }
    var editingExpense by remember { mutableStateOf<FundExpenseEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.message.value = null }
    }
    fun label(v: VacationEntity): String {
        val who = state.people.firstOrNull { it.id == v.personId }?.let { " de ${shortName(it.name)}" }.orEmpty()
        val start = v.result().start
        return "Férias$who" + (start?.let { " · ${MONTH_NAMES[it.monthValue - 1].take(3).lowercase()}/${it.year % 100}" } ?: "")
    }
    fun newVacation() {
        // A new one starts from the last salary typed, for the same person.
        val last = state.vacations.firstOrNull()
        editing = VacationEntity(
            userId = 0, personId = last?.personId ?: state.people.firstOrNull()?.id, grossCents = last?.grossCents ?: 0,
            dependents = last?.dependents ?: 0,
        )
    }
    val vacation = state.vacation

    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FundTopBar(
                title = vacation?.let(::label) ?: "Férias",
                options = state.vacations.map { it.id to label(it) },
                onPick = viewModel::select, onStep = viewModel::step, onNew = ::newVacation, onMenu = onMenu, newLabel = "Novas férias",
            )
        },
        floatingActionButton = {
            if (vacation != null) {
                ExtendedFloatingActionButton(
                    onClick = { editingExpense = FundExpenseEntity(kind = FundKind.VACATION, ownerId = vacation.id, description = "") },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Adicionar gasto") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vacation == null) {
                item {
                    FundEmpty(
                        "Aqui ficam as férias como se fossem um mês: o dinheiro que entra (o app calcula as férias pelo salário bruto, " +
                            "os dias tirados e os vendidos) e os gastos da viagem, separados do orçamento dos meses.",
                        "Cadastrar férias", ::newVacation,
                    )
                }
                return@LazyColumn
            }
            val r = vacation.result()
            item("hero") {
                FundHero(
                    "Sobra das férias", state.totals,
                    countdown(r.start, r.end, "Já foram"),
                    if (r.start != null && r.end != null && r.back != null) {
                        "${formatDate(r.start.toEpochDay())} a ${formatDate(r.end.toEpochDay())} · volta ${formatDate(r.back.toEpochDay())}"
                    } else "Sem data definida",
                )
            }
            item("money") {
                FundMoneyCard(
                    "Dinheiro das férias", state.totals, onPay = { editing = vacation },
                    onAddIncome = { editingIncome = FundIncomeEntity(kind = FundKind.VACATION, ownerId = vacation.id, source = "", amountCents = 0) },
                    onEditIncome = { editingIncome = it },
                )
            }
            item("expenses") {
                FundExpensesCard(
                    "Gastos das férias", "Passagens, hospedagem, passeios... Cada gasto desconta do dinheiro das férias.",
                    state.totals.expenses, state.reference,
                    onAdd = { editingExpense = FundExpenseEntity(kind = FundKind.VACATION, ownerId = vacation.id, description = "") },
                    onEdit = { editingExpense = it }, onTogglePaid = viewModel::togglePaid,
                )
            }
            item("categories") { FundCategoryCard(state.totals.expenses, state.reference.categories) }
        }
    }

    editing?.let { initial ->
        VacationEditorSheet(
            initial = initial,
            people = state.people,
            postedIn = state.months.firstOrNull { it.id == initial.postedMonthId }?.let { "${MONTH_NAMES[it.month - 1]} ${it.year}" },
            onSave = { viewModel.save(it); editing = null },
            onPost = { viewModel.post(it); editing = null },
            onDelete = if (initial.id == 0L) null else { v -> viewModel.delete(v); editing = null },
            onDismiss = { editing = null },
        )
    }
    FundSheets(
        editingIncome, editingExpense, state.reference,
        onSaveIncome = viewModel::saveIncome, onDeleteIncome = viewModel::deleteIncome,
        onSaveExpense = viewModel::saveExpense, onDeleteExpense = viewModel::deleteExpense,
        onDismiss = { editingIncome = null; editingExpense = null },
    )
}
