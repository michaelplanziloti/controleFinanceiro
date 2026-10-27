package com.controlefinanceiro.app.ui.year

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlefinanceiro.app.ui.common.Avatar
import com.controlefinanceiro.app.ui.common.BudgetBar
import com.controlefinanceiro.app.ui.common.Caption
import com.controlefinanceiro.app.ui.common.IconBadge
import com.controlefinanceiro.app.ui.common.LabelValue
import com.controlefinanceiro.app.ui.common.MONTH_NAMES
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.MoneyText
import com.controlefinanceiro.app.ui.common.Pill
import com.controlefinanceiro.app.ui.common.SectionCard
import com.controlefinanceiro.app.ui.common.groupShape
import com.controlefinanceiro.app.ui.common.shortName
import com.controlefinanceiro.app.ui.common.usageColor
import com.controlefinanceiro.app.ui.theme.LocalBlockColors
import com.controlefinanceiro.app.ui.theme.LocalHeroColors
import com.controlefinanceiro.app.ui.theme.LocalMoneyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearScreen(viewModel: YearViewModel, contentPadding: PaddingValues, onOpenMonth: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("${state.year}", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = { viewModel.step(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ano anterior") } },
                actions = { IconButton(onClick = { viewModel.step(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo ano") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { YearHero(state) }
            item { Caption("Meses", Modifier.padding(top = 24.dp, bottom = 8.dp)) }
            if (state.months.isEmpty()) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Nenhum mês criado em ${state.year}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            itemsIndexed(state.months, key = { _, it -> it.first.id }) { index, (month, summary) ->
                val color = usageColor(summary.actualCents, summary.incomeCents)
                Column(
                    Modifier.fillMaxWidth()
                        .clip(groupShape(index, state.months.size))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onOpenMonth(month.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(MONTH_NAMES[month.month - 1], style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Renda ${Money.format(summary.incomeCents)} · gasto ${Money.format(summary.actualCents)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MoneyText(summary.surplusCents, style = MaterialTheme.typography.titleSmall, colored = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    BudgetBar(summary.actualCents, summary.incomeCents, color, height = 6.dp)
                }
            }
            if (state.people.isNotEmpty()) {
                item {
                    val purple = LocalBlockColors.current.personal
                    val money = LocalMoneyColors.current
                    SectionCard(Modifier.padding(top = 20.dp), title = "Mesadas no ano") {
                        state.people.forEachIndexed { index, p ->
                            if (index > 0) Spacer(Modifier.height(16.dp))
                            val balance = p.allowanceCents - p.spentCents
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.person.name, purple)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(shortName(p.person.name), fontWeight = FontWeight.SemiBold)
                                    Text("Gasto ${Money.format(p.spentCents)} de ${Money.format(p.allowanceCents)}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Pill(Money.format(balance), if (balance < 0) money.negative else money.positive)
                            }
                            Spacer(Modifier.height(10.dp))
                            BudgetBar(p.spentCents, p.allowanceCents, purple, height = 6.dp)
                        }
                    }
                }
            }
            if (state.categories.isNotEmpty()) {
                item {
                    val max = state.categories.maxOf { it.second }
                    val color = LocalBlockColors.current.couple
                    SectionCard(Modifier.padding(top = 12.dp), title = "Gastos por categoria") {
                        state.categories.forEachIndexed { index, (name, cents) ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            Row {
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(Money.format(cents), style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(Modifier.height(6.dp))
                            BudgetBar(cents, max, color, height = 6.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHero(state: YearUiState) {
    val hero = LocalHeroColors.current
    Column(
        Modifier.fillMaxWidth().background(hero.brush, MaterialTheme.shapes.extraLarge).padding(24.dp),
    ) {
        Text("Sobra no ano", style = MaterialTheme.typography.labelLarge, color = hero.muted)
        Spacer(Modifier.height(4.dp))
        MoneyText(state.surplusCents, style = MaterialTheme.typography.displaySmall, color = hero.content)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroTile("Renda", state.incomeCents, Modifier.weight(1f))
            HeroTile("Gasto", state.actualCents, Modifier.weight(1f))
            HeroTile("Fora", state.extraCents, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroTile(label: String, cents: Long, modifier: Modifier) {
    val hero = LocalHeroColors.current
    LabelValue(
        label, cents,
        modifier.background(hero.tile, MaterialTheme.shapes.medium).padding(horizontal = 12.dp, vertical = 10.dp),
        labelColor = hero.muted, valueColor = hero.content,
    )
}
