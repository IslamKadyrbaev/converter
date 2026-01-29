package com.example.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(modifier: Modifier = Modifier, repo: ConverterRepository) {
    val settings by repo.settingsFlow.collectAsState(initial = AppSettings.defaults())
    val liveRate by repo.liveUsdKgsRateFlow.collectAsState(initial = LiveUsdKgsRate(null, null))
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var isAdmin by rememberSaveable { mutableStateOf(false) }
    var loginPassword by rememberSaveable { mutableStateOf("") }
    var loginError by rememberSaveable { mutableStateOf<String?>(null) }

    // Admin editable fields
    var useLive by rememberSaveable { mutableStateOf(settings.useLiveKgsRate) }
    var eurText by rememberSaveable { mutableStateOf(settings.offlineEurPerUsd.toString()) }
    var rubText by rememberSaveable { mutableStateOf(settings.offlineRubPerUsd.toString()) }
    var kgsText by rememberSaveable { mutableStateOf(settings.offlineKgsPerUsd.toString()) }

    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    var statusText by rememberSaveable { mutableStateOf<String?>(null) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    // Keep the toggle synced with store updates
    LaunchedEffect(settings.useLiveKgsRate) { useLive = settings.useLiveKgsRate }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.admin_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (!isAdmin) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it; loginError = null },
                        label = { Text(stringResource(R.string.admin_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (loginError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = loginError!!, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (loginPassword == settings.adminPassword) {
                                isAdmin = true
                                loginPassword = ""
                            } else {
                                loginError = stringResource(R.string.admin_wrong_password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(stringResource(R.string.admin_login))
                    }
                }
            }
            return
        }

        // Panel
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.admin_use_live_rate))
                    Switch(checked = useLive, onCheckedChange = { useLive = it })
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.admin_offline_rates), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = eurText,
                    onValueChange = { eurText = it; statusText = null; errorText = null },
                    label = { Text(stringResource(R.string.admin_rate_eur)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rubText,
                    onValueChange = { rubText = it; statusText = null; errorText = null },
                    label = { Text(stringResource(R.string.admin_rate_rub)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = kgsText,
                    onValueChange = { kgsText = it; statusText = null; errorText = null },
                    label = { Text(stringResource(R.string.admin_rate_kgs)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Cached live USD→KGS: ${liveRate.rate?.toString() ?: "—"}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        statusText = null
                        errorText = null
                        scope.launch {
                            val res = repo.refreshUsdKgsRate(force = true)
                            if (res.isFailure) {
                                errorText = stringResource(R.string.live_rate_error)
                            } else {
                                statusText = stringResource(R.string.admin_saved)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.live_rate_refresh))
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val eur = eurText.toDoubleOrNull()
                        val rub = rubText.toDoubleOrNull()
                        val kgs = kgsText.toDoubleOrNull()

                        if (eur == null || rub == null || kgs == null || eur <= 0 || rub <= 0 || kgs <= 0) {
                            errorText = "Please enter valid positive numbers"
                            return@Button
                        }

                        statusText = null
                        errorText = null
                        scope.launch {
                            repo.setUseLiveKgsRate(useLive)
                            repo.saveOfflineRates(eur, rub, kgs)
                            statusText = stringResource(R.string.admin_saved)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.admin_save))
                }

                if (statusText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = statusText!!)
                }
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorText!!, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.admin_change_password), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; statusText = null; errorText = null },
                    label = { Text(stringResource(R.string.admin_new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; statusText = null; errorText = null },
                    label = { Text(stringResource(R.string.admin_confirm_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (newPassword.isBlank()) {
                            errorText = "Password can't be empty"
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            errorText = stringResource(R.string.admin_password_mismatch)
                            return@Button
                        }

                        statusText = null
                        errorText = null
                        scope.launch {
                            repo.setAdminPassword(newPassword)
                            newPassword = ""
                            confirmPassword = ""
                            statusText = stringResource(R.string.admin_saved)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.admin_save))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                isAdmin = false
                statusText = null
                errorText = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(stringResource(R.string.admin_logout))
        }
    }
}
