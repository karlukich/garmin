package com.carlos.garmin

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface UiState {
    data object NeedLogin : UiState
    data object Loading : UiState
    data class Ready(val data: DashboardData, val updated: LocalDateTime) : UiState
    data class Failed(val message: String) : UiState
}

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TokenStore(app)
    private val settingsStore = SettingsStore(app)

    /** Ajustes de la interfaz (módulos, bloques del inicio, colores). Se guardan al cambiar. */
    var settings: AppSettings by mutableStateOf(settingsStore.load())
        private set

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        settings = change(settings)
        settingsStore.save(settings)
    }

    var state: UiState by mutableStateOf<UiState>(if (store.load() == null) UiState.NeedLogin else UiState.Loading)
        private set

    /** Último dato cargado, para seguir mostrándolo mientras se actualiza o si falla un refresco. */
    var lastData: DashboardData? by mutableStateOf<DashboardData?>(null)
        private set

    var refreshing: Boolean by mutableStateOf(false)
        private set

    init {
        if (store.load() != null) refresh()
    }

    /** Guarda los tokens pegados por el usuario. Devuelve un mensaje de error o null si fue bien. */
    fun importTokens(text: String): String? {
        val t = TokenStore.parse(text)
            ?: return "Eso no parece el contenido de garmin_tokens.json (debe incluir \"di_token\")."
        store.save(t)
        refresh()
        return null
    }

    fun logout() {
        store.clear()
        lastData = null
        state = UiState.NeedLogin
    }

    fun refresh() {
        val tokens = store.load()
        if (tokens == null) {
            state = UiState.NeedLogin
            return
        }
        if (refreshing) return
        refreshing = true
        if (lastData == null) state = UiState.Loading
        viewModelScope.launch {
            try {
                val client = GarminClient(tokens) { store.save(it) }
                val data = Repository.load(client, LocalDate.now())
                lastData = data
                state = UiState.Ready(data, LocalDateTime.now())
            } catch (e: GarminException) {
                if (e.code == 401 || e.code == 403) {
                    state = UiState.Failed(e.message ?: "Sesión caducada")
                } else {
                    state = lastData?.let { UiState.Ready(it, LocalDateTime.now()) } ?: UiState.Failed(e.message ?: "Error")
                }
            } catch (e: Exception) {
                state = UiState.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                refreshing = false
            }
        }
    }
}
