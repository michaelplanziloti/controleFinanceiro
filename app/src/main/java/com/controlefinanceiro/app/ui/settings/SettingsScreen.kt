package com.controlefinanceiro.app.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlefinanceiro.app.data.SheetsConfig
import com.controlefinanceiro.app.data.ThemeMode
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.data.db.ExpenseBlock
import com.controlefinanceiro.app.data.db.SubcategoryEntity
import com.controlefinanceiro.app.data.sheets.nameKey
import com.controlefinanceiro.app.ui.common.IconBadge
import com.controlefinanceiro.app.ui.common.Money
import com.controlefinanceiro.app.ui.common.SectionCard
import com.controlefinanceiro.app.ui.common.groupShape
import com.controlefinanceiro.app.ui.month.SubcategoryEditorSheet
import com.controlefinanceiro.app.ui.month.title

/** Each settings title in the menu opens its own screen. */
enum class SettingsPage(val title: String, val subtitle: String, val icon: ImageVector) {
    THEME("Aparência", "Claro, escuro ou do sistema", Icons.Outlined.Palette),
    PEOPLE("Pessoas com mesada", "Quem recebe mesada", Icons.Outlined.People),
    CATEGORIES("Categorias", "Listas dos lançamentos", Icons.Outlined.Label),
    METHODS("Formas de pagamento", "Pix, débito, crédito...", Icons.Outlined.Wallet),
    CARDS("Cartões", "Gasto de cada cartão no mês", Icons.Outlined.CreditCard),
    SUBCATEGORIES("Subcategorias", "Mercado, Gasolina... com meta", Icons.Outlined.Category),
    BUDGET("Regra de orçamento", "Dízimo, fixo, variável e investimento", Icons.Outlined.PieChart),
    SHEETS("Planilha Google", "Sincronização com a planilha", Icons.Outlined.TableChart),
}

/** The side menu opened by the three bars: only the settings titles, plus "Sair". */
@Composable
fun SettingsDrawer(onOpen: (SettingsPage) -> Unit, onLogout: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text("Controle Financeiro", Modifier.padding(horizontal = 28.dp, vertical = 16.dp), style = MaterialTheme.typography.titleLarge)
            Text("Ajustes", Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsPage.entries.forEach { page ->
                NavigationDrawerItem(
                    label = { Text(page.title) },
                    icon = { Icon(page.icon, null) },
                    selected = false,
                    onClick = { onOpen(page) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
            NavigationDrawerItem(
                label = { Text("Sair") },
                icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                selected = false,
                onClick = onLogout,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScreen(page: SettingsPage, viewModel: SettingsViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by remember(page) { mutableStateOf(false) }
    var editingSubcategory by remember { mutableStateOf<SubcategoryEntity?>(null) }
    val canAdd = page in setOf(SettingsPage.PEOPLE, SettingsPage.CATEGORIES, SettingsPage.METHODS, SettingsPage.CARDS, SettingsPage.SUBCATEGORIES)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(page.title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (canAdd) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (page == SettingsPage.SUBCATEGORIES) {
                            editingSubcategory = SubcategoryEntity(userId = 0, name = "", block = ExpenseBlock.COUPLE, plannedCents = 0, inTracker = true)
                        } else {
                            adding = true
                        }
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Adicionar") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                SettingsPage.THEME -> ThemePage(state.theme, viewModel::setTheme)
                SettingsPage.PEOPLE -> NamesPage(
                    hint = "Quem tem mesada no mês. Remover uma pessoa apaga as mesadas dela.",
                    names = state.people.map { it.id to it.name },
                    noun = "pessoa",
                    adding = adding, onAddingDone = { adding = false },
                    onAdd = viewModel::addPerson,
                    onRename = { id, name -> state.people.firstOrNull { it.id == id }?.let { viewModel.renamePerson(it, name) } },
                    onDelete = { id -> state.people.firstOrNull { it.id == id }?.let(viewModel::deletePerson) },
                    deleteWarning = "As mesadas dessa pessoa em todos os meses também serão apagadas.",
                )
                SettingsPage.CATEGORIES -> NamesPage(
                    hint = "As categorias aparecem nos lançamentos e no resumo do ano.",
                    names = state.categories.map { it.id to it.name },
                    noun = "categoria",
                    adding = adding, onAddingDone = { adding = false },
                    onAdd = viewModel::addCategory,
                    onRename = { id, name -> state.categories.firstOrNull { it.id == id }?.let { viewModel.renameCategory(it, name) } },
                    onDelete = { id -> state.categories.firstOrNull { it.id == id }?.let(viewModel::deleteCategory) },
                    deleteWarning = "Os lançamentos com essa categoria ficam sem categoria.",
                )
                SettingsPage.METHODS -> NamesPage(
                    hint = "Como cada conta é paga. Para cartões, use Cartões.",
                    names = state.paymentMethods.map { it.id to it.name },
                    noun = "forma de pagamento",
                    adding = adding, onAddingDone = { adding = false },
                    onAdd = viewModel::addPaymentMethod,
                    onRename = { id, name -> state.paymentMethods.firstOrNull { it.id == id }?.let { viewModel.renamePaymentMethod(it, name) } },
                    onDelete = { id -> state.paymentMethods.firstOrNull { it.id == id }?.let(viewModel::deletePaymentMethod) },
                    deleteWarning = "Os lançamentos com essa forma de pagamento ficam sem forma.",
                )
                SettingsPage.CARDS -> NamesPage(
                    hint = "Ligue um cartão aos lançamentos para ver, no início do mês, quanto vai e quanto já foi em cada um.",
                    names = state.cards.map { it.id to it.name },
                    noun = "cartão",
                    adding = adding, onAddingDone = { adding = false },
                    onAdd = viewModel::addCard,
                    onRename = { id, name -> state.cards.firstOrNull { it.id == id }?.let { viewModel.renameCard(it, name) } },
                    onDelete = { id -> state.cards.firstOrNull { it.id == id }?.let(viewModel::deleteCard) },
                    deleteWarning = "Os lançamentos ligados a esse cartão ficam sem cartão.",
                )
                SettingsPage.SUBCATEGORIES -> SubcategoriesPage(state.subcategories) { editingSubcategory = it }
                SettingsPage.BUDGET -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    state.budget?.let { BudgetRuleCard(it, viewModel::saveBudget) }
                }
                SettingsPage.SHEETS -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    SheetsCard(state.sheets, state.sheetsStatus, viewModel::saveSheets, viewModel::syncSheetsNow)
                }
            }
        }
    }
    editingSubcategory?.let { sub ->
        SubcategoryEditorSheet(
            initial = sub,
            people = state.people,
            categories = state.categories,
            paymentMethods = state.paymentMethods,
            cards = state.cards,
            onSave = { viewModel.saveSubcategory(it); editingSubcategory = null },
            onDelete = if (sub.id == 0L) null else { s -> viewModel.deleteSubcategory(s); editingSubcategory = null },
            onDismiss = { editingSubcategory = null },
            takenNames = state.subcategories.filter { it.id != sub.id }.map { it.name.nameKey() }.toSet(),
        )
    }
}

@Composable
private fun ThemePage(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "Igual ao sistema", "Segue o modo claro ou escuro do celular"),
        Triple(ThemeMode.LIGHT, "Claro", "Fundo claro"),
        Triple(ThemeMode.DARK, "Escuro", "Fundo escuro"),
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { index, (mode, title, subtitle) ->
            Row(
                Modifier.fillMaxWidth().clip(groupShape(index, options.size)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onSelect(mode) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (current == mode) Icon(Icons.Default.Check, "Selecionado", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * A list of names (people, categories, payment methods, cards): tap to rename, the bin to delete
 * (after confirming), the "Adicionar" button to add. Names that already exist are refused, ignoring
 * case and accents.
 */
@Composable
private fun NamesPage(
    hint: String,
    names: List<Pair<Long, String>>,
    noun: String,
    adding: Boolean,
    onAddingDone: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    deleteWarning: String,
) {
    var renaming by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Long, String>?>(null) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item("hint") {
            Text(hint, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (names.isEmpty()) item("empty") { Text("Nenhum item ainda.", Modifier.padding(4.dp), style = MaterialTheme.typography.bodyMedium) }
        itemsIndexed(names, key = { _, it -> it.first }) { index, entry ->
            Row(
                Modifier.fillMaxWidth().clip(groupShape(index, names.size)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { renaming = entry }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry.second, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { deleting = entry }) {
                    Icon(Icons.Outlined.DeleteOutline, "Excluir ${entry.second}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (adding) {
        NameDialog("Nova $noun".replace("Nova cartão", "Novo cartão"), "", names, null, onSave = { onAdd(it); onAddingDone() }, onDismiss = onAddingDone)
    }
    renaming?.let { (id, name) ->
        NameDialog("Renomear", name, names, id, onSave = { onRename(id, it); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir $name?") },
            text = { Text(deleteWarning) },
            confirmButton = {
                TextButton(onClick = { onDelete(id); deleting = null }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

/** Name field that refuses blanks and names that already exist (other than the one being renamed). */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    existing: List<Pair<Long, String>>,
    editingId: Long?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val duplicate = existing.firstOrNull { it.first != editingId && it.second.nameKey() == name.nameKey() }
    val valid = name.isNotBlank() && duplicate == null && name.trim() != initial
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
                isError = duplicate != null,
                supportingText = duplicate?.let { { Text("Já existe \"${it.second}\"") } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(name.trim()) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SubcategoriesPage(subcategories: List<SubcategoryEntity>, onEdit: (SubcategoryEntity) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item("hint") {
            Text(
                "Orçamentos com meta, como Mercado e Gasolina. Cada compra que você adicionar soma na linha do mês.",
                Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (subcategories.isEmpty()) item("empty") { Text("Nenhuma ainda.", Modifier.padding(4.dp), style = MaterialTheme.typography.bodyMedium) }
        itemsIndexed(subcategories, key = { _, it -> it.id }) { index, sub ->
            Row(
                Modifier.fillMaxWidth().clip(groupShape(index, subcategories.size)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onEdit(sub) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(sub.name, style = MaterialTheme.typography.bodyLarge)
                    Text(sub.block.title() + if (sub.inTracker) " · no acompanhamento" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(sub.plannedCents), style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BudgetRuleCard(budget: BudgetSettingsEntity, onSave: (BudgetSettingsEntity) -> Unit) {
    fun pct(bp: Int) = (bp / 100).toString()
    var tithe by remember { mutableStateOf(pct(budget.titheBp)) }
    var fixed by remember { mutableStateOf(pct(budget.fixedBp)) }
    var variable by remember { mutableStateOf(pct(budget.variableBp)) }
    var investment by remember { mutableStateOf(pct(budget.investmentBp)) }
    LaunchedEffect(budget) {
        tithe = pct(budget.titheBp); fixed = pct(budget.fixedBp)
        variable = pct(budget.variableBp); investment = pct(budget.investmentBp)
    }
    val values = listOf(tithe, fixed, variable, investment).map { it.toIntOrNull() }
    val sum = values.drop(1).sumOf { it ?: 0 }
    val valid = values.all { it != null && it in 0..100 } && sum == 100

    SectionCard {
        Text(
            "Dízimo sobre a renda total. Fixo + Variável + Investimento somam 100% da renda após o dízimo.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        PercentField("Dízimo", tithe) { tithe = it }
        PercentField("Fixo", fixed) { fixed = it }
        PercentField("Variável", variable) { variable = it }
        PercentField("Investimento", investment) { investment = it }
        if (sum != 100) {
            Text("Fixo + Variável + Investimento = $sum%", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = valid,
            onClick = {
                onSave(budget.copy(titheBp = values[0]!! * 100, fixedBp = values[1]!! * 100, variableBp = values[2]!! * 100, investmentBp = values[3]!! * 100))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salvar regra") }
    }
}

/** One-time setup of the Apps Script, done on a computer in the spreadsheet. */
@Composable
private fun SheetsSetupSteps() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val steps = listOf(
        "No computador, abra a planilha Controle Financeiro 2026 e vá em Extensões > Apps Script.",
        "Apague o que estiver no arquivo Código.gs e cole o código do script. Ele está em $SCRIPT_URL " +
            "e também pode ser copiado pelo botão abaixo.",
        "Salve. No topo, escolha a função gerarChave e clique em Executar. Autorize com sua conta " +
            "(Avançado > Acessar, o aviso aparece porque o script é seu). Copie a chave do Registro de execução.",
        "Clique em Implantar > Nova implantação > App da Web. Em Executar como, deixe Eu; em Quem pode acessar, " +
            "escolha Qualquer pessoa. Implante e copie o URL do app da Web (termina em /exec).",
        "Cole o link e a chave aqui embaixo e toque em Salvar. O app baixa os meses da planilha na hora.",
    )
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        steps.forEachIndexed { index, step ->
            Row {
                Text("${index + 1}.", Modifier.width(22.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(
            onClick = {
                val code = context.assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() }
                clipboard.setText(AnnotatedString(code))
                copied = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (copied) "Código copiado" else "Copiar código do script") }
    }
}

private const val SCRIPT_ASSET = "Codigo.gs"
private const val SCRIPT_URL = "github.com/michaelplanziloti/controleFinanceiro/blob/main/sheets/Codigo.gs"

/** Link and key of the Apps Script that fills the spreadsheet (setup in the README, "Planilha Google"). */
@Composable
private fun SheetsCard(config: SheetsConfig, status: String?, onSave: (SheetsConfig) -> Unit, onSyncNow: () -> Unit) {
    var url by remember { mutableStateOf(config.url) }
    var key by remember { mutableStateOf(config.key) }
    LaunchedEffect(config) { url = config.url; key = config.key }
    val changed = url.trim() != config.url || key.trim() != config.key

    var showSteps by remember { mutableStateOf(!config.enabled) }

    SectionCard {
        Text(
            "O app e a planilha ficam iguais nos dois sentidos, um mês (aba) por vez: o que muda no app vai para a aba " +
                "do mês (ex.: Out26) e o que muda na planilha vem para o app. Se os dois mudarem, fica a versão da planilha. " +
                "No mês, puxe a tela para baixo para conferir na hora. Deixe o link vazio para desligar.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { showSteps = !showSteps }, contentPadding = PaddingValues(0.dp)) {
            Text(if (showSteps) "Esconder como instalar" else "Como instalar o script na planilha")
        }
        if (showSteps) SheetsSetupSteps()
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            url, { url = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp),
            label = { Text("Link do script (app da web)") },
            placeholder = { Text("https://script.google.com/macros/s/.../exec") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth().padding(vertical = 4.dp),
            label = { Text("Chave") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        )
        status?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSyncNow, enabled = config.enabled && !changed, modifier = Modifier.weight(1f)) {
                Text("Sincronizar agora")
            }
            Button(onClick = { onSave(SheetsConfig(url, key)) }, enabled = changed, modifier = Modifier.weight(1f)) { Text("Salvar") }
        }
    }
}

@Composable
private fun PercentField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        suffix = { Text("%") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
