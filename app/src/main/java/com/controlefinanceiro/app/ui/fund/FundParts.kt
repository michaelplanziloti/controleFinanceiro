package com.controlefinanceiro.app.ui.fund

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.db.CardEntity
import com.controlefinanceiro.app.data.db.CategoryEntity
import com.controlefinanceiro.app.data.db.FundExpenseEntity
import com.controlefinanceiro.app.data.db.FundIncomeEntity
import com.controlefinanceiro.app.data.db.PaymentMethodEntity
import com.controlefinanceiro.app.data.sheets.formatDate
import com.controlefinanceiro.app.ui.common.AmountField
import com.controlefinanceiro.app.ui.common.BudgetBar
import com.controlefinanceiro.app.ui.common.DateField
import com.controlefinanceiro.app.ui.common.DropdownField
import com.controlefinanceiro.app.ui.common.LabelValue
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.MoneyText
import com.controlefinanceiro.app.ui.common.Pill
import com.controlefinanceiro.app.ui.common.SectionCard
import com.controlefinanceiro.app.ui.common.dateDigits
import com.controlefinanceiro.app.ui.common.parseDateDigits
import com.controlefinanceiro.app.ui.common.usageColor
import com.controlefinanceiro.app.ui.month.budgetStatusColor
import com.controlefinanceiro.app.ui.theme.LocalBlockColors
import com.controlefinanceiro.app.ui.theme.LocalHeroColors

/** Lists the spending editor picks from. */
data class FundReference(
    val categories: List<CategoryEntity> = emptyList(),
    val paymentMethods: List<PaymentMethodEntity> = emptyList(),
    val cards: List<CardEntity> = emptyList(),
)

/** A line of what the vacation or PLR itself pays (e.g. "Férias líquidas"); tapping it shows its calculation. */
data class PayLine(val label: String, val detail: String, val cents: Long)

/** The money of a tab (pay plus extra money) against what it is spent on. */
data class FundTotals(val pay: List<PayLine>, val incomes: List<FundIncomeEntity>, val expenses: List<FundExpenseEntity>) {
    val moneyCents get() = pay.sumOf { it.cents } + incomes.sumOf { it.amountCents }
    val plannedCents get() = expenses.sumOf { it.plannedCents }
    val spentCents get() = expenses.sumOf { it.paidCents }
    val leftCents get() = moneyCents - spentCents

    /** What is left once everything planned is paid (more where it already went over). */
    val expectedLeftCents get() = moneyCents - expenses.sumOf { maxOf(it.plannedCents, it.paidCents) }
}

/** Top bar like the month's: menu, previous, the picked one (tap to choose), next, and "+" for a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundTopBar(
    title: String,
    options: List<Pair<Long, String>>,
    onPick: (Long) -> Unit,
    onStep: (Int) -> Unit,
    onNew: () -> Unit,
    onMenu: () -> Unit,
    newLabel: String,
) {
    var open by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Menu") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onStep(-1) }, enabled = options.size > 1) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anterior") }
                Box(Modifier.weight(1f, fill = false)) {
                    Row(
                        Modifier.clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = options.isNotEmpty()) { open = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (options.isNotEmpty()) Icon(Icons.Default.KeyboardArrowDown, null)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        options.forEach { (id, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(id) }) }
                    }
                }
                IconButton(onClick = { onStep(1) }, enabled = options.size > 1) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo") }
            }
        },
        actions = {
            FilledTonalIconButton(onClick = onNew, modifier = Modifier.padding(end = 8.dp)) { Icon(Icons.Default.Add, newLabel) }
        },
    )
}

/** The black card: what is left of the money, and money, planned and spent. */
@Composable
fun FundHero(label: String, totals: FundTotals, status: String?, dates: String?) {
    val hero = LocalHeroColors.current
    Column(Modifier.fillMaxWidth().background(hero.brush, MaterialTheme.shapes.extraLarge).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = hero.muted)
            status?.let { Pill(it, hero.content, container = hero.tile) }
        }
        Spacer(Modifier.height(4.dp))
        MoneyText(totals.leftCents, style = MaterialTheme.typography.displaySmall, color = hero.content)
        dates?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = hero.muted) }
        if (totals.moneyCents > 0) {
            Spacer(Modifier.height(12.dp))
            BudgetBar(totals.spentCents, totals.moneyCents, usageColor(totals.spentCents, totals.moneyCents), trackColor = hero.tile, height = 6.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Gastou ${Money.formatPercent(totals.spentCents.toDouble() / totals.moneyCents)} do dinheiro" +
                    if (totals.expectedLeftCents != totals.leftCents) " · fechamento previsto ${Money.format(totals.expectedLeftCents)}" else "",
                style = MaterialTheme.typography.bodySmall, color = hero.muted,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Dinheiro" to totals.moneyCents, "Previsto" to totals.plannedCents, "Gasto" to totals.spentCents).forEach { (l, cents) ->
                LabelValue(
                    l, cents, Modifier.weight(1f).background(hero.tile, MaterialTheme.shapes.medium).padding(horizontal = 12.dp, vertical = 10.dp),
                    labelColor = hero.muted, valueColor = hero.content,
                )
            }
        }
    }
}

/** Where the money comes from: the pay (tap for its calculation) and the extra money added. */
@Composable
fun FundMoneyCard(title: String, totals: FundTotals, onPay: () -> Unit, onAddIncome: () -> Unit, onEditIncome: (FundIncomeEntity) -> Unit) {
    SectionCard(title = title, subtitle = "Total ${Money.format(totals.moneyCents)}", action = { TextButton(onClick = onAddIncome) { Text("Adicionar") } }) {
        totals.pay.forEach { line -> MoneyRow(line.label, line.detail, line.cents, onPay) }
        totals.incomes.forEach { income -> MoneyRow(income.source, "Dinheiro extra", income.amountCents) { onEditIncome(income) } }
    }
}

@Composable
private fun MoneyRow(label: String, detail: String, cents: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoneyText(cents, style = MaterialTheme.typography.titleSmall)
    }
}

/** What the money pays for, like the month's entries: planned, paid, and a tap to mark it paid. */
@Composable
fun FundExpensesCard(
    title: String,
    emptyText: String,
    expenses: List<FundExpenseEntity>,
    reference: FundReference,
    onAdd: () -> Unit,
    onEdit: (FundExpenseEntity) -> Unit,
    onTogglePaid: (FundExpenseEntity) -> Unit,
) {
    val totalPlanned = expenses.sumOf { it.plannedCents }
    val totalPaid = expenses.sumOf { it.paidCents }
    SectionCard(
        title = title,
        subtitle = if (expenses.isEmpty()) null else "${Money.format(totalPaid)} pagos de ${Money.format(totalPlanned)} previstos",
        action = { TextButton(onClick = onAdd) { Text("Adicionar") } },
    ) {
        if (expenses.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        expenses.forEach { e ->
            val color = budgetStatusColor(e.paidCents, e.plannedCents)
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onEdit(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onTogglePaid(e) }) {
                    Icon(
                        if (e.isPaid) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        if (e.isPaid) "Marcar como não pago" else "Marcar como pago",
                        tint = if (e.isPaid) LocalBlockColors.current.investment else MaterialTheme.colorScheme.outline,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(e.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            reference.categories.firstOrNull { it.id == e.categoryId }?.name,
                            reference.cards.firstOrNull { it.id == e.cardId }?.name ?: reference.paymentMethods.firstOrNull { it.id == e.paymentMethodId }?.name,
                            e.date?.let(::formatDate),
                        ).joinToString(" · ").ifEmpty { "Sem categoria" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(e.paidCents, style = MaterialTheme.typography.titleSmall, color = if (e.paidCents > 0) color else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("de ${Money.format(e.plannedCents)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Spending per category: the larger of planned and paid, biggest first. */
@Composable
fun FundCategoryCard(expenses: List<FundExpenseEntity>, categories: List<CategoryEntity>) {
    val rows = expenses.groupBy { it.categoryId }
        .map { (id, list) -> (categories.firstOrNull { it.id == id }?.name ?: "Sem categoria") to list.sumOf { maxOf(it.plannedCents, it.paidCents) } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    if (rows.isEmpty()) return
    val total = rows.sumOf { it.second }
    val color = LocalBlockColors.current.couple
    SectionCard(title = "Por categoria", subtitle = "Previsto ou gasto, o que for maior") {
        rows.forEachIndexed { i, (name, cents) ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Row {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(Money.formatPercent(cents.toDouble() / total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(Money.format(cents), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            BudgetBar(cents, rows.first().second, color, height = 6.dp)
        }
    }
}

/** Adds or edits extra money (e.g. "Guardado para a viagem"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundIncomeSheet(initial: FundIncomeEntity, onSave: (FundIncomeEntity) -> Unit, onDelete: ((FundIncomeEntity) -> Unit)?, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf(initial.source) }
    var amount by remember { mutableStateOf(Money.toInput(initial.amountCents)) }
    val valid = source.isNotBlank() && Money.parse(amount)?.let { it != 0L } == true
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (initial.id == 0L) "Dinheiro extra" else "Editar dinheiro extra", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("De onde vem (ex.: guardado para a viagem)") }, singleLine = true)
            AmountField("Valor", amount, { amount = it })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) OutlinedButton(onClick = { onDelete(initial) }, Modifier.weight(1f)) { Text("Excluir") }
                Button(onClick = { onSave(initial.copy(source = source.trim(), amountCents = Money.parse(amount) ?: 0)) }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Salvar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Adds or edits something the money pays for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundExpenseSheet(
    initial: FundExpenseEntity,
    reference: FundReference,
    onSave: (FundExpenseEntity) -> Unit,
    onDelete: ((FundExpenseEntity) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var description by remember { mutableStateOf(initial.description) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var methodId by remember { mutableStateOf(initial.paymentMethodId) }
    var cardId by remember { mutableStateOf(initial.cardId) }
    var planned by remember { mutableStateOf(Money.toInput(initial.plannedCents)) }
    var paid by remember { mutableStateOf(Money.toInput(initial.paidCents)) }
    var isPaid by remember { mutableStateOf(initial.isPaid) }
    var date by remember { mutableStateOf(dateDigits(initial.date)) }
    val dateOk = date.isEmpty() || parseDateDigits(date) != null
    val valid = description.isNotBlank() && Money.parse(planned) != null && Money.parse(paid) != null && dateOk

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial.id == 0L) "Novo gasto" else "Editar gasto", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descrição (ex.: passagens)") }, singleLine = true)
            DropdownField("Categoria", reference.categories, reference.categories.firstOrNull { it.id == categoryId }, { it.name }, { categoryId = it?.id })
            DropdownField("Forma de pagamento", reference.paymentMethods, reference.paymentMethods.firstOrNull { it.id == methodId }, { it.name }, { methodId = it?.id })
            DropdownField("Cartão", reference.cards, reference.cards.firstOrNull { it.id == cardId }, { it.name }, { cardId = it?.id })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField("Previsto", planned, { planned = it }, Modifier.weight(1f))
                AmountField("Pago", paid, { paid = it }, Modifier.weight(1f))
            }
            DateField("Data (opcional)", date, { date = it }, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
                Text("Pago")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) OutlinedButton(onClick = { onDelete(initial) }, Modifier.weight(1f)) { Text("Excluir") }
                Button(
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val plannedCents = Money.parse(planned) ?: 0
                        val paidCents = Money.parse(paid) ?: 0
                        onSave(
                            initial.copy(
                                description = description.trim(), categoryId = categoryId, paymentMethodId = methodId, cardId = cardId,
                                plannedCents = plannedCents,
                                // Marked as paid without a value: it was paid as planned.
                                paidCents = if (isPaid && paidCents == 0L) plannedCents else paidCents,
                                isPaid = isPaid, date = parseDateDigits(date),
                            )
                        )
                    },
                ) { Text("Salvar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Shown when the tab has nothing yet: what it is for and the button to start. */
@Composable
fun FundEmpty(text: String, button: String, onClick: () -> Unit) {
    SectionCard {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
    }
}

/** The money and spending actions both tabs share. */
@Composable
fun FundSheets(
    editingIncome: FundIncomeEntity?,
    editingExpense: FundExpenseEntity?,
    reference: FundReference,
    onSaveIncome: (FundIncomeEntity) -> Unit,
    onDeleteIncome: (FundIncomeEntity) -> Unit,
    onSaveExpense: (FundExpenseEntity) -> Unit,
    onDeleteExpense: (FundExpenseEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    editingIncome?.let { income ->
        FundIncomeSheet(income, { onSaveIncome(it); onDismiss() }, if (income.id == 0L) null else { i -> onDeleteIncome(i); onDismiss() }, onDismiss)
    }
    editingExpense?.let { expense ->
        FundExpenseSheet(expense, reference, { onSaveExpense(it); onDismiss() }, if (expense.id == 0L) null else { e -> onDeleteExpense(e); onDismiss() }, onDismiss)
    }
}

/** Value shown for a duration: "Faltam 12 dias", "Em andamento" or "Já foram"/"Já recebido". */
fun countdown(from: java.time.LocalDate?, to: java.time.LocalDate?, pastLabel: String): String? {
    val today = java.time.LocalDate.now()
    return when {
        from == null -> null
        to != null && today.isAfter(to) -> pastLabel
        today.isBefore(from) -> (from.toEpochDay() - today.toEpochDay()).let { if (it == 1L) "Falta 1 dia" else "Faltam $it dias" }
        else -> if (to != null) "Em andamento" else pastLabel
    }
}
