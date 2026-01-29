package com.example.converter

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

data class CurrencyInfo(val code: String, val name: String)

@Composable
fun getLocalizedCurrencyName(code: String): String {
    val context = LocalContext.current
    val resourceId = context.resources.getIdentifier("currency_$code", "string", context.packageName)
    return if (resourceId != 0) {
        stringResource(resourceId)
    } else {
        code
    }
}

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(modifier: Modifier = Modifier, repo: ConverterRepository) {
    val settings by repo.settingsFlow.collectAsState(initial = AppSettings.defaults())
    val liveRate by repo.liveUsdKgsRateFlow.collectAsState(initial = LiveUsdKgsRate(null, null))

    // Best effort refresh when the live rate is enabled
    LaunchedEffect(settings.useLiveKgsRate) {
        if (settings.useLiveKgsRate) {
            repo.refreshUsdKgsRateIfStale()
        }
    }

    val effectiveRates = remember(settings, liveRate) { repo.effectiveRates(settings, liveRate) }

    var amount by remember { mutableStateOf("") }
    val currencies = listOf(
        CurrencyInfo("usd", getLocalizedCurrencyName("usd")),
        CurrencyInfo("eur", getLocalizedCurrencyName("eur")),
        CurrencyInfo("kgs", getLocalizedCurrencyName("kgs")),
        CurrencyInfo("rub", getLocalizedCurrencyName("rub"))
    )
    var fromCurrency by remember { mutableStateOf(currencies[0]) }
    var toCurrency by remember { mutableStateOf(currencies[2]) }
    var result by remember { mutableStateOf<Double?>(null) }

    val isInputValid by derivedStateOf {
        val amountValue = amount.toDoubleOrNull()
        amountValue != null && amountValue > 0 && fromCurrency != toCurrency
    }

    val liveRateNote: String? = when {
        !settings.useLiveKgsRate -> null
        (fromCurrency.code == "usd" && toCurrency.code == "kgs") || (fromCurrency.code == "kgs" && toCurrency.code == "usd") -> {
            val updatedAt = liveRate.updatedAtMillis
            if (liveRate.rate != null && updatedAt != null) {
                val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                "Live USD/KGS rate • ${fmt.format(Date(updatedAt))}"
            } else {
                "Live USD/KGS rate • not loaded yet (offline fallback)"
            }
        }
        else -> null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (liveRateNote != null) {
            Text(
                text = liveRateNote,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                CurrencyDropdown(
                    label = stringResource(R.string.from),
                    selectedCurrency = fromCurrency,
                    onCurrencySelected = { fromCurrency = it },
                    currencies = currencies
                )

                Spacer(modifier = Modifier.height(16.dp))

                CurrencyDropdown(
                    label = stringResource(R.string.to),
                    selectedCurrency = toCurrency,
                    onCurrencySelected = { toCurrency = it },
                    currencies = currencies
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(targetState = result, label = "result") {
            Text(
                text = it?.let { v -> formatCurrency(v, toCurrency.code) }
                    ?: stringResource(R.string.result_placeholder),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val amountValue = amount.toDoubleOrNull() ?: 0.0
                val conversionRate = getConversionRate(fromCurrency.code, toCurrency.code, effectiveRates)
                result = amountValue * conversionRate
            },
            enabled = isInputValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(stringResource(R.string.convert))
        }
    }
}

fun getConversionRate(from: String, to: String, rates: Map<String, Double>): Double {
    val toRate = rates[to] ?: 1.0
    val fromRate = rates[from] ?: 1.0
    return toRate / fromRate
}

fun formatCurrency(value: Double, currencyCode: String): String {
    return try {
        val format = NumberFormat.getCurrencyInstance()
        format.currency = Currency.getInstance(currencyCode.uppercase(Locale.getDefault()))
        format.format(value)
    } catch (e: Exception) {
        String.format(Locale.getDefault(), "%.2f", value)
    }
}
