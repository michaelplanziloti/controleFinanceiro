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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.ui.common.Avatar
import com.controlefinanceiro.app.ui.common.BudgetBar
import com.controlefinanceiro.app.ui.common.Caption
import com.controlefinanceiro.app.ui.common.Dot
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
import com.controlefinanceiro.app.ui.month.title
import com.controlefinanceiro.app.ui.theme.LocalBlockColors
import com.controlefinanceiro.app.ui.theme.LocalHeroColors
import com.controlefinanceiro.app.ui.theme.LocalMoneyColors
import java.time.LocalDate

/**
 * The year at a glance, in the style of investment and bank apps: the surplus piling up, income
 * against spending, where the money went and how much was saved, month by month. Every chart
 * shares the picked month (tap or slide over any of them).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearScreen(
    viewModel: YearViewModel,
    contentPadding: PaddingValues,
    onOpenMonth: (Long) -> Unit,
    onMenu: () -> Unit,
    /** Creates the month (year, month) copying the one before, and opens it. */
    onCreateMonth: (Int, Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Starts on the current month this year, or on the last month created.
    var selected by rememberSaveable(state.year, state.points.count { it.hasData }) {
        val today = LocalDate.now()
        mutableStateOf(
            when {
                state.year == today.year && state.points[today.monthValue - 1].hasData -> today.monthValue - 1
                // A year still to come starts on its first month.
                state.allFuture -> state.points.indexOfFirst { it.hasData }.takeIf { it >= 0 }
                else -> state.points.indexOfLast { it.hasData && !it.future }.takeIf { it >= 0 }
            },
        )
    }
    val pick: (Int) -> Unit = { selected = it }

    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("${state.year}", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Menu") }
                        IconButton(onClick = { viewModel.step(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ano anterior") }
                    }
                },
                actions = { IconButton(onClick = { viewModel.step(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo ano") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("hero") { YearHero(state) }
            if (!state.hasData) {
                item("empty") {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Nenhum mês criado em ${state.year}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                return@LazyColumn
            }
            item("surplus") { SurplusCard(state, selected, pick) }
            item("flow") { FlowCard(state, selected, pick, onOpenMonth, onCreateMonth) }
            item("blocks") { BlocksCard(state, selected, pick) }
            item("savings") { SavingsCard(state, selected, pick) }
            item("highlights") { Highlights(state) }
            if (state.categories.isNotEmpty()) item("categories") { RankingCard("Gastos por categoria", "Somando os 12 meses", state.categories, LocalBlockColors.current.couple) }
            if (state.cards.isNotEmpty()) item("cards") { RankingCard("Cartões no ano", "Gasto em cada cartão", state.cards, LocalBlockColors.current.fixed) }
            if (state.people.isNotEmpty()) item("people") { PeopleCard(state.people) }
            if (state.months.isNotEmpty()) {
                item("months-caption") { Caption("Meses", Modifier.padding(top = 12.dp)) }
                item("months") { MonthList(state, onOpenMonth) }
            }
        }
    }
}

@Composable
private fun YearHero(state: YearUiState) {
    val hero = LocalHeroColors.current
    Column(Modifier.fillMaxWidth().background(hero.brush, MaterialTheme.shapes.extraLarge).padding(24.dp)) {
        if (state.allFuture) {
            FutureYearHero(state)
            return@Column
        }
        Text(if (state.hasFuture) "Sobra até agora" else "Sobra no ano", style = MaterialTheme.typography.labelLarge, color = hero.muted)
        Spacer(Modifier.height(4.dp))
        MoneyText(state.surplusCents, style = MaterialTheme.typography.displaySmall, color = hero.content)
        state.previousSameTimeCents?.let { before ->
            val diff = state.surplusCents - before
            val money = LocalMoneyColors.current
            Spacer(Modifier.height(4.dp))
            Text(
                "${if (diff >= 0) "+" else "−"}${Money.format(kotlin.math.abs(diff))} que ${state.year - 1}" +
                    if (state.hasFuture) " no mesmo período" else "",
                style = MaterialTheme.typography.labelLarge, color = if (diff >= 0) money.positive else money.negative,
            )
        }
        state.savingsRate?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Poupança ${Money.formatPercent(it)} da renda",
                Modifier.background(hero.tile, MaterialTheme.shapes.small).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge, color = hero.content,
            )
        }
        if (state.hasFuture) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Projeção para o ano: ${Money.format(state.projectedSurplusCents)}, com os meses que faltam pelo previsto",
                style = MaterialTheme.typography.bodySmall, color = hero.muted,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroTile("Renda", state.incomeCents, Modifier.weight(1f))
            HeroTile("Gasto", state.actualCents, Modifier.weight(1f))
            HeroTile("Investido", state.investedCents, Modifier.weight(1f))
        }
    }
}

/** A year that has not started: everything is projected from the latest month. */
@Composable
private fun FutureYearHero(state: YearUiState) {
    val hero = LocalHeroColors.current
    val months = state.points.filter { it.hasData }
    Text("Sobra prevista em ${state.year}", style = MaterialTheme.typography.labelLarge, color = hero.muted)
    Spacer(Modifier.height(4.dp))
    MoneyText(state.projectedSurplusCents, style = MaterialTheme.typography.displaySmall, color = hero.content)
    Spacer(Modifier.height(10.dp))
    Text(
        "Projeção: cada mês como o último criado, com as contas pelo previsto e as parcelas até acabarem.",
        style = MaterialTheme.typography.bodySmall, color = hero.muted,
    )
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HeroTile("Renda", months.sumOf { it.incomeCents }, Modifier.weight(1f))
        HeroTile("Gasto", months.sumOf { it.spentCents }, Modifier.weight(1f))
        HeroTile("Investido", months.sumOf { it.expectedInvestedCents }, Modifier.weight(1f))
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

/** The value of the picked month above a chart, like the balance on top of an investment chart. */
@Composable
private fun PickedValue(label: String, cents: Long?, colored: Boolean = false) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (cents == null) Text("—", style = MaterialTheme.typography.headlineSmall)
    else MoneyText(cents, style = MaterialTheme.typography.headlineSmall, colored = colored)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SurplusCard(state: YearUiState, selected: Int?, onSelect: (Int) -> Unit) {
    var cumulative by rememberSaveable { mutableStateOf(true) }
    val line = MaterialTheme.colorScheme.primaryContainer
    SectionCard(title = "Evolução da sobra", subtitle = "Renda menos gastos do orçamento") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(true to "Acumulado", false to "Por mês").forEachIndexed { i, (value, label) ->
                SegmentedButton(cumulative == value, { cumulative = value }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        Spacer(Modifier.height(16.dp))
        val values: List<Long?> = if (cumulative) state.cumulativeSurplus else state.points.map { p -> p.surplusCents.takeIf { p.hasData } }
        val before: List<Long?>? = if (!state.hasPrevious) null
        else if (cumulative) state.previousCumulative else state.previous.map { p -> p.surplusCents.takeIf { p.hasData } }
        val month = selected?.let { MONTH_NAMES[it] }
        val projected = selected != null && state.points[selected].future
        PickedValue(
            when {
                month == null -> "Sobra"
                cumulative -> "Até $month" + if (projected) " (projeção)" else ""
                else -> "Sobra em $month" + if (projected) " (pelo previsto)" else ""
            },
            selected?.let { values[it] }, colored = true,
        )
        MonthLineChart(
            values.map { it?.toFloat() }, line, selected, onSelect, projectedFrom = state.firstFuture,
            compare = before?.map { it?.toFloat() },
        )
        if (before != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("${state.year}", line)
                Legend("${state.year - 1}", MaterialTheme.colorScheme.outline)
            }
            selected?.let { before[it] }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Em ${state.year - 1}, ${if (cumulative) "até" else "em"} ${MONTH_NAMES[selected!!]}: ${Money.format(it)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.hasFuture) ProjectionNote()
    }
}

@Composable
private fun FlowCard(state: YearUiState, selected: Int?, onSelect: (Int) -> Unit, onOpenMonth: (Long) -> Unit, onCreateMonth: (Int, Int) -> Unit) {
    val money = LocalMoneyColors.current
    val spentColor = MaterialTheme.colorScheme.onSurface
    val extraColor = LocalBlockColors.current.extra
    SectionCard(title = "Renda x gastos", subtitle = "Entradas e saídas de cada mês") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("Renda", money.positive)
            Legend("Gastos", spentColor)
            Legend("Fora do orçamento", extraColor)
        }
        Spacer(Modifier.height(16.dp))
        MonthBarChart(
            series = listOf(
                state.points.map { it.incomeCents.toFloat() },
                state.points.map { it.spentCents.toFloat() },
                state.points.map { it.extraCents.toFloat() },
            ),
            colors = listOf(money.positive, spentColor, extraColor),
            selected = selected, onSelect = onSelect, fadedFrom = state.firstFuture,
        )
        val point = selected?.let { state.points[it] } ?: return@SectionCard
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(MONTH_NAMES[point.month - 1], Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                when {
                    point.projected -> Pill("Projeção", MaterialTheme.colorScheme.onSurfaceVariant)
                    point.future -> Pill("Pelo previsto", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!point.hasData) {
                Text("Mês não criado.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onCreateMonth(state.year, point.month) }, Modifier.align(Alignment.End)) { Text("Criar mês") }
                return@Column
            }
            Spacer(Modifier.height(10.dp))
            Row {
                LabelValue("Renda", point.incomeCents, Modifier.weight(1f))
                LabelValue(if (point.future) "Gastos previstos" else "Gastos", point.spentCents, Modifier.weight(1f))
                LabelValue("Sobra", point.surplusCents, Modifier.weight(1f), colored = true)
            }
            Spacer(Modifier.height(10.dp))
            BudgetBar(point.spentCents, point.incomeCents, usageColor(point.spentCents, point.incomeCents), height = 6.dp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        point.incomeCents <= 0 -> "Sem renda lançada"
                        point.future -> "Deve usar ${Money.formatPercent(point.spentCents.toDouble() / point.incomeCents)} da renda"
                        else -> "Usou ${Money.formatPercent(point.spentCents.toDouble() / point.incomeCents)} da renda"
                    },
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val monthId = point.monthId
                TextButton(onClick = { if (monthId != null) onOpenMonth(monthId) else onCreateMonth(state.year, point.month) }) {
                    Text(if (monthId != null) "Abrir mês" else "Criar mês")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun ProjectionNote() {
    Spacer(Modifier.height(8.dp))
    Text(
        "Os meses que ainda vão chegar aparecem mais claros ou tracejados, pelo previsto.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 8)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Order of the blocks in the donut and the stacked bars. */
private val BLOCK_ORDER = listOf(
    ExpenseBlock.TITHE, ExpenseBlock.FIXED, ExpenseBlock.COUPLE, ExpenseBlock.PERSONAL, ExpenseBlock.INVESTMENT, ExpenseBlock.EXTRA,
)

@Composable
private fun BlocksCard(state: YearUiState, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = LocalBlockColors.current
    // The year, or the picked month when the bars below are touched.
    var showMonth by rememberSaveable { mutableStateOf(false) }
    val point = selected?.let { state.points[it] }
    val totals = if (showMonth && point != null) point.byBlock else state.byBlock
    val total = totals.values.sum()
    SectionCard(
        title = if (state.allFuture) "Para onde deve ir o dinheiro" else "Para onde foi o dinheiro",
        subtitle = if (showMonth && point != null) MONTH_NAMES[point.month - 1] else if (state.hasFuture) "No ano todo, com o previsto dos meses que faltam" else "No ano todo",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Donut(BLOCK_ORDER.map { (totals[it] ?: 0L).toFloat() to colors.of(it) }, size = 148.dp, thickness = 20.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(total, Modifier.padding(horizontal = 26.dp), style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BLOCK_ORDER.filter { (totals[it] ?: 0L) > 0 }.forEach { block ->
                    val cents = totals[block] ?: 0L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(colors.of(block), 8)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(block.title(), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${Money.formatPercent(cents.toDouble() / total)} · ${Money.format(cents)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Mês a mês", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        MonthBarChart(
            series = BLOCK_ORDER.map { block -> state.points.map { (it.byBlock[block] ?: 0L).toFloat() } },
            colors = BLOCK_ORDER.map { colors.of(it) },
            fadedFrom = state.firstFuture,
            selected = selected.takeIf { showMonth },
            onSelect = { onSelect(it); showMonth = true },
            stacked = true,
        )
        if (showMonth) {
            TextButton(onClick = { showMonth = false }, Modifier.align(Alignment.End)) { Text("Ver o ano todo") }
        }
    }
}

@Composable
private fun SavingsCard(state: YearUiState, selected: Int?, onSelect: (Int) -> Unit) {
    val money = LocalMoneyColors.current
    val rates = state.points.map { it.savingsRate }
    // The average only counts the months that already happened.
    val known = state.points.filter { !it.future }.mapNotNull { it.savingsRate }
    val average = known.takeIf { it.isNotEmpty() }?.average()
    SectionCard(title = "Poupança por mês", subtitle = "Investido mais a sobra, sobre a renda") {
        Row {
            Column(Modifier.weight(1f)) {
                Text(selected?.let { MONTH_NAMES[it] } ?: "Mês", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected?.let { rates[it] }?.let(Money::formatPercent) ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Média", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(average?.let(Money::formatPercent) ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        // Green when saving, red when the month spent more than it earned.
        MonthBarChart(
            series = listOf(rates.map { ((it ?: 0.0).coerceAtLeast(0.0) * 100).toFloat() }, rates.map { ((it ?: 0.0).coerceAtMost(0.0) * 100).toFloat() }),
            colors = listOf(money.positive, money.negative),
            selected = selected, onSelect = onSelect, stacked = false,
            average = average?.let { (it * 100).toFloat() }, fadedFrom = state.firstFuture, height = 130.dp,
        )
        if (state.hasFuture) ProjectionNote()
    }
}

@Composable
private fun Highlights(state: YearUiState) {
    // Only months that already happened: a month still to come has its bills unpaid.
    val created = state.points.filter { it.hasData && !it.future }
    val money = LocalMoneyColors.current
    val mostSpent = created.maxByOrNull { it.actualCents }
    val bestSurplus = created.maxByOrNull { it.surplusCents }
    val worstSurplus = created.minByOrNull { it.surplusCents }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("Destaques", Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HighlightTile(Icons.AutoMirrored.Outlined.TrendingUp, money.positive, "Melhor sobra", bestSurplus?.let { MONTH_NAMES[it.month - 1] }, bestSurplus?.surplusCents, Modifier.weight(1f))
            HighlightTile(Icons.AutoMirrored.Outlined.TrendingDown, money.negative, "Pior sobra", worstSurplus?.let { MONTH_NAMES[it.month - 1] }, worstSurplus?.surplusCents, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HighlightTile(Icons.Outlined.ShoppingCart, LocalBlockColors.current.couple, "Maior gasto", mostSpent?.let { MONTH_NAMES[it.month - 1] }, mostSpent?.actualCents, Modifier.weight(1f))
            HighlightTile(
                Icons.Outlined.Savings, LocalBlockColors.current.investment, "Média de gastos", "${created.size} meses",
                created.takeIf { it.isNotEmpty() }?.let { c -> c.sumOf { it.actualCents } / c.size }, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HighlightTile(icon: ImageVector, color: Color, label: String, detail: String?, cents: Long?, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)) {
        IconBadge(icon, color, size = 36.dp)
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (cents != null) MoneyText(cents, style = MaterialTheme.typography.titleMedium) else Text("—", style = MaterialTheme.typography.titleMedium)
        Text(detail ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Biggest first, with each one's share of the total; the first five, then all on request. */
@Composable
private fun RankingCard(title: String, subtitle: String, rows: List<Pair<String, Long>>, color: Color) {
    var all by rememberSaveable(title) { mutableStateOf(false) }
    val total = rows.sumOf { it.second }
    val max = rows.maxOf { it.second }
    SectionCard(title = title, subtitle = subtitle) {
        (if (all) rows else rows.take(5)).forEachIndexed { index, (name, cents) ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.width(22.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Money.formatPercent(cents.toDouble() / total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(Money.format(cents), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            BudgetBar(cents, max, color, Modifier.padding(start = 22.dp), height = 6.dp)
        }
        if (rows.size > 5) {
            FilledTonalButton(onClick = { all = !all }, Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(if (all) "Mostrar menos" else "Ver todos (${rows.size})")
            }
        }
    }
}

@Composable
private fun PeopleCard(people: List<PersonYear>) {
    val purple = LocalBlockColors.current.personal
    val money = LocalMoneyColors.current
    SectionCard(title = "Mesadas no ano") {
        people.forEachIndexed { index, p ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            val balance = p.allowanceCents - p.spentCents
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.person.name, purple)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(shortName(p.person.name), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Gasto ${Money.format(p.spentCents)} de ${Money.format(p.allowanceCents)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(Money.format(balance), if (balance < 0) money.negative else money.positive)
            }
            Spacer(Modifier.height(10.dp))
            BudgetBar(p.spentCents, p.allowanceCents, purple, height = 6.dp)
        }
    }
}

@Composable
private fun MonthList(state: YearUiState, onOpenMonth: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        state.months.forEachIndexed { index, (month, summary) ->
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
                BudgetBar(summary.actualCents, summary.incomeCents, usageColor(summary.actualCents, summary.incomeCents), height = 6.dp)
            }
        }
    }
}
