package com.controlefinanceiro.app.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.MonthEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import com.controlefinanceiro.app.data.sheets.formatDate
import com.controlefinanceiro.app.domain.BudgetCalculator
import com.controlefinanceiro.app.ui.common.IconBadge
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.MoneyText
import com.controlefinanceiro.app.ui.common.groupShape
import com.controlefinanceiro.app.ui.common.monthLabel
import com.controlefinanceiro.app.ui.common.searchKey
import com.controlefinanceiro.app.ui.theme.LocalBlockColors

/** One search result: an expense of a month, or an item of a subcategory row. */
data class SearchHit(
    val month: MonthEntity,
    val expense: ExpenseEntity,
    val item: SubcategoryItemEntity?,
) {
    val title: String get() = item?.description ?: expense.description
    val amountCents: Long get() = item?.amountCents ?: BudgetCalculator.actual(expense).takeIf { it != 0L } ?: expense.plannedCents
    val createdAt: Long get() = item?.createdAt ?: expense.createdAt
}

/**
 * Finds expenses and items by description (case and accents ignored) or by value ("250", "1.500",
 * "61,7"), in every month, newest additions first.
 */
fun searchMonths(query: String, months: List<MonthEntity>, expenses: List<ExpenseEntity>, items: List<SubcategoryItemEntity>): List<SearchHit> {
    val text = query.searchKey()
    if (text.isEmpty()) return emptyList()
    val digits = query.filter { it.isDigit() || it == ',' }.trimEnd(',').takeIf { it.any(Char::isDigit) }
    fun valueMatches(cents: Long) = digits != null && cents != 0L && plainValue(cents).contains(digits)
    fun textMatches(value: String) = value.searchKey().contains(text)

    val monthsById = months.associateBy { it.id }
    val rows = expenses.associateBy { it.id }
    val hits = expenses.filter { e ->
        textMatches(e.description) || valueMatches(e.plannedCents) || valueMatches(BudgetCalculator.actual(e))
    }.mapNotNull { e -> monthsById[e.monthId]?.let { SearchHit(it, e, null) } } +
        items.filter { textMatches(it.description) || valueMatches(it.amountCents) }.mapNotNull { item ->
            val row = rows[item.expenseId] ?: return@mapNotNull null
            monthsById[row.monthId]?.let { SearchHit(it, row, item) }
        }
    return hits.sortedWith(compareByDescending<SearchHit> { it.createdAt }.thenByDescending { it.item?.id ?: it.expense.id }).take(200)
}

/** "R$ 1.234,50" as "1234,50", so a typed value matches with or without thousand dots. */
private fun plainValue(cents: Long) = Money.format(cents).filter { it.isDigit() || it == ',' }

@Composable
fun SearchScreen(
    query: String,
    results: List<SearchHit>,
    onQuery: (String) -> Unit,
    onOpen: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        placeholder = { Text("Descrição ou valor") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Limpar") } }) else null,
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                }
                val hint = when {
                    query.isBlank() -> "Busque em todos os meses pela descrição (ex.: mercado) ou pelo valor (ex.: 250 ou 1.500,00)."
                    results.isEmpty() -> "Nada encontrado para \"$query\"."
                    else -> null
                }
                if (hint != null) {
                    Text(hint, Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(results, key = { _, hit -> (if (hit.item != null) "i-${hit.item.id}" else "e-${hit.expense.id}") }) { index, hit ->
                        SearchRow(hit, groupShape(index, results.size)) { onOpen(hit) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val color = LocalBlockColors.current.of(hit.expense.block)
    val details = listOfNotNull(
        monthLabel(hit.month.year, hit.month.month),
        if (hit.item != null) hit.expense.description else hit.expense.block.title(),
        hit.item?.date?.let(::formatDate),
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Outlined.Search, color, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        MoneyText(hit.amountCents, style = MaterialTheme.typography.titleSmall)
    }
}
