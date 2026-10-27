package com.controlefinanceiro.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.AppContainer
import com.controlefinanceiro.app.data.AuthResult
import com.controlefinanceiro.app.ui.theme.LocalHeroColors
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(container: AppContainer) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var partner by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hero = LocalHeroColors.current

    fun submit() {
        if (registering && password != confirm) {
            error = "As senhas não conferem."
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = if (registering) container.auth.register(name, email, password, partner)
            else container.auth.login(email, password)
            busy = false
            if (result is AuthResult.Error) error = result.message
        }
    }

    val fieldShape = MaterialTheme.shapes.medium
    val passwordTransform = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Brand header
        Column(
            Modifier
                .fillMaxWidth()
                .background(hero.brush, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(72.dp).background(hero.tile, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(36.dp), tint = hero.content) }
            Spacer(Modifier.height(16.dp))
            Text("Controle Financeiro", style = MaterialTheme.typography.headlineMedium, color = hero.content)
            Spacer(Modifier.height(4.dp))
            Text(
                if (registering) "Crie sua conta. Os dados ficam só neste aparelho." else "Entre para ver o orçamento do mês.",
                style = MaterialTheme.typography.bodyMedium, color = hero.muted, textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(registering) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Seu nome") },
                        singleLine = true, shape = fieldShape)
                    OutlinedTextField(
                        partner, { partner = it }, Modifier.fillMaxWidth(),
                        label = { Text("Nome do(a) parceiro(a) (opcional)") }, singleLine = true, shape = fieldShape,
                    )
                }
            }
            OutlinedTextField(
                email, { email = it }, Modifier.fillMaxWidth(), label = { Text("E-mail") }, singleLine = true, shape = fieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Senha") }, singleLine = true, shape = fieldShape,
                visualTransformation = passwordTransform,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (showPassword) "Ocultar senha" else "Mostrar senha",
                        )
                    }
                },
            )
            AnimatedVisibility(registering) {
                OutlinedTextField(
                    confirm, { confirm = it }, Modifier.fillMaxWidth(), label = { Text("Confirmar senha") }, singleLine = true,
                    shape = fieldShape, visualTransformation = passwordTransform,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            error?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp), shape = fieldShape) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (registering) "Criar conta" else "Entrar", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { registering = !registering; error = null }) {
                Text(if (registering) "Já tenho conta" else "Ainda não tem conta? Criar uma")
            }
        }
    }
}
