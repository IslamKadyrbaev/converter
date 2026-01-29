package com.example.converter

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "converter_settings")

data class AppSettings(
    val useLiveKgsRate: Boolean,
    val offlineEurPerUsd: Double,
    val offlineRubPerUsd: Double,
    val offlineKgsPerUsd: Double,
    val adminPassword: String
) {
    companion object {
        fun defaults() = AppSettings(
            useLiveKgsRate = true,
            offlineEurPerUsd = 0.92,
            offlineRubPerUsd = 91.5,
            offlineKgsPerUsd = 87.5,
            adminPassword = "admin"
        )
    }
}

data class LiveUsdKgsRate(
    val rate: Double?,
    val updatedAtMillis: Long?
)

class ConverterRepository(private val context: Context) {

    private object Keys {
        val useLiveKgsRate = booleanPreferencesKey("use_live_kgs_rate")
        val offlineEurPerUsd = doublePreferencesKey("offline_eur_per_usd")
        val offlineRubPerUsd = doublePreferencesKey("offline_rub_per_usd")
        val offlineKgsPerUsd = doublePreferencesKey("offline_kgs_per_usd")
        val adminPassword = stringPreferencesKey("admin_password")

        val liveUsdKgsRate = doublePreferencesKey("live_usd_kgs_rate")
        val liveUsdKgsUpdatedAt = longPreferencesKey("live_usd_kgs_updated_at")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val d = AppSettings.defaults()
        AppSettings(
            useLiveKgsRate = prefs[Keys.useLiveKgsRate] ?: d.useLiveKgsRate,
            offlineEurPerUsd = prefs[Keys.offlineEurPerUsd] ?: d.offlineEurPerUsd,
            offlineRubPerUsd = prefs[Keys.offlineRubPerUsd] ?: d.offlineRubPerUsd,
            offlineKgsPerUsd = prefs[Keys.offlineKgsPerUsd] ?: d.offlineKgsPerUsd,
            adminPassword = prefs[Keys.adminPassword] ?: d.adminPassword
        )
    }

    val liveUsdKgsRateFlow: Flow<LiveUsdKgsRate> = context.dataStore.data.map { prefs ->
        LiveUsdKgsRate(
            rate = prefs[Keys.liveUsdKgsRate],
            updatedAtMillis = prefs[Keys.liveUsdKgsUpdatedAt]
        )
    }

    fun effectiveRates(settings: AppSettings, liveRate: LiveUsdKgsRate): Map<String, Double> {
        val kgsPerUsd = if (settings.useLiveKgsRate && liveRate.rate != null) {
            liveRate.rate
        } else {
            settings.offlineKgsPerUsd
        }

        return mapOf(
            "usd" to 1.0,
            "eur" to settings.offlineEurPerUsd,
            "rub" to settings.offlineRubPerUsd,
            "kgs" to kgsPerUsd
        )
    }

    suspend fun setUseLiveKgsRate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.useLiveKgsRate] = enabled }
    }

    suspend fun saveOfflineRates(eurPerUsd: Double, rubPerUsd: Double, kgsPerUsd: Double) {
        context.dataStore.edit {
            it[Keys.offlineEurPerUsd] = eurPerUsd
            it[Keys.offlineRubPerUsd] = rubPerUsd
            it[Keys.offlineKgsPerUsd] = kgsPerUsd
        }
    }

    suspend fun setAdminPassword(newPassword: String) {
        context.dataStore.edit { it[Keys.adminPassword] = newPassword }
    }

    suspend fun refreshUsdKgsRateIfStale(maxAgeMillis: Long = TimeUnit.HOURS.toMillis(6)): Result<Double> {
        val current = liveUsdKgsRateFlow.first()
        val now = System.currentTimeMillis()
        val updatedAt = current.updatedAtMillis
        val isStale = current.rate == null || updatedAt == null || (now - updatedAt) > maxAgeMillis
        return if (isStale) refreshUsdKgsRate(force = true) else Result.success(current.rate!!)
    }

    suspend fun refreshUsdKgsRate(force: Boolean = true): Result<Double> {
        return runCatching {
            val existing = liveUsdKgsRateFlow.first()
            if (!force && existing.rate != null) return@runCatching existing.rate

            val rate = fetchUsdToKgsFromInternet()
            val now = System.currentTimeMillis()
            context.dataStore.edit {
                it[Keys.liveUsdKgsRate] = rate
                it[Keys.liveUsdKgsUpdatedAt] = now
            }
            rate
        }
    }

    private suspend fun fetchUsdToKgsFromInternet(): Double = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            // No API key required
            "https://open.er-api.com/v6/latest/USD",
            "https://api.exchangerate-api.com/v4/latest/USD"
        )

        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "ConverterApp/1.0")
                }

                try {
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val rates = json.optJSONObject("rates")
                        ?: throw IllegalStateException("No 'rates' in response")
                    val kgs = rates.optDouble("KGS", Double.NaN)
                    if (kgs.isNaN() || kgs <= 0) throw IllegalStateException("KGS rate missing")
                    return@withContext kgs
                } finally {
                    conn.disconnect()
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw (lastError ?: IllegalStateException("Failed to load live rate"))
    }
}
