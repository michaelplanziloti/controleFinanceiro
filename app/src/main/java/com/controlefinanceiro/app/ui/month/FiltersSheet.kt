package com.controlefinanceiro.app.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.ui.common.DropdownField

/** Picks the filters of the month's lists; each change applies at once, [matching] counts the result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(
    filters: MonthFilters,
    reference: ReferenceData,
    matching: Int,
    onChange: (MonthFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filtros", style = MaterialTheme.typography.titleLarge)

            Label("Dia do pagamento")
            Choices(listOf(null to "Todos", PayDay.DAY15 to "Dia 15", PayDay.DAY30 to "Dia 30"), filters.day) { onChange(filters.copy(day = it)) }

            Label("Baixa")
            Choices(listOf(null to "Todos", true to "Pagos", false to "Pendentes"), filters.paid) { onChange(filters.copy(paid = it)) }

            DropdownField(
                "Categoria", reference.categories, reference.categories.firstOrNull { it.id == filters.categoryId }, { it.name },
                { onChange(filters.copy(categoryId = it?.id)) }, noneLabel = "Todas",
            )
            DropdownField(
                "Cartão", reference.cards, reference.cards.firstOrNull { it.id == filters.cardId }, { it.name },
                { onChange(filters.copy(cardId = it?.id)) }, noneLabel = "Todos",
            )
            DropdownField(
                "Forma de pagamento", reference.paymentMethods, reference.paymentMethods.firstOrNull { it.id == filters.paymentMethodId }, { it.name },
                { onChange(filters.copy(paymentMethodId = it?.id)) }, noneLabel = "Todas",
            )

            Label("Outros")
            FilterChip(
                selected = filters.overTarget, onClick = { onChange(filters.copy(overTarget = !filters.overTarget)) },
                label = { Text("Passou da meta") },
                leadingIcon = if (filters.overTarget) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
            )

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onChange(MonthFilters()) }, enabled = filters.active, modifier = Modifier.weight(1f)) { Text("Limpar") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(if (filters.active) "Ver $matching ${if (matching == 1) "lançamento" else "lançamentos"}" else "Pronto")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge)

/** One choice among a few, as chips. */
@Composable
private fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) },
                leadingIcon = if (selected == value) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
            )
        }
    }
}

/** Strip above the lists while filters are on: what is filtered, how many match, and "Limpar". */
@Composable
fun ActiveFiltersBar(filters: MonthFilters, reference: ReferenceData, matching: Int, onOpen: () -> Unit, onClear: () -> Unit) {
    val parts = listOfNotNull(
        filters.day?.label,
        filters.paid?.let { if (it) "Pagos" else "Pendentes" },
        reference.categories.firstOrNull { it.id == filters.categoryId }?.name,
        reference.cards.firstOrNull { it.id == filters.cardId }?.let { "Cartão ${it.name}" },
        reference.paymentMethods.firstOrNull { it.id == filters.paymentMethodId }?.name,
        "Passou da meta".takeIf { filters.overTarget },
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.FilterList, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(
                "$matching ${if (matching == 1) "lançamento" else "lançamentos"} nas listas abaixo",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        TextButton(onClick = onOpen) { Text("Editar", color = MaterialTheme.colorScheme.onPrimaryContainer) }
        TextButton(onClick = onClear) { Text("Limpar", color = MaterialTheme.colorScheme.onPrimaryContainer) }
    }
}
