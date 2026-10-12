package com.carlos.garmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.carlos.garmin.ui.AppTheme
import com.carlos.garmin.ui.BodyScreen
import com.carlos.garmin.ui.CustomizeScreen
import com.carlos.garmin.ui.DailyScreen
import com.carlos.garmin.ui.HomeScreen
import com.carlos.garmin.ui.SettingsScreen
import com.carlos.garmin.ui.SleepScreen
import com.carlos.garmin.ui.TrainingScreen

class MainActivity : ComponentActivity() {
    private val vm: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(vm.settings) {
                App(vm)
            }
        }
    }
}

private enum class Screen { MAIN, SETTINGS, CUSTOMIZE }

private val ICONS: Map<String, ImageVector> = mapOf(
    Modules.HOME to Icons.Default.Home,
    Modules.SLEEP to Icons.Default.Favorite,
    Modules.TRAINING to Icons.Default.PlayArrow,
    Modules.DAILY to Icons.Default.List,
    Modules.BODY to Icons.Default.Person,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: DashboardViewModel) {
    val settings = vm.settings
    val tabs = settings.visibleModules()
    var selected by rememberSaveable { mutableStateOf(Modules.HOME) }
    val tab = if (selected in tabs) selected else tabs.first()
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }

    val state = vm.state
    val data = vm.lastData
    // Sin credenciales (o con la sesión caducada y sin datos) se abre Ajustes en la sección de la cuenta.
    val needLogin = state is UiState.NeedLogin || (state is UiState.Failed && data == null)
    val current = if (needLogin) Screen.SETTINGS else screen

    BackHandler(enabled = current != Screen.MAIN && !needLogin) { screen = Screen.MAIN }

    val title = when (current) {
        Screen.SETTINGS -> "Ajustes"
        Screen.CUSTOMIZE -> "Personalizar inicio"
        Screen.MAIN -> Modules.TITLE.getValue(tab)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (current != Screen.MAIN && !needLogin) {
                        IconButton(onClick = { screen = Screen.MAIN }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    if (current == Screen.MAIN) {
                        if (tab == Modules.HOME) {
                            IconButton(onClick = { screen = Screen.CUSTOMIZE }) {
                                Icon(Icons.Default.Edit, contentDescription = "Personalizar inicio")
                            }
                        }
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
                        IconButton(onClick = { screen = Screen.SETTINGS }) { Icon(Icons.Default.Settings, contentDescription = "Ajustes") }
                    }
                },
            )
        },
        bottomBar = {
            if (current == Screen.MAIN && data != null && tabs.size > 1) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { id ->
                        val name = Modules.TITLE.getValue(id)
                        NavigationBarItem(
                            selected = tab == id,
                            onClick = { selected = id },
                            icon = { Icon(ICONS.getValue(id), contentDescription = name) },
                            label = { Text(name) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (current) {
                Screen.SETTINGS -> SettingsScreen(vm, state, needLogin, onDone = { screen = Screen.MAIN })
                Screen.CUSTOMIZE -> CustomizeScreen(vm)
                Screen.MAIN -> when {
                    data == null -> Loading()
                    else -> when (tab) {
                        Modules.HOME -> HomeScreen(data, settings)
                        Modules.SLEEP -> SleepScreen(data)
                        Modules.TRAINING -> TrainingScreen(data, settings.training)
                        Modules.DAILY -> DailyScreen(data)
                        else -> BodyScreen(data)
                    }
                }
            }
            if (vm.refreshing && data != null) {
                CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Descargando tus datos de Garmin…")
        }
    }
}
