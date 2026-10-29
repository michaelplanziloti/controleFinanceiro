package com.controlefinanceiro.app.ui.month

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlefinanceiro.app.data.db.CardEntity
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.ExpenseEntity
import com.controlefinanceiro.app.data.db.IncomeEntity
import com.controlefinanceiro.app.data.db.PersonEntity
import com.controlefinanceiro.app.data.db.SubcategoryEntity
import com.controlefinanceiro.app.data.db.SubcategoryItemEntity
import com.controlefinanceiro.app.data.sheets.nameKey
import com.controlefinanceiro.app.domain.BudgetCalculator
import com.controlefinanceiro.app.domain.BudgetGroup
import com.controlefinanceiro.app.domain.MonthSummary
import com.controlefinanceiro.app.ui.common.Avatar
import com.controlefinanceiro.app.ui.common.BudgetBar
import com.controlefinanceiro.app.ui.common.IconBadge
import com.controlefinanceiro.app.ui.common.LabelValue
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.MoneyText
import com.controlefinanceiro.app.ui.common.Pill
import com.controlefinanceiro.app.ui.common.SectionCard
import com.controlefinanceiro.app.ui.common.UsageRing
import com.controlefinanceiro.app.ui.common.groupShape
import com.controlefinanceiro.app.ui.common.monthLabel
import com.controlefinanceiro.app.ui.common.ratioOf
import com.controlefinanceiro.app.ui.common.shortName
import com.controlefinanceiro.app.ui.common.usageColor
import com.controlefinanceiro.app.ui.theme.BlockColors
import com.controlefinanceiro.app.ui.theme.LocalBlockColors
import com.controlefinanceiro.app.ui.theme.LocalHeroColors
import com.controlefinanceiro.app.ui.theme.LocalMoneyColors
import kotlin.math.abs
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Sections shown in the month, top to bottom. TITHE is listed inside the fixed section. */
private enum class Section(val title: String, val icon: ImageVector, val blocks: Set<ExpenseBlock>, val newBlock: ExpenseBlock) {
    FIXED("Gastos fixos", Icons.Outlined.Home, setOf(ExpenseBlock.TITHE, ExpenseBlock.FIXED), ExpenseBlock.FIXED),
    COUPLE("Variáveis do casal", Icons.Outlined.ShoppingCart, setOf(ExpenseBlock.COUPLE), ExpenseBlock.COUPLE),
    PERSONAL("Mesadas", Icons.Outlined.Person, setOf(ExpenseBlock.PERSONAL), ExpenseBlock.PERSONAL),
    INVESTMENT("Investimentos", Icons.AutoMirrored.Outlined.TrendingUp, setOf(ExpenseBlock.INVESTMENT), ExpenseBlock.INVESTMENT),
    EXTRA("Fora do orçamento", Icons.Outlined.MoreHoriz, setOf(ExpenseBlock.EXTRA), ExpenseBlock.EXTRA),
}

private fun BudgetGroup.icon(): ImageVector = when (this) {
    BudgetGroup.TITHE -> Icons.Outlined.VolunteerActivism
    BudgetGroup.FIXED -> Icons.Outlined.Home
    BudgetGroup.VARIABLE -> Icons.Outlined.ShoppingCart
    BudgetGroup.INVESTMENT -> Icons.AutoMirrored.Outlined.TrendingUp
}

private fun BudgetGroup.color(blocks: BlockColors): Color = when (this) {
    BudgetGroup.TITHE -> blocks.tithe
    BudgetGroup.FIXED -> blocks.fixed
    BudgetGroup.VARIABLE -> blocks.couple
    BudgetGroup.INVESTMENT -> blocks.investment
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(viewModel: MonthViewModel, contentPadding: PaddingValues, onMenu: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var editingIncome by remember { mutableStateOf<IncomeEntity?>(null) }
    var editingAllowance by remember { mutableStateOf<PersonEntity?>(null) }
    var showNewMonth by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDeleteMonth by remember { mutableStateOf(false) }
    // Sections start collapsed, so the month opens on the overview; a missing key means collapsed.
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    fun isCollapsed(key: String) = collapsed[key] ?: true
    val listState = rememberLazyListState()
    // Where each section starts in the list, filled while the list is built.
    val anchors = remember { mutableMapOf<String, Int>() }
    var expenseList by remember { mutableStateOf<ExpenseFilter?>(null) }
    // The list to show again when the editor opened from it closes.
    var returnToList by remember { mutableStateOf<ExpenseFilter?>(null) }
    // Subcategories: the row whose items are open, the item and subcategory being edited, the generic "+".
    var openRowId by remember { mutableStateOf<Long?>(null) }
    var editingItem by remember { mutableStateOf<Pair<SubcategoryItemEntity?, Long?>?>(null) }
    var editingSubcategory by remember { mutableStateOf<SubcategoryEntity?>(null) }
    var showNewEntry by remember { mutableStateOf(false) }
    var showTrackerChooser by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    fun newSubcategory() {
        editingSubcategory = SubcategoryEntity(userId = 0, name = "", block = ExpenseBlock.COUPLE, plannedCents = 0, inTracker = true)
    }

    // Open expenses of a card waiting for the "which day was it paid" answer.
    var payingAll by remember { mutableStateOf<Pair<ExpenseFilter, List<ExpenseEntity>>?>(null) }

    // Overview cards can be reordered: long-press one and drag it up or down.
    val savedOrder by viewModel.cardOrder.collectAsStateWithLifecycle()
    var cardOrder by remember(savedOrder) { mutableStateOf(savedOrder) }
    var draggingCard by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Finger position inside the dragged card, to scroll the list when it reaches the top or bottom edge.
    var dragFingerY by remember { mutableFloatStateOf(0f) }
    val edgePx = with(LocalDensity.current) { 96.dp.toPx() }
    val maxScrollPx = with(LocalDensity.current) { 18.dp.toPx() }
    val haptics = LocalHapticFeedback.current

    /** Moves the dragged card past a neighbour once its middle crosses into it, keeping it under the finger. */
    fun dragCard(key: String, dy: Float) {
        dragOffset += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        val middle = current.offset + dragOffset + current.size / 2f
        val target = items.firstOrNull { it.key != key && it.key in cardOrder && middle >= it.offset && middle < it.offset + it.size }
            ?: return
        val from = cardOrder.indexOf(key)
        val to = cardOrder.indexOf(target.key)
        // The list keeps its position on the first visible card; when that card is part of the swap it
        // would follow it and push the dragged card off screen, so the scroll position is pinned instead.
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        cardOrder = cardOrder.toMutableList().apply { add(to, removeAt(from)) }
        if (current.index == firstIndex || target.index == firstIndex) {
            scope.launch { listState.scrollToItem(firstIndex, firstOffset) }
        }
        dragOffset += if (to > from) -((target.offset + target.size) - (current.offset + current.size)).toFloat()
        else (current.offset - target.offset).toFloat()
    }

    // While a card is held near the top or bottom of the screen, the list scrolls and the card stays under the finger.
    LaunchedEffect(draggingCard) {
        val key = draggingCard ?: return@LaunchedEffect
        while (draggingCard == key) {
            val info = listState.layoutInfo
            val current = info.visibleItemsInfo.firstOrNull { it.key == key }
            if (current != null) {
                val fingerY = current.offset + dragOffset + dragFingerY
                val speed = when {
                    fingerY < info.viewportStartOffset + edgePx -> -maxScrollPx * (1f - ((fingerY - info.viewportStartOffset) / edgePx).coerceIn(0f, 1f))
                    fingerY > info.viewportEndOffset - edgePx -> maxScrollPx * (1f - ((info.viewportEndOffset - fingerY) / edgePx).coerceIn(0f, 1f))
                    else -> 0f
                }
                if (speed != 0f) {
                    val consumed = listState.scrollBy(speed)
                    dragOffset += consumed
                    dragCard(key, 0f)
                }
            }
            withFrameNanos { }
        }
    }

    fun endCardDrag() {
        if (draggingCard != null) viewModel.saveCardOrder(cardOrder)
        draggingCard = null
        dragOffset = 0f
    }

    fun Modifier.reorderableCard(key: String) = this
        .zIndex(if (draggingCard == key) 1f else 0f)
        .graphicsLayer {
            if (draggingCard == key) {
                translationY = dragOffset
                scaleX = 1.02f
                scaleY = 1.02f
                alpha = 0.95f
            }
        }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { start ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    draggingCard = key
                    dragOffset = 0f
                    dragFingerY = start.y
                },
                onDrag = { change, amount ->
                    change.consume()
                    dragFingerY = change.position.y
                    dragCard(key, amount.y)
                },
                onDragEnd = ::endCardDrag,
                onDragCancel = ::endCardDrag,
            )
        }

    /** Expands the target section if needed and scrolls to it. */
    fun goTo(target: String) {
        val section = when {
            target == ANCHOR_INCOME -> ANCHOR_INCOME
            target.startsWith("person-") -> Section.PERSONAL.name
            Section.entries.any { it.name == target } -> target
            else -> null
        }
        section?.let { collapsed[it] = false }
        scope.launch {
            withFrameNanos { } // lets the expanded section join the list first
            anchors[target]?.let { listState.animateScrollToItem(it) }
        }
    }

    fun goTo(group: BudgetGroup?) = goTo(
        when (group) {
            BudgetGroup.TITHE, BudgetGroup.FIXED -> Section.FIXED.name
            BudgetGroup.VARIABLE -> Section.COUPLE.name
            BudgetGroup.INVESTMENT -> Section.INVESTMENT.name
            null -> Section.EXTRA.name
        }
    )

    fun editFromList(expense: ExpenseEntity) {
        returnToList = expenseList
        expenseList = null
        editingExpense = expense
    }

    fun closeEditor() {
        editingExpense = null
        expenseList = returnToList
        returnToList = null
    }

    /** Shows [message] with "Desfazer", replacing any snackbar still on screen instead of queueing behind it. */
    fun offerUndo(message: String, duration: SnackbarDuration = SnackbarDuration.Short, undo: () -> Unit) {
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            if (snackbar.showSnackbar(message, "Desfazer", duration = duration) == SnackbarResult.ActionPerformed) undo()
        }
    }

    fun deleteWithUndo(expense: ExpenseEntity) {
        viewModel.deleteExpense(expense)
        offerUndo("\"${expense.description}\" excluído") { viewModel.restoreExpense(expense) }
    }

    fun deleteItemWithUndo(item: SubcategoryItemEntity) {
        viewModel.deleteItem(item)
        offerUndo("\"${item.description}\" excluído") { viewModel.restoreItem(item) }
    }

    fun togglePaidWithUndo(expense: ExpenseEntity) {
        viewModel.togglePaid(expense)
        val message = if (expense.isPaid) "\"${expense.description}\" reaberto" else "\"${expense.description}\" pago"
        offerUndo(message) { viewModel.restoreExpenses(listOf(expense)) }
    }

    val detail = state.detail
    val summary = state.summary
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (detail != null) {
                ExtendedFloatingActionButton(
                    onClick = { showNewEntry = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Adicionar") },
                )
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Menu") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.step(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior")
                        }
                        Box {
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable(enabled = state.months.isNotEmpty()) { showPicker = true }
                                    .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    detail?.month?.let { monthLabel(it.year, it.month) } ?: "Controle Financeiro",
                                    style = MaterialTheme.typography.titleMedium, maxLines = 1,
                                )
                                if (state.months.isNotEmpty()) Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = showPicker, onDismissRequest = { showPicker = false }) {
                                state.months.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(monthLabel(m.year, m.month)) },
                                        onClick = { viewModel.selectMonth(m.id); showPicker = false },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Excluir este mês", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showPicker = false; confirmDeleteMonth = true },
                                    enabled = detail != null,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.step(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = true }) { Icon(Icons.Outlined.Search, "Buscar") }
                    FilledTonalIconButton(onClick = { showNewMonth = true }, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Default.Add, "Novo mês")
                    }
                },
            )
        },
    ) { padding ->
        if (detail == null || summary == null) {
            EmptyMonth(Modifier.padding(padding), loading = state.loading) { showNewMonth = true }
        } else {
            val blockColors = LocalBlockColors.current
            val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
            // Pulling the month down checks the spreadsheet right away.
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { viewModel.refresh() }, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Counts items as they are added, so taps in the overview know where each section starts.
                var count = 0
                fun single(key: String, anchor: String? = null, content: @Composable () -> Unit) {
                    anchor?.let { anchors[it] = count }
                    item(key) { content() }
                    count++
                }
                fun expenseRows(list: List<ExpenseEntity>, color: Color) {
                    itemsIndexed(list, key = { _, it -> "e-${it.id}" }) { index, expense ->
                        val itemCount = detail.items.count { it.expenseId == expense.id }
                        ExpenseRow(expense, viewModel.resolvedPlanned(expense), state.reference, color,
                            shape = groupShape(index, list.size),
                            onToggle = { togglePaidWithUndo(expense) },
                            onClick = { if (expense.subcategoryId != null || itemCount > 0) openRowId = expense.id else editingExpense = expense },
                            itemCount = itemCount,
                            onDelete = { deleteWithUndo(expense) })
                    }
                    count += list.size
                }

                // Always present (even empty), so the list does not stay scrolled past it when it appears.
                single("updated") { detail.month.updatedAt?.let { UpdatedLine(it, detail.month.updatedInSheet) } }
                // The overview cards, in the order the user arranged them by dragging.
                cardOrder.forEachIndexed { index, key ->
                    val anchor = when (key) {
                        "usage" -> ANCHOR_USAGE
                        "groups" -> ANCHOR_GROUPS
                        else -> null
                    }
                    single(key, anchor) {
                        Box(Modifier.reorderableCard(key).padding(top = if (index > 0) 12.dp else 0.dp)) {
                            when (key) {
                                "overview" -> {
                                    val hideValues by viewModel.hideValues.collectAsStateWithLifecycle()
                                    HeroCard(
                                        summary, hideValues, onToggleHidden = viewModel::toggleHideValues,
                                        onIncome = { goTo(ANCHOR_INCOME) }, onBudget = { goTo(ANCHOR_GROUPS) },
                                        onSurplus = { goTo(ANCHOR_USAGE) },
                                    )
                                }
                                "cards" -> CardsCard(summary, state.reference, Modifier) { card ->
                                    expenseList = if (card == null) ExpenseFilter.AnyCard else ExpenseFilter.ByCard(card.id, "Cartão ${card.name}")
                                }
                                "tracker" -> TrackerCard(
                                    state.reference.subcategories.filter { it.inTracker }, detail.expenses, Modifier,
                                    onOpen = { sub ->
                                        val row = detail.expenses.firstOrNull { it.subcategoryId == sub.id }
                                        if (row != null) openRowId = row.id else editingItem = null to sub.id
                                    },
                                    onChoose = { showTrackerChooser = true },
                                )
                                "usage" -> IncomeUsageCard(summary, Modifier) { goTo(ANCHOR_GROUPS) }
                                "groups" -> GroupsCard(summary, Modifier) { goTo(it) }
                                "couple" -> CoupleCard(
                                    summary, Modifier,
                                    onEditAllowance = { editingAllowance = it },
                                    onPerson = { goTo("person-${it.id}") },
                                    onAllowances = { goTo(Section.PERSONAL.name) },
                                    onCouple = { goTo(Section.COUPLE.name) },
                                )
                            }
                        }
                    }
                }

                // Income
                single("income-header", ANCHOR_INCOME) {
                    SectionHeader(
                        title = "Renda", icon = Icons.Outlined.Payments, color = MaterialTheme.colorScheme.primary,
                        subtitle = "${detail.incomes.size} entradas · ${Money.format(summary.incomeCents)}",
                        collapsed = isCollapsed(ANCHOR_INCOME),
                        onToggle = { collapsed[ANCHOR_INCOME] = !isCollapsed(ANCHOR_INCOME) },
                        onAdd = { editingIncome = IncomeEntity(monthId = detail.month.id, personId = null, source = "", amountCents = 0) },
                    )
                }
                if (!isCollapsed(ANCHOR_INCOME)) {
                    itemsIndexed(detail.incomes.sortedByDescending { it.id }, key = { _, it -> "income-${it.id}" }) { index, income ->
                        IncomeRow(
                            income,
                            state.reference.people.firstOrNull { it.id == income.personId }?.name?.let(::shortName) ?: "Casal",
                            groupShape(index, detail.incomes.size),
                        ) { editingIncome = income }
                    }
                    count += detail.incomes.size
                }

                Section.entries.forEach { section ->
                    val list = detail.expenses.filter { it.block in section.blocks }.newestFirst()
                    val color = blockColors.of(section.newBlock)
                    val total = list.sumOf { BudgetCalculator.actual(it) }
                    val planned = if (section == Section.PERSONAL) summary.totalAllowanceCents
                    else list.sumOf { viewModel.resolvedPlanned(it) }
                    single("header-${section.name}", section.name) {
                        SectionHeader(
                            title = section.title,
                            icon = section.icon,
                            color = color,
                            subtitle = "${list.count { it.isPaid }} de ${list.size} pagos · ${Money.format(total)} de ${Money.format(planned)}",
                            progress = total to planned,
                            collapsed = isCollapsed(section.name),
                            onToggle = { collapsed[section.name] = !isCollapsed(section.name) },
                            onAdd = {
                                editingExpense = ExpenseEntity(
                                    monthId = detail.month.id, block = section.newBlock, description = "",
                                    personId = if (section == Section.PERSONAL) state.reference.people.firstOrNull()?.id else null,
                                )
                            },
                        )
                    }
                    if (!isCollapsed(section.name)) {
                        if (section == Section.PERSONAL) {
                            state.reference.people.forEach { person ->
                                val personal = list.filter { it.personId == person.id }
                                val line = summary.people.firstOrNull { it.person.id == person.id }
                                single("person-${person.id}", "person-${person.id}") {
                                    PersonSubheader(shortName(person.name), line?.spentCents ?: 0, line?.allowanceCents ?: 0, color)
                                }
                                expenseRows(personal, color)
                                if (personal.isEmpty()) single("empty-person-${person.id}") { EmptyLine("Nenhum gasto de ${shortName(person.name)}.") }
                            }
                        } else {
                            expenseRows(list, color)
                            if (list.isEmpty()) single("empty-${section.name}") { EmptyLine("Nenhum lançamento. Toque em + para adicionar.") }
                        }
                    }
                }

                single("methods") {
                    PaymentMethodsCard(summary, state.reference, Modifier.padding(top = 20.dp)) { methodId, name ->
                        expenseList = ExpenseFilter.ByMethod(methodId, name)
                    }
                }
            }
            }
        }
    }

    val budgetAlert by viewModel.budgetAlert.collectAsStateWithLifecycle()
    budgetAlert?.let { BudgetAlertDialog(it, onDismiss = viewModel::dismissBudgetAlert) }
    if (showSearch) {
        val query by viewModel.searchQuery.collectAsStateWithLifecycle()
        val results by viewModel.searchResults.collectAsStateWithLifecycle()
        SearchScreen(
            query = query,
            results = results,
            onQuery = { viewModel.searchQuery.value = it },
            onOpen = { hit ->
                showSearch = false
                viewModel.selectMonth(hit.month.id)
                when {
                    hit.item != null -> editingItem = hit.item to hit.expense.subcategoryId
                    hit.expense.subcategoryId != null -> openRowId = hit.expense.id
                    else -> editingExpense = hit.expense
                }
            },
            onDismiss = { showSearch = false },
        )
    }
    if (showNewEntry && detail != null) {
        NewEntrySheet(
            onItem = { showNewEntry = false; editingItem = null to null },
            onExpense = {
                showNewEntry = false
                editingExpense = ExpenseEntity(monthId = detail.month.id, block = ExpenseBlock.COUPLE, description = "")
            },
            onSubcategory = { showNewEntry = false; newSubcategory() },
            onDismiss = { showNewEntry = false },
        )
    }
    openRowId?.let { rowId ->
        val row = detail?.expenses?.firstOrNull { it.id == rowId }
        if (row == null) {
            openRowId = null
        } else {
            SubcategoryRowSheet(
                row = row,
                items = detail.items.filter { it.expenseId == rowId }.newestFirstItems(),
                onAddItem = { editingItem = SubcategoryItemEntity(expenseId = rowId, description = "", amountCents = 0).takeIf { row.subcategoryId == null } to row.subcategoryId },
                onEditItem = { editingItem = it to row.subcategoryId },
                onDeleteItem = ::deleteItemWithUndo,
                onEditRow = { openRowId = null; editingExpense = row },
                onDismiss = { openRowId = null },
            )
        }
    }
    editingItem?.let { (item, subId) ->
        val month = detail?.month
        if (month != null) {
            ItemEditorSheet(
                initial = item?.takeIf { it.id != 0L },
                subcategoryId = subId,
                subcategories = state.reference.subcategories,
                year = month.year,
                month = month.month,
                onSave = { chosen, saved -> viewModel.saveItem(chosen, saved.copy(expenseId = saved.expenseId.takeIf { it != 0L } ?: item?.expenseId ?: 0), item?.takeIf { it.id != 0L }); editingItem = null },
                onDelete = { editingItem = null; deleteItemWithUndo(it) },
                onNewSubcategory = { editingItem = null; newSubcategory() },
                onDismiss = { editingItem = null },
                hasRow = item != null,
            )
        }
    }
    editingSubcategory?.let { sub ->
        SubcategoryEditorSheet(
            initial = sub,
            people = state.reference.people,
            categories = state.reference.categories,
            paymentMethods = state.reference.paymentMethods,
            cards = state.reference.cards,
            onSave = { viewModel.saveSubcategory(it); editingSubcategory = null },
            onDelete = if (sub.id == 0L) null else { s -> viewModel.deleteSubcategory(s); editingSubcategory = null },
            onDismiss = { editingSubcategory = null },
            takenNames = state.reference.subcategories.filter { it.id != sub.id }.map { it.name.nameKey() }.toSet(),
        )
    }
    if (showTrackerChooser) {
        TrackerChooserDialog(
            state.reference.subcategories,
            onSave = { viewModel.setTracked(it); showTrackerChooser = false },
            onNew = { showTrackerChooser = false; newSubcategory() },
            onDismiss = { showTrackerChooser = false },
        )
    }
    editingExpense?.let { expense ->
        ExpenseEditorSheet(
            paidFromItems = detail?.items.orEmpty().any { it.expenseId == expense.id },
            initial = expense,
            reference = state.reference,
            onSave = { viewModel.saveExpense(it); closeEditor() },
            onDelete = if (expense.id == 0L) null else { e -> closeEditor(); deleteWithUndo(e) },
            onDismiss = ::closeEditor,
        )
    }
    expenseList?.let { filter ->
        ExpenseListSheet(
            filter = filter,
            expenses = detail?.expenses.orEmpty().filter(filter::matches).newestFirst(),
            reference = state.reference,
            plannedOf = viewModel::resolvedPlanned,
            onToggle = ::togglePaidWithUndo,
            onClick = ::editFromList,
            onDelete = ::deleteWithUndo,
            onPayAll = { open -> payingAll = filter to open },
            snackbar = snackbar,
            onDismiss = { expenseList = null },
        )
    }
    payingAll?.let { (filter, open) ->
        PayAllDialog(
            title = filter.title,
            count = open.size,
            totalCents = open.sumOf { viewModel.resolvedPlanned(it) },
            onConfirm = { onDay15 ->
                viewModel.payAll(open, onDay15)
                payingAll = null
                expenseList = null
                val label = if (open.size == 1) "1 item baixado" else "${open.size} itens baixados"
                offerUndo("$label · ${filter.title}", SnackbarDuration.Long) { viewModel.restoreExpenses(open) }
            },
            onDismiss = { payingAll = null },
        )
    }
    editingIncome?.let { income ->
        IncomeEditorSheet(
            initial = income,
            people = state.reference.people,
            onSave = { viewModel.saveIncome(it); editingIncome = null },
            onDelete = if (income.id == 0L) null else { i -> viewModel.deleteIncome(i); editingIncome = null },
            onDismiss = { editingIncome = null },
        )
    }
    editingAllowance?.let { person ->
        AllowanceDialog(
            person = person,
            currentCents = summary?.people?.firstOrNull { it.person.id == person.id }?.allowanceCents ?: 0,
            onSave = { viewModel.setAllowance(person.id, it); editingAllowance = null },
            onDismiss = { editingAllowance = null },
        )
    }
    if (showNewMonth) {
        val latest = state.months.firstOrNull()
        val today = LocalDate.now()
        val (y, m) = when {
            latest == null -> today.year to today.monthValue
            latest.month == 12 -> latest.year + 1 to 1
            else -> latest.year to latest.month + 1
        }
        NewMonthDialog(y, m, onCreate = { year, month, copy -> viewModel.createMonth(year, month, copy); showNewMonth = false },
            onDismiss = { showNewMonth = false })
    }
    if (confirmDeleteMonth && detail != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteMonth = false },
            icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Excluir ${monthLabel(detail.month.year, detail.month.month)}?") },
            text = { Text("Todos os lançamentos, rendas e mesadas deste mês serão apagados.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMonth(detail.month); confirmDeleteMonth = false }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteMonth = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EmptyMonth(modifier: Modifier, loading: Boolean, onCreate: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!loading) {
            IconBadge(Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.primary, size = 88.dp)
            Spacer(Modifier.height(20.dp))
            Text("Nenhum mês criado ainda", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Crie o primeiro mês para lançar renda, contas e mesadas.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCreate, modifier = Modifier.height(52.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Criar mês")
            }
        }
    }
}

/** Same text as cell B3 of the month's tab: "Última atualização: 28/10/2026 15:34 (na planilha)". */
@Composable
private fun UpdatedLine(at: Long, inSheet: Boolean) {
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Update, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            "Última atualização: ${time.format(UPDATED_FORMAT)} (${if (inSheet) "na planilha" else "pelo app"})",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val UPDATED_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** Gradient card at the top: what is left of the income, savings rate and a ring with how much of it is used. */
@Composable
private fun HeroCard(
    summary: MonthSummary,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onIncome: () -> Unit,
    onBudget: () -> Unit,
    onSurplus: () -> Unit,
) {
    val hero = LocalHeroColors.current
    val used = ratioOf(summary.actualCents, summary.incomeCents)
    val ringColor = when {
        summary.incomeCents <= 0 -> hero.muted
        used > 1f -> Color(0xFFFF8A7E)
        used >= 0.9f -> Color(0xFFFF9F43)
        else -> Color(0xFFFFD400)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(hero.brush)
            .padding(24.dp),
    ) {
        Row(Modifier.clickable(onClick = onSurplus), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sobra do mês", style = MaterialTheme.typography.labelLarge, color = hero.muted)
                    // One eye hides or shows every number of this card.
                    Icon(
                        if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (hidden) "Mostrar valores" else "Esconder valores",
                        Modifier.clip(CircleShape).clickable(onClick = onToggleHidden).padding(8.dp).size(18.dp),
                        tint = hero.muted,
                    )
                }
                if (hidden) {
                    Text(HIDDEN_MONEY, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = hero.content)
                } else {
                    MoneyText(summary.surplusCents, style = MaterialTheme.typography.displaySmall, color = hero.content)
                }
                Spacer(Modifier.height(10.dp))
                Pill(
                    "Poupança ${if (hidden) "••%" else Money.formatPercent(summary.savingsRate)}",
                    color = hero.content, container = hero.tile,
                )
            }
            Spacer(Modifier.width(12.dp))
            UsageRing(if (hidden) 0f else used, ringColor, hero.tile, Modifier.size(104.dp), strokeWidth = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            hidden -> "••%"
                            summary.incomeCents > 0 -> "${(used * 100).toInt()}%"
                            else -> "–"
                        },
                        style = MaterialTheme.typography.titleLarge, color = hero.content,
                    )
                    Text("da renda", style = MaterialTheme.typography.labelMedium, color = hero.muted)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroTile("Renda", summary.incomeCents, hidden, Modifier.weight(1f), onIncome)
            HeroTile("Previsto", summary.plannedCents, hidden, Modifier.weight(1f), onBudget)
            HeroTile("Realizado", summary.actualCents, hidden, Modifier.weight(1f), onBudget)
        }
    }
}

@Composable
private fun HeroTile(label: String, cents: Long, hidden: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val hero = LocalHeroColors.current
    val tile = modifier.clip(MaterialTheme.shapes.medium).background(hero.tile).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp)
    if (hidden) {
        Column(tile) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = hero.muted)
            Text(HIDDEN_MONEY, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = hero.content, maxLines = 1)
        }
    } else {
        LabelValue(label, cents, tile, labelColor = hero.muted, valueColor = hero.content)
    }
}

private const val HIDDEN_MONEY = "R$ •••••"

/** Same two checks as the spreadsheet summary: actual and planned against the income, without "Fora do orçamento". */
@Composable
private fun IncomeUsageCard(summary: MonthSummary, modifier: Modifier, onClick: () -> Unit) {
    SectionCard(modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick), title = "Renda do mês",
        subtitle = "Sem contar os gastos fora do orçamento") {
        UsageLine("Uso da renda", summary.actualCents, summary.incomeCents)
        Spacer(Modifier.height(18.dp))
        UsageLine("Previsto vs renda", summary.plannedCents, summary.incomeCents)
    }
}

@Composable
private fun UsageLine(label: String, used: Long, income: Long) {
    val color = usageColor(used, income)
    val status = when {
        income <= 0 -> "Sem renda lançada"
        used > income -> "Passou ${Money.format(used - income)} da renda"
        else -> "Faltam ${Money.format(income - used)} para a renda"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = color)
        }
        Pill(if (income > 0) Money.formatPercent(used.toDouble() / income) else "–", color)
    }
    Spacer(Modifier.height(10.dp))
    BudgetBar(used, income, color)
}

@Composable
private fun GroupsCard(summary: MonthSummary, modifier: Modifier, onGroup: (BudgetGroup?) -> Unit) {
    val blocks = LocalBlockColors.current
    SectionCard(modifier, title = "Orçamento por grupo", subtitle = "Realizado de previsto") {
        summary.groups.forEachIndexed { index, line ->
            val color = line.group.color(blocks)
            if (index > 0) Spacer(Modifier.height(10.dp))
            Column(Modifier.clickable { onGroup(line.group) }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(line.group.icon(), color)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(line.group.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "Recomendado ${Money.format(line.recommendedCents)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(line.actualCents, style = MaterialTheme.typography.titleSmall)
                        Text("de ${Money.format(line.plannedCents)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                BudgetBar(line.actualCents, line.plannedCents, color)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { onGroup(null) }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Outlined.MoreHoriz, blocks.extra, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text("Fora do orçamento", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            MoneyText(summary.extraActualCents, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun CoupleCard(
    summary: MonthSummary,
    modifier: Modifier,
    onEditAllowance: (PersonEntity) -> Unit,
    onPerson: (PersonEntity) -> Unit,
    onAllowances: () -> Unit,
    onCouple: () -> Unit,
) {
    val purple = LocalBlockColors.current.personal
    SectionCard(
        modifier,
        title = summary.people.joinToString(" e ") { shortName(it.person.name) }.ifEmpty { "Mesadas" },
        subtitle = "Mesadas e divisão das contas pela renda",
    ) {
        summary.people.forEachIndexed { index, line ->
            if (index > 0) Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { onPerson(line.person) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(line.person.name, purple)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shortName(line.person.name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${Money.formatPercent(line.incomeShare)} da renda · casal ${Money.format(line.coupleShareCents)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    Modifier.clip(CircleShape).background(purple.copy(alpha = 0.12f))
                        .clickable { onEditAllowance(line.person) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Money.format(line.allowanceCents), style = MaterialTheme.typography.labelLarge, color = purple)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Edit, "Editar mesada", Modifier.size(14.dp), tint = purple)
                }
            }
            Spacer(Modifier.height(12.dp))
            BudgetBar(line.spentCents, line.allowanceCents, purple)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gasto ${Money.format(line.spentCents)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val money = LocalMoneyColors.current
                Pill("Saldo ${Money.format(line.balanceCents)}", if (line.balanceCents < 0) money.negative else money.positive)
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.clickable(onClick = onAllowances)) {
                Text("Total das mesadas", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                MoneyText(summary.totalAllowanceCents, style = MaterialTheme.typography.titleSmall)
            }
            Row(Modifier.clickable(onClick = onAllowances)) {
                Text("Gasto / saldo", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${Money.format(summary.totalPersonalSpentCents)} / ", style = MaterialTheme.typography.bodyMedium)
                MoneyText(summary.totalAllowanceCents - summary.totalPersonalSpentCents, style = MaterialTheme.typography.bodyMedium, colored = true)
            }
            Row(Modifier.clickable(onClick = onCouple)) {
                Text("Contas do casal no mês", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(summary.coupleActualCents), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    color: Color,
    subtitle: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    progress: Pair<Long, Long>? = null,
) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onToggle).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, color, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Default.ExpandMore, if (collapsed) "Expandir" else "Recolher",
                        Modifier.size(20.dp).rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalIconButton(
                onClick = onAdd,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = color.copy(alpha = 0.14f), contentColor = color),
            ) { Icon(Icons.Default.Add, "Adicionar em $title") }
        }
        if (progress != null && progress.second > 0) {
            Spacer(Modifier.height(8.dp))
            BudgetBar(progress.first, progress.second, color, height = 4.dp)
        }
    }
}

@Composable
private fun PersonSubheader(name: String, spent: Long, allowance: Long, color: Color) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, color, size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text("${Money.format(spent)} de ${Money.format(allowance)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseRow(
    expense: ExpenseEntity,
    plannedCents: Long,
    reference: ReferenceData,
    color: Color,
    shape: Shape,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    itemCount: Int = 0,
) {
    // Swipe right marks paid (or reopens), swipe left deletes. A quick vertical scroll with a little
    // sideways drift passes the swipe's velocity threshold, so only a drag that really travelled
    // 40% of the width counts.
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnDelete by rememberUpdatedState(onDelete)
    var rowWidth by remember { mutableFloatStateOf(0f) }
    val stateRef = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            val dragged = abs(stateRef[0]?.requireOffset() ?: 0f)
            if (rowWidth > 0f && dragged >= rowWidth * SWIPE_FRACTION) {
                when (it) {
                    SwipeToDismissBoxValue.StartToEnd -> currentOnToggle()
                    SwipeToDismissBoxValue.EndToStart -> currentOnDelete()
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
            false
        },
        positionalThreshold = { distance -> distance * SWIPE_FRACTION },
    )
    stateRef[0] = dismissState
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.onSizeChanged { rowWidth = it.width.toFloat() },
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Row(
                    Modifier.fillMaxSize().clip(shape).background(color.copy(alpha = 0.18f)).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (expense.isPaid) Icons.AutoMirrored.Filled.Undo else Icons.Default.Check, null, tint = color)
                    Spacer(Modifier.width(8.dp))
                    Text(if (expense.isPaid) "Reabrir" else "Dar baixa", style = MaterialTheme.typography.labelLarge, color = color)
                }
                SwipeToDismissBoxValue.EndToStart -> Box(
                    Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) { Icon(Icons.Outlined.DeleteOutline, "Excluir", tint = MaterialTheme.colorScheme.onErrorContainer) }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        val actual = BudgetCalculator.actual(expense)
        val details = listOfNotNull(
            reference.categories.firstOrNull { it.id == expense.categoryId }?.name,
            // A linked card says more than "Cartão de crédito", and keeps the line short.
            reference.cards.firstOrNull { it.id == expense.cardId }?.let { "Cartão ${it.name}" }
                ?: reference.paymentMethods.firstOrNull { it.id == expense.paymentMethodId }?.name,
            expense.dueDay?.let { "dia $it" },
            when {
                itemCount == 1 -> "1 item"
                itemCount > 1 -> "$itemCount itens"
                else -> null
            },
        ).joinToString(" · ")
        Row(
            Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    expense.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (expense.isPaid) TextDecoration.LineThrough else null,
                    color = if (expense.isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(actual, style = MaterialTheme.typography.titleSmall)
                Text("meta ${Money.format(plannedCents)}", style = MaterialTheme.typography.bodySmall,
                    color = when {
                        // Subcategory rows: red above the target, blue on it, green below.
                        expense.subcategoryId != null -> budgetStatusColor(actual, plannedCents)
                        actual > plannedCents && plannedCents > 0 -> LocalMoneyColors.current.negative
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
            }
        }
    }
}

@Composable
private fun IncomeRow(income: IncomeEntity, owner: String, shape: Shape, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(owner, MaterialTheme.colorScheme.primary, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(income.source, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(owner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoneyText(income.amountCents, style = MaterialTheme.typography.titleSmall, colored = true)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Expected (planned) against spent (paid) on each card this month. */
@Composable
private fun CardsCard(summary: MonthSummary, reference: ReferenceData, modifier: Modifier, onCard: (CardEntity?) -> Unit) {
    val color = MaterialTheme.colorScheme.tertiary
    SectionCard(modifier, title = "Cartões", subtitle = "Gasto de esperado no mês") {
        if (reference.cards.isEmpty()) {
            Text(
                "Cadastre seus cartões em Ajustes e escolha o cartão em cada lançamento.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val lines = reference.cards
            .map { card -> card to (summary.byCard[card.id] ?: (0L to 0L)) }
            .sortedByDescending { (_, values) -> values.first }
        lines.forEachIndexed { index, (card, values) ->
            val (expected, spent) = values
            if (index > 0) Spacer(Modifier.height(8.dp))
            Column(Modifier.clickable { onCard(card) }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.CreditCard, color, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            cardStatus(expected, spent), style = MaterialTheme.typography.bodySmall,
                            color = if (spent > expected) LocalMoneyColors.current.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(spent, style = MaterialTheme.typography.titleSmall)
                        Text("de ${Money.format(expected)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                BudgetBar(spent, expected, color, height = 6.dp)
            }
        }
        if (lines.size > 1) {
            val expected = lines.sumOf { it.second.first }
            val spent = lines.sumOf { it.second.second }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onCard(null) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total nos cartões", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                MoneyText(spent, style = MaterialTheme.typography.titleSmall)
                Text(" de ${Money.format(expected)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun cardStatus(expected: Long, spent: Long): String = when {
    expected == 0L && spent == 0L -> "Nada lançado neste mês"
    spent == expected -> "Fechou no esperado"
    spent > expected -> "Passou ${Money.format(spent - expected)} do esperado"
    else -> "Faltam ${Money.format(expected - spent)}"
}

@Composable
private fun PaymentMethodsCard(summary: MonthSummary, reference: ReferenceData, modifier: Modifier, onMethod: (Long?, String) -> Unit) {
    val color = MaterialTheme.colorScheme.secondary
    SectionCard(modifier, title = "Por forma de pagamento", subtitle = "Realizado de previsto · toque para ver os itens") {
        summary.byPaymentMethod.entries.sortedByDescending { it.value.first }.forEachIndexed { index, (methodId, values) ->
            val name = reference.paymentMethods.firstOrNull { it.id == methodId }?.name
            if (index > 0) Spacer(Modifier.height(6.dp))
            Column(
                Modifier.clickable { onMethod(methodId, name ?: NO_METHOD) }
                    .padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.CreditCard, if (name == null) MaterialTheme.colorScheme.primary else color, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    if (name == null) {
                        Column(Modifier.weight(1f)) {
                            Text(NO_METHOD, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary)
                            Text("Toque para definir a forma", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("${Money.format(values.second)} de ${Money.format(values.first)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                BudgetBar(values.second, values.first, color, height = 4.dp)
            }
        }
    }
}

private const val NO_METHOD = "Sem forma"
private const val SWIPE_FRACTION = 0.4f
private const val ANCHOR_INCOME = "income"
private const val ANCHOR_USAGE = "usage"
private const val ANCHOR_GROUPS = "groups"

/** Cross-cutting lists opened from the overview: one card, all cards or one payment method. */
private sealed interface ExpenseFilter {
    val title: String
    fun matches(expense: ExpenseEntity): Boolean

    data class ByCard(val cardId: Long, override val title: String) : ExpenseFilter {
        override fun matches(expense: ExpenseEntity) = expense.cardId == cardId
    }

    data object AnyCard : ExpenseFilter {
        override val title = "Todos os cartões"
        override fun matches(expense: ExpenseEntity) = expense.cardId != null
    }

    /** A null [methodId] lists the expenses that still have no payment method. */
    data class ByMethod(val methodId: Long?, override val title: String) : ExpenseFilter {
        override fun matches(expense: ExpenseEntity) = expense.paymentMethodId == methodId
    }
}

/** Bottom sheet with the month's expenses that match [filter]; tapping one opens the editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseListSheet(
    filter: ExpenseFilter,
    expenses: List<ExpenseEntity>,
    reference: ReferenceData,
    plannedOf: (ExpenseEntity) -> Long,
    onToggle: (ExpenseEntity) -> Unit,
    onClick: (ExpenseEntity) -> Unit,
    onDelete: (ExpenseEntity) -> Unit,
    onPayAll: (List<ExpenseEntity>) -> Unit,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val blocks = LocalBlockColors.current
    val isCard = filter is ExpenseFilter.ByCard || filter is ExpenseFilter.AnyCard
    val open = expenses.filter { !it.isPaid }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(filter.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "${expenses.size} ${if (expenses.size == 1) "lançamento" else "lançamentos"} · " +
                    "${Money.format(expenses.sumOf { BudgetCalculator.actual(it) })} " +
                    "de ${Money.format(expenses.sumOf(plannedOf))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filter is ExpenseFilter.ByMethod && filter.methodId == null && expenses.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Toque num item para escolher a forma de pagamento.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (isCard && expenses.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                if (open.isEmpty()) {
                    Text("Tudo pago neste cartão.", style = MaterialTheme.typography.bodyMedium,
                        color = LocalMoneyColors.current.positive)
                } else {
                    FilledTonalButton(onClick = { onPayAll(open) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DoneAll, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (open.size == 1) "Dar baixa no item em aberto" else "Dar baixa nos ${open.size} itens em aberto")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Box {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (expenses.isEmpty()) {
                    val text = if (filter is ExpenseFilter.ByMethod && filter.methodId == null) "Todos os lançamentos já têm forma de pagamento."
                    else "Nenhum lançamento neste mês."
                    item("empty") { EmptyLine(text) }
                }
                itemsIndexed(expenses, key = { _, it -> "list-${it.id}" }) { index, expense ->
                    ExpenseRow(expense, plannedOf(expense), reference, blocks.of(expense.block),
                        shape = groupShape(index, expenses.size),
                        onToggle = { onToggle(expense) },
                        onClick = { onClick(expense) },
                        onDelete = { onDelete(expense) })
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Asks on which day a card's open items were paid; each one is then paid with its planned value on that day. */
@Composable
private fun PayAllDialog(title: String, count: Int, totalCents: Long, onConfirm: (onDay15: Boolean) -> Unit, onDismiss: () -> Unit) {
    var onDay15 by remember { mutableStateOf(LocalDate.now().dayOfMonth <= 15) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DoneAll, null) },
        title = { Text("Dar baixa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "$title: ${if (count == 1) "o item em aberto fica pago" else "os $count itens em aberto ficam pagos"} com o valor " +
                        "esperado de cada um (${Money.format(totalCents)} no total). Quando foi pago?"
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(true to "Dia 15", false to "Dia 30").forEachIndexed { index, (day15, label) ->
                        SegmentedButton(
                            selected = onDay15 == day15,
                            onClick = { onDay15 = day15 },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text(label) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(onDay15) }) { Text("Dar baixa") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Lists show the latest additions first (rows from before the time was recorded follow their insertion order). */
fun List<ExpenseEntity>.newestFirst(): List<ExpenseEntity> =
    sortedWith(compareByDescending<ExpenseEntity> { it.createdAt }.thenByDescending { it.id })

fun List<SubcategoryItemEntity>.newestFirstItems(): List<SubcategoryItemEntity> =
    sortedWith(compareByDescending<SubcategoryItemEntity> { it.createdAt }.thenByDescending { it.id })
