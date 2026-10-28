package com.controlefinanceiro.app.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.db.CardEntity
import com.controlefinanceiro.app.data.db.CategoryEntity
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.PaymentMethodEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.SubcategoryEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import com.controlefinanceiro.app.data.sheets.formatDate
import com.controlefinanceiro.app.data.sheets.parseDate
import com.controlefinanceiro.app.domain.BudgetCalculator
import com.controlefinanceiro.app.ui.common.AmountField
import com.controlefinanceiro.app.ui.common.BudgetBar
import com.controlefinanceiro.app.ui.common.DropdownField
import com.controlefinanceiro.app.ui.common.IconBadge
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.MoneyText
import com.controlefinanceiro.app.ui.common.SectionCard
import com.controlefinanceiro.app.ui.common.groupShape
import com.controlefinanceiro.app.ui.theme.LocalMoneyColors
import java.time.LocalDate

/** Blue when a budget is hit exactly. */
private val ExactBlue = Color(0xFF5B9BF0)

/** Red above the target, blue exactly on it, green below it. */
@Composable
fun budgetStatusColor(spent: Long, planned: Long): Color {
    val money = LocalMoneyColors.current
    return when {
        planned <= 0 && spent <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        spent > planned -> money.negative
        spent == planned -> ExactBlue
        else -> money.positive
    }
}

fun budgetStatusText(spent: Long, planned: Long): String = when {
    spent > planned -> "Passou ${Money.format(spent - planned)}"
    spent == planned -> "Bateu a meta"
    else -> "Sobram ${Money.format(planned - spent)}"
}

/** The generic "+": what to add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEntrySheet(onItem: () -> Unit, onExpense: () -> Unit, onSubcategory: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Adicionar", style = MaterialTheme.typography.titleLarge)
            EntryOption(Icons.Outlined.ShoppingBasket, "Item de subcategoria", "Uma compra que soma no Mercado, na Gasolina...", onItem)
            EntryOption(Icons.Outlined.ReceiptLong, "Lançamento", "Uma conta em Fixos, Variáveis, Mesadas, Investimentos...", onExpense)
            EntryOption(Icons.Outlined.Category, "Nova subcategoria", "Um orçamento com meta, como Mercado", onSubcategory)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EntryOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Creates or edits a subcategory: name, monthly target, block and defaults for its rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubcategoryEditorSheet(
    initial: SubcategoryEntity,
    people: List<PersonEntity>,
    categories: List<CategoryEntity>,
    paymentMethods: List<PaymentMethodEntity>,
    cards: List<CardEntity>,
    onSave: (SubcategoryEntity) -> Unit,
    onDelete: ((SubcategoryEntity) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var planned by remember { mutableStateOf(Money.toInput(initial.plannedCents)) }
    var block by remember { mutableStateOf(initial.block) }
    var personId by remember { mutableStateOf(initial.personId ?: people.firstOrNull()?.id) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var methodId by remember { mutableStateOf(initial.paymentMethodId) }
    var cardId by remember { mutableStateOf(initial.cardId) }
    var inTracker by remember { mutableStateOf(initial.inTracker) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && Money.parse(planned) != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial.id == 0L) "Nova subcategoria" else "Editar subcategoria", style = MaterialTheme.typography.titleLarge)
            Text(
                "Cada mês ganha uma linha com essa meta no bloco escolhido. Os itens que você adicionar somam nela.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome (ex.: Mercado)") }, singleLine = true)
            AmountField("Meta do mês", planned, { planned = it })
            DropdownField(
                "Onde fica", ExpenseBlock.entries.filter { it != ExpenseBlock.TITHE }, block, { it.title() },
                { if (it != null) block = it }, noneLabel = null,
            )
            if (block == ExpenseBlock.PERSONAL) {
                DropdownField("Mesada de", people, people.firstOrNull { it.id == personId }, { it.name }, { personId = it?.id }, noneLabel = null)
            }
            DropdownField("Categoria", categories, categories.firstOrNull { it.id == categoryId }, { it.name }, { categoryId = it?.id })
            DropdownField("Forma de pagamento", paymentMethods, paymentMethods.firstOrNull { it.id == methodId }, { it.name }, { methodId = it?.id })
            DropdownField("Cartão", cards, cards.firstOrNull { it.id == cardId }, { it.name }, { cardId = it?.id })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar no acompanhamento")
                    Text("Card no topo do mês", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = inTracker, onCheckedChange = { inTracker = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) OutlinedButton(onClick = { confirmDelete = true }, Modifier.weight(1f)) { Text("Excluir") }
                Button(
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSave(
                            initial.copy(
                                name = name.trim(),
                                plannedCents = Money.parse(planned) ?: 0,
                                block = block,
                                personId = if (block == ExpenseBlock.PERSONAL) personId else null,
                                categoryId = categoryId,
                                paymentMethodId = methodId,
                                cardId = cardId,
                                inTracker = inTracker,
                            )
                        )
                    },
                ) { Text("Salvar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir ${initial.name}?") },
            text = { Text("As linhas e os itens dos meses continuam lá; só deixam de ser uma subcategoria.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(initial) }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/**
 * Adds or edits one purchase. [subcategoryId] is preselected; saving returns the chosen subcategory
 * (null keeps the item in the row it already is in).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorSheet(
    initial: SubcategoryItemEntity?,
    subcategoryId: Long?,
    subcategories: List<SubcategoryEntity>,
    year: Int,
    month: Int,
    onSave: (Long?, SubcategoryItemEntity) -> Unit,
    onDelete: ((SubcategoryItemEntity) -> Unit)?,
    onNewSubcategory: () -> Unit,
    onDismiss: () -> Unit,
    /** The item goes to a row already chosen (one without a subcategory), so none needs picking. */
    hasRow: Boolean = false,
) {
    val today = LocalDate.now()
    val defaultDate = if (today.year == year && today.monthValue == month) today else LocalDate.of(year, month, 1)
    var subId by remember { mutableStateOf(subcategoryId ?: subcategories.singleOrNull()?.id.takeIf { initial == null }) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var amount by remember { mutableStateOf(initial?.let { Money.toInput(it.amountCents) }.orEmpty()) }
    // An item saved without a date keeps no date; a new one starts on today (or the month's first day).
    var date by remember { mutableStateOf(if (initial != null) initial.date?.let(::formatDate).orEmpty() else formatDate(defaultDate.toEpochDay())) }
    var onDay15 by remember { mutableStateOf(initial?.onDay15 ?: (defaultDate.dayOfMonth <= 15)) }
    val dateOk = date.isBlank() || parseDate(date) != null
    val valid = (subId != null || initial != null || hasRow) && Money.parse(amount)?.let { it != 0L } == true && dateOk

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "Novo item" else "Editar item", style = MaterialTheme.typography.titleLarge)
            if (subcategories.isEmpty()) {
                Text("Crie uma subcategoria primeiro, como Mercado ou Gasolina.", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onNewSubcategory, modifier = Modifier.fillMaxWidth()) { Text("Nova subcategoria") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DropdownField(
                        "Subcategoria", subcategories, subcategories.firstOrNull { it.id == subId }, { it.name },
                        { subId = it?.id }, Modifier.weight(1f), noneLabel = null,
                    )
                    IconButton(onClick = onNewSubcategory) { Icon(Icons.Default.Add, "Nova subcategoria") }
                }
            }
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descrição (opcional)") }, singleLine = true)
            AmountField("Valor", amount, { amount = it })
            OutlinedTextField(
                date, { date = it.filter { c -> c.isDigit() || c == '/' }.take(10) }, Modifier.fillMaxWidth(),
                label = { Text("Data (dd/mm/aaaa)") }, singleLine = true, isError = !dateOk,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text("Pago com o salário do", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(true to "Dia 15", false to "Dia 30").forEachIndexed { index, (day15, label) ->
                    SegmentedButton(selected = onDay15 == day15, onClick = { onDay15 = day15 }, shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                        Text(label)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null && initial != null) OutlinedButton(onClick = { onDelete(initial) }, Modifier.weight(1f)) { Text("Excluir") }
                Button(
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val sub = subcategories.firstOrNull { it.id == subId }
                        onSave(
                            subId,
                            SubcategoryItemEntity(
                                id = initial?.id ?: 0,
                                expenseId = initial?.expenseId ?: 0,
                                description = description.trim().ifEmpty { sub?.name ?: initial?.description.orEmpty() },
                                date = parseDate(date),
                                amountCents = Money.parse(amount) ?: 0,
                                onDay15 = onDay15,
                            ),
                        )
                    },
                ) { Text("Salvar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A subcategory's row in the month: total against the target, and its items. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubcategoryRowSheet(
    row: ExpenseEntity,
    items: List<SubcategoryItemEntity>,
    onAddItem: () -> Unit,
    onEditItem: (SubcategoryItemEntity) -> Unit,
    onDeleteItem: (SubcategoryItemEntity) -> Unit,
    onEditRow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spent = BudgetCalculator.actual(row)
    val color = budgetStatusColor(spent, row.plannedCents)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.description, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onEditRow) { Icon(Icons.Outlined.Edit, "Editar linha") }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(spent, style = MaterialTheme.typography.headlineSmall, color = color)
                Text(" de ${Money.format(row.plannedCents)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(budgetStatusText(spent, row.plannedCents), style = MaterialTheme.typography.bodyMedium, color = color)
            Spacer(Modifier.height(10.dp))
            BudgetBar(spent, row.plannedCents, color)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddItem, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Adicionar item")
            }
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (items.isEmpty()) {
                item("empty") {
                    Text(
                        "Nenhum item ainda. Cada compra que você adicionar soma aqui.",
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(items, key = { _, it -> "item-${it.id}" }) { index, item ->
                Row(
                    Modifier.fillMaxWidth().clip(groupShape(index, items.size)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onEditItem(item) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(item.date?.let(::formatDate), if (item.onDay15) "dia 15" else "dia 30").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoneyText(item.amountCents, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { onDeleteItem(item) }) {
                        Icon(Icons.Outlined.DeleteOutline, "Excluir item", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** "Acompanhamento": the subcategories the user chose, spent against target with the status colour. */
@Composable
fun TrackerCard(
    tracked: List<SubcategoryEntity>,
    expenses: List<ExpenseEntity>,
    modifier: Modifier,
    onOpen: (SubcategoryEntity) -> Unit,
    onChoose: () -> Unit,
) {
    SectionCard(
        modifier,
        title = "Acompanhamento",
        subtitle = "Gasto de meta nas subcategorias",
        action = { TextButton(onClick = onChoose) { Text("Escolher") } },
    ) {
        if (tracked.isEmpty()) {
            Text(
                "Escolha as subcategorias que quer ver aqui, como Mercado e Gasolina.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        tracked.forEachIndexed { index, sub ->
            val row = expenses.firstOrNull { it.subcategoryId == sub.id }
            val planned = row?.plannedCents ?: sub.plannedCents
            val spent = row?.let(BudgetCalculator::actual) ?: 0
            val color = budgetStatusColor(spent, planned)
            if (index > 0) Spacer(Modifier.height(8.dp))
            Column(Modifier.clickable { onOpen(sub) }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sub.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(budgetStatusText(spent, planned), style = MaterialTheme.typography.bodySmall, color = color)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(spent, style = MaterialTheme.typography.titleSmall, color = color)
                        Text("de ${Money.format(planned)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                BudgetBar(spent, planned, color, height = 6.dp)
            }
        }
    }
}

/** Picks which subcategories appear in the "Acompanhamento" card. */
@Composable
fun TrackerChooserDialog(subcategories: List<SubcategoryEntity>, onSave: (List<Long>) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    val chosen = remember { mutableStateListOf<Long>().apply { addAll(subcategories.filter { it.inTracker }.map { it.id }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acompanhamento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (subcategories.isEmpty()) Text("Você ainda não tem subcategorias.")
                subcategories.forEach { sub ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (sub.id in chosen) chosen.remove(sub.id) else chosen.add(sub.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = sub.id in chosen, onCheckedChange = { if (it) chosen.add(sub.id) else chosen.remove(sub.id) })
                        Text(sub.name, Modifier.weight(1f))
                        Text(Money.format(sub.plannedCents), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onNew) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nova subcategoria")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen.toList()) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
