package com.controlefinanceiro.app.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.domain.BudgetCalculator
import com.controlefinanceiro.app.domain.MonthSummary
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.MoneyText
import com.controlefinanceiro.app.ui.theme.LocalMoneyColors

/** Subcategory rows whose target differs from what was posted in them. */
fun targetsToEqualize(expenses: List<ExpenseEntity>): List<ExpenseEntity> =
    expenses.filter { it.subcategoryId != null && BudgetCalculator.actual(it) != it.plannedCents }

/** What an extra weighs once it is in the budget: the larger of its planned and paid values. */
fun extraValue(expense: ExpenseEntity): Long = maxOf(expense.plannedCents, BudgetCalculator.actual(expense))

/**
 * End of the month: 1) the subcategories' targets become what was posted (freeing what was left
 * of them), 2) the extras ("Fora do orçamento") that fit in what is left of the income can move
 * into "Variáveis do casal".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseMonthSheet(
    expenses: List<ExpenseEntity>,
    summary: MonthSummary,
    onEqualize: (List<ExpenseEntity>) -> Unit,
    onMove: (List<ExpenseEntity>) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoneyColors.current
    val toEqualize = targetsToEqualize(expenses)
    val freed = toEqualize.sumOf { it.plannedCents - BudgetCalculator.actual(it) }
    val extras = expenses.filter { it.block == ExpenseBlock.EXTRA && extraValue(it) > 0 }.sortedByDescending(::extraValue)
    var picked by remember { mutableStateOf(setOf<Long>()) }
    val available = summary.expectedSurplusCents
    val pickedCents = extras.filter { it.id in picked }.sumOf(::extraValue)
    val left = available - pickedCents

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Fechar o mês", style = MaterialTheme.typography.titleLarge)

            Text("1. Igualar as metas ao que foi lançado", style = MaterialTheme.typography.titleMedium)
            Text(
                "A meta de cada subcategoria neste mês vira o que foi gasto nela. O que sobrou das metas fica livre no orçamento.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (toEqualize.isEmpty()) {
                Text("As metas já batem com o lançado.", style = MaterialTheme.typography.bodyMedium, color = money.positive)
            } else {
                toEqualize.forEach { e ->
                    val spent = BudgetCalculator.actual(e)
                    val diff = e.plannedCents - spent
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Meta ${Money.format(e.plannedCents)} → ${Money.format(spent)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (diff > 0) "libera ${Money.format(diff)}" else "+${Money.format(-diff)}",
                            style = MaterialTheme.typography.labelLarge, color = if (diff > 0) money.positive else money.negative,
                        )
                    }
                }
                Button(onClick = { onEqualize(toEqualize) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Igualar ${toEqualize.size} ${if (toEqualize.size == 1) "meta" else "metas"}" +
                            if (freed > 0) " (libera ${Money.format(freed)})" else "",
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("2. Pagar gastos de fora do orçamento com o que sobrou", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sobra do orçamento, pelo previsto", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                MoneyText(left, style = MaterialTheme.typography.titleMedium, colored = true)
            }
            when {
                extras.isEmpty() -> Text("Não há gastos fora do orçamento neste mês.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                available <= 0 -> Text(
                    "Não sobrou dinheiro no orçamento deste mês." + if (toEqualize.isNotEmpty() && freed > 0) " Igualar as metas acima pode liberar." else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Text(
                        "Marque os que quer passar para Variáveis do casal. Os que não cabem no que sobra ficam apagados.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    extras.forEach { e ->
                        val value = extraValue(e)
                        val checked = e.id in picked
                        val fits = checked || value <= left
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, enabled = fits, onCheckedChange = { picked = if (it) picked + e.id else picked - e.id })
                            Text(
                                e.description, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (fits) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            )
                            MoneyText(value, style = MaterialTheme.typography.titleSmall, color = if (fits) Color.Unspecified else MaterialTheme.colorScheme.outline)
                        }
                    }
                    Button(
                        onClick = { onMove(extras.filter { it.id in picked }); picked = emptySet() },
                        enabled = picked.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (picked.isEmpty()) "Marque os gastos acima" else "Passar ${picked.size} para Variáveis do casal (${Money.format(pickedCents)})")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
