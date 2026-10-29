package com.controlefinanceiro.app.ui.vacation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.VacationEntity
import com.controlefinanceiro.app.data.sheets.formatDate
import com.controlefinanceiro.app.domain.Vacation
import com.controlefinanceiro.app.ui.common.AmountField
import com.controlefinanceiro.app.ui.common.DateField
import com.controlefinanceiro.app.ui.common.DropdownField
import com.controlefinanceiro.app.ui.common.IconBadge
import com.controlefinanceiro.app.ui.common.MONTH_NAMES
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.MoneyText
import com.controlefinanceiro.app.ui.common.Pill
import com.controlefinanceiro.app.ui.common.SectionCard
import com.controlefinanceiro.app.ui.common.dateDigits
import com.controlefinanceiro.app.ui.common.parseDateDigits
import com.controlefinanceiro.app.ui.common.shortName
import com.controlefinanceiro.app.ui.theme.LocalHeroColors
import com.controlefinanceiro.app.ui.theme.LocalMoneyColors
import java.time.LocalDate

/** "Férias": every vacation with what it pays, and an editor that does the whole calculation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacationScreen(viewModel: VacationViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<VacationEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.message.value = null }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Férias", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // A new one starts from the last salary typed, for the same person.
                    val last = state.vacations.firstOrNull()
                    editing = VacationEntity(
                        userId = 0, personId = last?.personId ?: state.people.firstOrNull()?.id, grossCents = last?.grossCents ?: 0,
                        dependents = last?.dependents ?: 0,
                    )
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Novas férias") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.vacations.isEmpty()) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Outlined.BeachAccess, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Coloque o salário bruto, quando começam e quantos dias vai tirar e vender. O app calcula o 1/3, o abono, " +
                                    "o INSS, o IR e quanto cai na conta.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(state.vacations, key = { it.id }) { vacation ->
                VacationCard(vacation, state.people, state.months.firstOrNull { it.id == vacation.postedMonthId }?.let { "${MONTH_NAMES[it.month - 1]} ${it.year}" }) {
                    editing = vacation
                }
            }
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
}

/** One vacation in the list: when, how long, and what lands in the account. */
@Composable
private fun VacationCard(vacation: VacationEntity, people: List<PersonEntity>, postedIn: String?, onClick: () -> Unit) {
    val r = vacation.result()
    val hero = LocalHeroColors.current
    val today = LocalDate.now()
    val status = when {
        r.start == null -> "Sem data"
        r.back != null && !today.isBefore(r.back) -> "Já foram"
        !today.isBefore(r.start) -> "Em andamento"
        else -> "Faltam ${r.start.toEpochDay() - today.toEpochDay()} dias"
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(hero.brush).clickable(onClick = onClick).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                people.firstOrNull { it.id == vacation.personId }?.let { "Férias de ${shortName(it.name)}" } ?: "Férias",
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = hero.content,
            )
            Pill(status, hero.content, container = hero.tile)
        }
        Text(
            listOfNotNull(
                if (r.start != null && r.end != null) "${formatDate(r.start.toEpochDay())} a ${formatDate(r.end.toEpochDay())}" else null,
                "${vacation.daysTaken} dias" + if (vacation.daysSold > 0) " + ${vacation.daysSold} vendidos" else "",
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = hero.muted,
        )
        Spacer(Modifier.height(14.dp))
        Text("Cai na conta", style = MaterialTheme.typography.labelMedium, color = hero.muted)
        MoneyText(r.netCents, style = MaterialTheme.typography.headlineMedium, color = hero.content)
        Text(
            listOfNotNull(
                r.payBy?.let { "Pagamento até ${formatDate(it.toEpochDay())}" },
                postedIn?.let { "Lançado em $it" },
            ).joinToString(" · ").ifEmpty { "Defina a data para ver quando cai" },
            style = MaterialTheme.typography.bodySmall, color = hero.muted,
        )
    }
}

/** Adds or edits a vacation; the whole calculation shows below the fields as they change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VacationEditorSheet(
    initial: VacationEntity,
    people: List<PersonEntity>,
    postedIn: String?,
    onSave: (VacationEntity) -> Unit,
    onPost: (VacationEntity) -> Unit,
    onDelete: ((VacationEntity) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var personId by remember { mutableStateOf(initial.personId) }
    var gross by remember { mutableStateOf(Money.toInput(initial.grossCents)) }
    var start by remember { mutableStateOf(dateDigits(initial.startDate)) }
    var taken by remember { mutableStateOf(initial.daysTaken.toString()) }
    var sold by remember { mutableStateOf(initial.daysSold) }
    var dependents by remember { mutableStateOf(initial.dependents.toString()) }
    var advance by remember { mutableStateOf(initial.advance13th) }
    var note by remember { mutableStateOf(initial.note) }
    var confirmPost by remember { mutableStateOf(false) }

    val startDay = parseDateDigits(start)
    val dateOk = start.isEmpty() || startDay != null
    val vacation = initial.copy(
        personId = personId,
        grossCents = Money.parse(gross) ?: 0,
        startDate = startDay,
        daysTaken = taken.toIntOrNull() ?: 0,
        daysSold = sold,
        dependents = dependents.toIntOrNull() ?: 0,
        advance13th = advance,
        note = note.trim(),
    )
    val r = vacation.result()
    val valid = vacation.grossCents > 0 && vacation.daysTaken in 1..30 && dateOk

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial.id == 0L) "Novas férias" else "Editar férias", style = MaterialTheme.typography.titleLarge)
            if (people.isNotEmpty()) {
                DropdownField("De quem", people, people.firstOrNull { it.id == personId }, { it.name }, { personId = it?.id }, noneLabel = "Ninguém em especial")
            }
            AmountField("Salário bruto do mês", gross, { gross = it })
            DateField("Primeiro dia de férias", start, { start = it }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    taken, { taken = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Dias tirados") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = vacation.daysTaken !in 1..30,
                )
                OutlinedTextField(
                    dependents, { dependents = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Dependentes no IR") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text("Dias vendidos (abono)", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(0, 5, 10).forEachIndexed { i, days ->
                    SegmentedButton(
                        selected = sold == days,
                        onClick = { sold = days; if ((taken.toIntOrNull() ?: 0) + days > 30) taken = (30 - days).toString() },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                    ) { Text(if (days == 0) "Nenhum" else "$days dias") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Adiantar 1ª parcela do 13º")
                    Text("Metade do salário, junto com as férias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = advance, onCheckedChange = { advance = it })
            }

            if (vacation.grossCents > 0) Breakdown(vacation, r)

            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Observação (opcional)") })
            if (r.payBy != null && vacation.grossCents > 0) {
                FilledTonalButton(onClick = { confirmPost = true }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (postedIn != null) "Já lançado em $postedIn"
                        else "Lançar como renda em ${MONTH_NAMES[r.payBy.monthValue - 1]}",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) OutlinedButton(onClick = { onDelete(initial) }, Modifier.weight(1f)) { Text("Excluir") }
                Button(onClick = { onSave(vacation) }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Salvar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmPost && r.payBy != null) {
        val month = MONTH_NAMES[r.payBy.monthValue - 1]
        AlertDialog(
            onDismissRequest = { confirmPost = false },
            title = { Text("Lançar em $month?") },
            text = {
                Text(
                    "Entra como renda em $month ${r.payBy.year}: férias ${Money.format(r.netCents - r.advance13thCents)}" +
                        (if (r.advance13thCents > 0) " e 13º ${Money.format(r.advance13thCents)}" else "") + "." +
                        if (postedIn != null) " Já foi lançado em $postedIn uma vez; vai entrar de novo." else "",
                )
            },
            confirmButton = { TextButton(onClick = { confirmPost = false; onPost(vacation) }) { Text("Lançar") } },
            dismissButton = { TextButton(onClick = { confirmPost = false }) { Text("Cancelar") } },
        )
    }
}

/** Like a payslip: what is earned, what is discounted, and the dates that matter. */
@Composable
private fun Breakdown(vacation: VacationEntity, r: Vacation.Result) {
    val money = LocalMoneyColors.current
    val hero = LocalHeroColors.current
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(hero.brush).padding(16.dp)) {
            Text("Cai na conta", style = MaterialTheme.typography.labelMedium, color = hero.muted)
            MoneyText(r.netCents, style = MaterialTheme.typography.headlineMedium, color = hero.content)
            r.payBy?.let { Text("Até ${formatDate(it.toEpochDay())} (2 dias antes de começar)", style = MaterialTheme.typography.bodySmall, color = hero.muted) }
        }
        Spacer(Modifier.height(14.dp))
        Text("Proventos", style = MaterialTheme.typography.labelLarge)
        Line("Férias (${vacation.daysTaken} dias)", r.vacationCents)
        Line("1/3 de férias", r.vacationThirdCents)
        if (r.soldCents > 0) {
            Line("Abono (${vacation.daysSold} dias vendidos)", r.soldCents, "sem INSS e IR")
            Line("1/3 do abono", r.soldThirdCents, "sem INSS e IR")
        }
        if (r.advance13thCents > 0) Line("13º salário, 1ª parcela", r.advance13thCents, "descontos só em dezembro")
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Descontos", style = MaterialTheme.typography.labelLarge)
        Line("INSS", -r.inssCents, "sobre férias + 1/3", money.negative)
        Line("Imposto de renda", -r.irrfCents, if (r.irrfCents == 0L) "isento" else "sobre férias + 1/3 − INSS", money.negative)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Line("Bruto", r.grossTotalCents)
        Line("Líquido", r.netCents, bold = true)

        if (r.start != null && r.end != null && r.back != null) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Datas", style = MaterialTheme.typography.labelLarge)
            DateLine("Pagamento até", r.payBy)
            DateLine("Começa", r.start)
            DateLine("Último dia de férias", r.end)
            DateLine("Volta ao trabalho", r.back)
        }
        if (r.salaries.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Salário dos meses das férias", style = MaterialTheme.typography.labelLarge)
            Text(
                "As férias são pagas adiantadas, então o salário desses meses vem só com os dias trabalhados.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            r.salaries.forEach { s ->
                Line("${MONTH_NAMES[s.month.monthValue - 1]}: ${s.workedDays} dias trabalhados", s.grossCents, "bruto, ${s.vacationDays} dias de férias")
            }
        }
        if (r.warnings.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            r.warnings.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = money.warning)
                    Spacer(Modifier.width(8.dp))
                    Text(w, style = MaterialTheme.typography.bodySmall, color = money.warning)
                }
            }
        }
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

@Composable
private fun DateLine(label: String, date: LocalDate?) {
    if (date == null) return
    val weekday = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")[date.dayOfWeek.value - 1]
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$weekday, ${formatDate(date.toEpochDay())}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
