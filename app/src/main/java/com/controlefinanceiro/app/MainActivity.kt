package com.controlefinanceiro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlefinanceiro.app.data.ThemeMode
import com.controlefinanceiro.app.ui.MainScreen
import com.controlefinanceiro.app.ui.auth.AuthScreen
import com.controlefinanceiro.app.ui.theme.FinanceTheme

private const val LOADING = -1L

class MainActivity : CoponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FinanceApp).container
        setContent {
            val theme by container.session.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val userId by container.session.userId.collectAsStateWithLifecycle(initialValue = LOADING)
            FinanceTheme(theme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(userId, container)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(userId: Long?, container: AppContainer) {
    when {
        userId == LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        userId == null -> AuthScreen(container)
        else -> MainScreen(userId, container)
    }
}
