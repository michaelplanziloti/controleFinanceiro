package com.controlefinanceiro.app.ui.plr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlefinanceiro.app.data.db.FundExpenseEntity
import com.controlefinanceiro.app.data.db.FundIncomeEntity
import com.controlefinanceiro.app.data.db.FundKind
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.PlrEntity
import com.controlefinanceiro.app.data.sheets.formatDate
import com.controlefinanceiro.app.ui.common.AmountField
import com.controlefinanceiro.app.ui.common.DateField
import com.controlefinanceiro.app.ui.common.DropdownField
import com.controlefinanceiro.app.ui.common.MONTH_NAMES
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.MoneyText
import com.controlefinanceiro.app.ui.common.dateDigits
import com.controlefinanceiro.app.ui.common.parseDateDigits
import com.controlefinanceiro.app.ui.common.shortName
import com.controlefinanceiro.app.ui.fund.FundCategoryCard
import com.controlefinanceiro.app.ui.fund.FundEmpty
import com.controlefinanceiro.app.ui.fund.FundExpensesCard
import com.controlefinanceiro.app.ui.fund.FundHero
import com.controlefinanceiro.app.ui.fund.FundMoneyCard
import com.controlefinanceiro.app.ui.fund.FundSheets
import com.controlefinanceiro.app.ui.fund.FundTopBar
import com.controlefinanceiro.app.ui.fund.countdown
import com.controlefinanceiro.app.ui.theme.LocalHeroColors
import com.controlefinanceiro.app.ui.theme.LocalMoneyColors
import java.time.LocalDate

/** The "PLR" tab: the PLR's net value (its tax calculated), extra money, and what it pays for. */
@Composable
fun PlrTab(viewModel: PlrViewModel, contentPadding: PaddingValues, onMenu: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlrEntity?>(null) }
    var editingIncome by remember { mutableStateOf<FundIncomeEntity?>(null) }
    var editingExpense by remember { mutableStateOf<FundExpenseEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.message.value = null }
    }
    fun label(p: PlrEntity): String {
        val who = state.people.firstOrNull { it.id == p.personId }?.let { " de ${shortName(it.name)}" }.orEmpty()
        val date = p.payDate?.let(LocalDate::ofEpochDay)
        return p.title.ifBlank { "PLR" } + who + (date?.let { " · ${MONTH_NAMES[it.monthValue - 1].take(3).lowercase()}/${it.year % 100}" } ?: "")
    }
    fun newPlr() {
        val last = state.plrs.firstOrNull()
        editing = PlrEntity(userId = 0, personId = last?.personId ?: state.people.firstOrNull()?.id, grossCents = 0)
    }
    val plr = state.plr

    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FundTopBar(
                title = plr?.let(::label) ?: "PLR",
                options = state.plrs.map { it.id to label(it) },
                onPick = viewModel::select, onStep = viewModel::step, onNew = ::newPlr, onMenu = onMenu, newLabel = "Nova PLR",
            )
        },
        floatingActionButton = {
            if (plr != null) {
                ExtendedFloatingActionButton(
                    onClick = { editingExpense = FundExpenseEntity(kind = FundKind.PLR, ownerId = plr.id, description = "") },
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
            if (plr == null) {
                item {
                    FundEmpty(
                        "Aqui fica a PLR como se fosse um mês: o valor líquido (o app calcula o IR pela tabela própria da PLR) e no que " +
                            "você vai usar esse dinheiro, separado do orçamento dos meses.",
                        "Cadastrar PLR", ::newPlr,
                    )
                }
                return@LazyColumn
            }
            val date = plr.payDate?.let(LocalDate::ofEpochDay)
            item("hero") {
                FundHero(
                    "Sobra da PLR", state.totals,
                    countdown(date, null, "Já recebida"),
                    date?.let { "Paga em ${formatDate(it.toEpochDay())}" } ?: "Sem data de pagamento",
                )
            }
            item("money") {
                FundMoneyCard(
                    "Dinheiro da PLR", state.totals, onPay = { editing = plr },
                    onAddIncome = { editingIncome = FundIncomeEntity(kind = FundKind.PLR, ownerId = plr.id, source = "", amountCents = 0) },
                    onEditIncome = { editingIncome = it },
                )
            }
            item("expenses") {
                FundExpensesCard(
                    "No que vai a PLR", "Quitar uma dívida, investir, uma compra grande... Cada gasto desconta do dinheiro da PLR.",
                    state.totals.expenses, state.reference,
                    onAdd = { editingExpense = FundExpenseEntity(kind = FundKind.PLR, ownerId = plr.id, description = "") },
                    onEdit = { editingExpense = it }, onTogglePaid = viewModel::togglePaid,
                )
            }
            item("categories") { FundCategoryCard(state.totals.expenses, state.reference.categories) }
        }
    }

    editing?.let { initial ->
        PlrEditorSheet(
            initial = initial,
            all = state.plrs,
            people = state.people,
            postedIn = state.months.firstOrNull { it.id == initial.postedMonthId }?.let { "${MONTH_NAMES[it.month - 1]} ${it.year}" },
            onSave = { viewModel.save(it); editing = null },
            onPost = { viewModel.post(it); editing = null },
            onDelete = if (initial.id == 0L) null else { p -> viewModel.delete(p); editing = null },
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

/** Adds or edits a PLR; its tax and net value show below the fields as they change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlrEditorSheet(
    initial: PlrEntity,
    all: List<PlrEntity>,
    people: List<PersonEntity>,
    postedIn: String?,
    onSave: (PlrEntity) -> Unit,
    onPost: (PlrEntity) -> Unit,
    onDelete: ((PlrEntity) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var personId by remember { mutableStateOf(initial.personId) }
    var title by remember { mutableStateOf(initial.title) }
    var gross by remember { mutableStateOf(Money.toInput(initial.grossCents)) }
    var date by remember { mutableStateOf(dateDigits(initial.payDate)) }
    var earlier by remember { mutableStateOf(Money.toInput(initial.earlierCents)) }
    var others by remember { mutableStateOf(Money.toInput(initial.otherDiscountsCents)) }
    var note by remember { mutableStateOf(initial.note) }
    var confirmPost by remember { mutableStateOf(false) }

    val dateOk = date.isEmpty() || parseDateDigits(date) != null
    val plr = initial.copy(
        personId = personId, title = title.trim().ifEmpty { "PLR" }, grossCents = Money.parse(gross) ?: 0, payDate = parseDateDigits(date),
        earlierCents = Money.parse(earlier) ?: 0, otherDiscountsCents = Money.parse(others) ?: 0, note = note.trim(),
    )
    val others2 = all.filter { it.id != plr.id }
    val earlierTotal = plr.earlierInYear(others2)
    val r = plr.result(others2)
    val valid = plr.grossCents > 0 && dateOk && Money.parse(earlier) != null && Money.parse(others) != null
    val money = LocalMoneyColors.current
    val hero = LocalHeroColors.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial.id == 0L) "Nova PLR" else "Editar PLR", style = MaterialTheme.typography.titleLarge)
            if (people.isNotEmpty()) {
                DropdownField("De quem", people, people.firstOrNull { it.id == personId }, { it.name }, { personId = it?.id }, noneLabel = "Ninguém em especial")
            }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Nome (ex.: PLR 1º semestre)") }, singleLine = true)
            AmountField("Valor bruto", gross, { gross = it })
            DateField("Data do pagamento", date, { date = it }, Modifier.fillMaxWidth())
            AmountField("PLR recebida antes neste ano, fora do app", earlier, { earlier = it })
            AmountField("Outros descontos (ex.: sindicato)", others, { others = it })

            if (plr.grossCents > 0) {
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)) {
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(hero.brush).padding(16.dp)) {
                        Text("Cai na conta", style = MaterialTheme.typography.labelMedium, color = hero.muted)
                        MoneyText(r.netCents, style = MaterialTheme.typography.headlineMedium, color = hero.content)
                        Text("IR de ${Money.formatPercent(r.effectiveRate)} do valor", style = MaterialTheme.typography.bodySmall, color = hero.muted)
                    }
                    Spacer(Modifier.height(14.dp))
                    Line("PLR bruta", r.grossCents)
                    Line("Imposto de renda", -r.taxCents, if (r.taxCents == 0L) "isento" else "faixa de ${Money.formatPercent(r.bandRate)} da tabela da PLR", money.negative)
                    if (r.otherDiscountsCents > 0) Line("Outros descontos", -r.otherDiscountsCents, null, money.negative)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Line("Líquido", r.netCents, bold = true)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A PLR não paga INSS. O IR é retido à parte do salário, pela tabela anual da PLR (isenta até R$ 8.214,40 no ano)" +
                            if (earlierTotal > 0) {
                                ". Como já foram ${Money.format(earlierTotal)} de PLR neste ano, esta é tributada somando as duas."
                            } else ".",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Observação (opcional)") })
            val payDay = plr.payDate?.let(LocalDate::ofEpochDay)
            if (payDay != null && plr.grossCents > 0) {
                FilledTonalButton(onClick = { confirmPost = true }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
                    Text(if (postedIn != null) "Já lançado em $postedIn" else "Lançar como renda em ${MONTH_NAMES[payDay.monthValue - 1]}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) OutlinedButton(onClick = { onDelete(initial) }, Modifier.weight(1f)) { Text("Excluir") }
                Button(onClick = { onSave(plr) }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Salvar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    val payDay = plr.payDate?.let(LocalDate::ofEpochDay)
    if (confirmPost && payDay != null) {
        val month = MONTH_NAMES[payDay.monthValue - 1]
        AlertDialog(
            onDismissRequest = { confirmPost = false },
            title = { Text("Lançar em $month?") },
            text = {
                Text(
                    "Entra como renda em $month ${payDay.year}: ${Money.format(r.netCents)}." +
                        if (postedIn != null) " Já foi lançado em $postedIn uma vez; vai entrar de novo." else "",
                )
            },
            confirmButton = { TextButton(onClick = { confirmPost = false; onPost(plr) }) { Text("Lançar") } },
            dismissButton = { TextButton(onClick = { confirmPost = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Line(label: String, cents: Long, detail: String? = null, color: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
            detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        MoneyText(cents, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = color)
    }
}
