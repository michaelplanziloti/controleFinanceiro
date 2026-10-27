package com.controlefinanceiro.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlefinanceiro.app.data.ThemeMode
import com.controlefinanceiro.app.data.db.BudgetSettingsEntity
import com.controlefinanceiro.app.ui.common.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ajustes", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Text("Tema", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    val options = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = state.theme == mode,
                                onClick = { viewModel.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) { Text(label) }
                        }
                    }
                }
            }
            item {
                ChipListCard(
                    title = "Pessoas com mesada",
                    hint = "Remover uma pessoa apaga as mesadas dela.",
                    items = state.people.map { it.name to { viewModel.deletePerson(it) } },
                    placeholder = "Nome",
                    onAdd = viewModel::addPerson,
                )
            }
            item {
                ChipListCard(
                    title = "Categorias",
                    hint = null,
                    items = state.categories.map { it.name to { viewModel.deleteCategory(it) } },
                    placeholder = "Nova categoria",
                    onAdd = viewModel::addCategory,
                )
            }
            item {
                ChipListCard(
                    title = "Formas de pagamento",
                    hint = null,
                    items = state.paymentMethods.map { it.name to { viewModel.deletePaymentMethod(it) } },
                    placeholder = "Ex.: Pix",
                    onAdd = viewModel::addPaymentMethod,
                )
            }
            item {
                ChipListCard(
                    title = "Cartões",
                    hint = "Ligue um cartão aos lançamentos para ver, no início do mês, quanto vai e quanto já foi em cada um.",
                    items = state.cards.map { it.name to { viewModel.deleteCard(it) } },
                    placeholder = "Ex.: Nubank",
                    onAdd = viewModel::addCard,
                )
            }
            state.budget?.let { budget -> item { BudgetRuleCard(budget, viewModel::saveBudget) } }
            item {
                OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Sair")
                }
            }
        }
    }
}

/** Items wrap onto new lines, so the list never scrolls sideways. Tapping a chip removes it. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChipListCard(
    title: String,
    hint: String?,
    items: List<Pair<String, () -> Unit>>,
    placeholder: String,
    onAdd: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        hint?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (label, onRemove) ->
                InputChip(
                    selected = false,
                    onClick = onRemove,
                    label = { Text(label) },
                    trailingIcon = { Icon(Icons.Default.Close, "Remover $label", Modifier.size(16.dp)) },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(placeholder) }, singleLine = true)
            IconButton(onClick = { onAdd(text); text = "" }, enabled = text.isNotBlank()) { Icon(Icons.Default.Add, "Adicionar") }
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
        Text("Regra de orçamento", style = MaterialTheme.typography.titleMedium)
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
