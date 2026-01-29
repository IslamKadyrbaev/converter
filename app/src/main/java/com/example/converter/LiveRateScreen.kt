package com.example.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveRateScreen(modifier: Modifier = Modifier, repo: ConverterRepository) {
    val liveRate by repo.liveUsdKgsRateFlow.collectAsState(initial = LiveUsdKgsRate(null, null))
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Best effort load on first open
        isLoading = true
        errorText = null
        val res = repo.refreshUsdKgsRateIfStale()
        isLoading = false
        if (res.isFailure) errorText = stringResource(R.string.live_rate_error)
    }

    val df = remember { DecimalFormat("0.####") }
    val rate = liveRate.rate
    val inverse = rate?.takeIf { it > 0 }?.let { 1.0 / it }

    val updatedAtText = liveRate.updatedAtMillis?.let {
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        fmt.format(Date(it))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(R.string.live_rate_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.live_rate_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                when {
                    isLoading && rate == null -> {
                        Text(stringResource(R.string.live_rate_loading), style = MaterialTheme.typography.titleMedium)
                    }
                    rate != null -> {
                        Text(
                            text = "1 USD = ${df.format(rate)} KGS",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        if (inverse != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "1 KGS = ${df.format(inverse)} USD",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.live_rate_error), style = MaterialTheme.typography.titleMedium)
                    }
                }

                if (updatedAtText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${stringResource(R.string.live_rate_last_updated)} $updatedAtText",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (errorText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = errorText!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { isLoading = true; errorText = null },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(stringResource(R.string.live_rate_refresh))
        }

        // Refresh trigger
        LaunchedEffect(isLoading) {
            if (!isLoading) return@LaunchedEffect
            val res = repo.refreshUsdKgsRate(force = true)
            isLoading = false
            if (res.isFailure) errorText = stringResource(R.string.live_rate_error)
        }
    }
}
