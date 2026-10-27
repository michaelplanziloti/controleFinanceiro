package com.controlefinanceiro.app.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.IncomeEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.ui.common.AmountField
import com.controlefinanceiro.app.ui.common.DropdownField
import com.controlefinanceiro.app.ui.common.MONTH_NAMES
import com.controlefinanceiro.app.ui.common.Money

fun ExpenseBlock.title(): String = when (this) {
    ExpenseBlock.TITHE -> "Dízimo"
    ExpenseBlock.FIXED -> "Gastos fixos"
    ExpenseBlock.COUPLE -> "Variáveis do casal"
    ExpenseBlock.PERSONAL -> "Mesadas"
    ExpenseBlock.INVESTMENT -> "Investimentos"
    ExpenseBlock.EXTRA -> "Fora do orçamento"
}

/** Bottom sheet to add or edit an expense of any block. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorSheet(
    initial: ExpenseEntity,
    reference: ReferenceData,
    onSave: (ExpenseEntity) -> Unit,
    onDelete: ((ExpenseEntity) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var description by remember { mutableStateOf(initial.description) }
    var block by remember { mutableStateOf(initial.block) }
    var personId by remember { mutableStateOf(initial.personId ?: reference.people.firstOrNull()?.id) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var methodId by remember { mutableStateOf(initial.paymentMethodId) }
    var cardId by remember { mutableStateOf(initial.cardId) }
    var dueDay by remember { mutableStateOf(initial.dueDay.orEmpty()) }
    var planned by remember { mutableStateOf(Money.toInput(initial.plannedCents)) }
    var auto by remember { mutableStateOf(initial.autoPlanned) }
    var paid30 by remember { mutableStateOf(Money.toInput(initial.paidDay30Cents)) }
    var paid15 by remember { mutableStateOf(Money.toInput(initial.paidDay15Cents)) }
    var isPaid by remember { mutableStateOf(initial.isPaid) }

    val valid = description.isNotBlank() && Money.parse(planned) != null && Money.parse(paid30) != null &&
        Money.parse(paid15) != null && (block != ExpenseBlock.PERSONAL || personId != null)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial.id == 0L) "Novo lançamento" else "Editar lançamento", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descrição") }, singleLine = true)
            DropdownField(
                label = "Bloco",
                options = ExpenseBlock.entries,
                selected = block,
                optionLabel = { it.title() },
                onSelect = { if (it != null) block = it },
                noneLabel = null,
            )
            if (block == ExpenseBlock.PERSONAL) {
                DropdownField(
                    label = "Mesada de",
                    options = reference.people,
                    selected = reference.people.firstOrNull { it.id == personId },
                    optionLabel = PersonEntity::name,
                    onSelect = { personId = it?.id },
                    noneLabel = null,
                )
            }
            DropdownField("Categoria", reference.categories, reference.categories.firstOrNull { it.id == categoryId }, { it.name }, { categoryId = it?.id })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    "Forma de pagamento", reference.paymentMethods, reference.paymentMethods.firstOrNull { it.id == methodId },
                    { it.name }, { methodId = it?.id }, Modifier.weight(1.4f),
                )
                OutlinedTextField(dueDay, { dueDay = it }, Modifier.weight(1f), label = { Text("Venc.") }, singleLine = true)
            }
            DropdownField("Cartão", reference.cards, reference.cards.firstOrNull { it.id == cardId }, { it.name }, { cardId = it?.id })
            if (block == ExpenseBlock.TITHE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Calcular pela renda", Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
            }
            if (!(block == ExpenseBlock.TITHE && auto)) AmountField("Meta", planned, { planned = it })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField("Pago dia 30", paid30, { paid30 = it }, Modifier.weight(1f))
                AmountField("Pago dia 15", paid15, { paid15 = it }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
                Text("Pago")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = { onDelete(initial) }, Modifier.weight(1f)) { Text("Excluir") }
                }
                Button(
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSave(
                            initial.copy(
                                description = description.trim(),
                                block = block,
                                personId = if (block == ExpenseBlock.PERSONAL) personId else null,
                                categoryId = categoryId,
                                paymentMethodId = methodId,
                                cardId = cardId,
                                dueDay = dueDay.trim().ifEmpty { null },
                                plannedCents = Money.parse(planned) ?: 0,
                                autoPlanned = block == ExpenseBlock.TITHE && auto,
                                paidDay30Cents = Money.parse(paid30) ?: 0,
                                paidDay15Cents = Money.parse(paid15) ?: 0,
                                isPaid = isPaid,
                            )
                        )
                    },
                ) { Text("Salvar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeEditorSheet(
    initial: IncomeEntity,
    people: List<PersonEntity>,
    onSave: (IncomeEntity) -> Unit,
    onDelete: ((IncomeEntity) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var source by remember { mutableStateOf(initial.source) }
    var personId by remember { mutableStateOf(initial.personId) }
    var amount by remember { mutableStateOf(Money.toInput(initial.amountCents)) }
    val valid = source.isNotBlank() && Money.parse(amount) != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial.id == 0L) "Nova renda" else "Editar renda", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("Fonte") }, singleLine = true)
            DropdownField("De quem", people, people.firstOrNull { it.id == personId }, { it.name }, { personId = it?.id }, noneLabel = "Casal")
            AmountField("Valor", amount, { amount = it })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) OutlinedButton(onClick = { onDelete(initial) }, Modifier.weight(1f)) { Text("Excluir") }
                Button(
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    onClick = { onSave(initial.copy(source = source.trim(), personId = personId, amountCents = Money.parse(amount) ?: 0)) },
                ) { Text("Salvar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AllowanceDialog(person: PersonEntity, currentCents: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(Money.toInput(currentCents)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mesada de ${person.name}") },
        text = { AmountField("Valor do mês", amount, { amount = it }) },
        confirmButton = {
            TextButton(enabled = Money.parse(amount) != null, onClick = { onSave(Money.parse(amount) ?: 0) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun NewMonthDialog(defaultYear: Int, defaultMonth: Int, onCreate: (Int, Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableIntStateOf(defaultYear) }
    var month by remember { mutableIntStateOf(defaultMonth) }
    var copy by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo mês") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField("Mês", (1..12).toList(), month, { MONTH_NAMES[it - 1] }, { if (it != null) month = it }, noneLabel = null)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { year-- }) { Text("−") }
                    Text("$year", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { year++ }) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copy, onCheckedChange = { copy = it })
                    Text("Copiar renda, mesadas e contas recorrentes do mês anterior")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(year, month, copy) }) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
