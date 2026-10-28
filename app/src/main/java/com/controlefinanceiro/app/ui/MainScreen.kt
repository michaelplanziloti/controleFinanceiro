package com.controlefinanceiro.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.controlefinanceiro.app.AppContainer
import com.controlefinanceiro.app.ui.month.MonthScreen
import com.controlefinanceiro.app.ui.month.MonthViewModel
import com.controlefinanceiro.app.ui.settings.SettingsScreen
import com.controlefinanceiro.app.ui.settings.SettingsViewModel
import com.controlefinanceiro.app.ui.year.YearScreen
import com.controlefinanceiro.app.ui.year.YearViewModel

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    MONTH("Mês", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    YEAR("Ano", Icons.Outlined.BarChart, Icons.Filled.BarChart),
    SETTINGS("Ajustes", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun MainScreen(userId: Long, container: AppContainer) {
    // Keyed by user so a new login never sees the previous user's data.
    val monthVm: MonthViewModel = viewModel(key = "month-$userId", factory = viewModelFactory {
        initializer { MonthViewModel(userId, container.finance, container::syncSheetsAndWait) }
    })
    val yearVm: YearViewModel = viewModel(key = "year-$userId", factory = viewModelFactory {
        initializer { YearViewModel(userId, container.finance) }
    })
    val settingsVm: SettingsViewModel = viewModel(key = "settings-$userId", factory = viewModelFactory {
        initializer { SettingsViewModel(userId, container.finance, container.auth, container.session, container::syncSheets) }
    })

    var tab by rememberSaveable { mutableStateOf(Tab.MONTH) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(if (tab == item) item.selectedIcon else item.icon, null) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.MONTH -> MonthScreen(monthVm, padding)
            Tab.YEAR -> YearScreen(yearVm, padding, onOpenMonth = { monthVm.selectMonth(it); tab = Tab.MONTH })
            Tab.SETTINGS -> SettingsScreen(settingsVm, padding)
        }
    }
}
