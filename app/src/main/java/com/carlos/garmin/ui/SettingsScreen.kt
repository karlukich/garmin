@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.carlos.garmin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlos.garmin.AppSettings
import com.carlos.garmin.DashboardViewModel
import com.carlos.garmin.Modules
import com.carlos.garmin.Swatch
import com.carlos.garmin.Swatches
import com.carlos.garmin.ThemeMode
import com.carlos.garmin.TrainingParts
import com.carlos.garmin.UiState

/** Fila con título, nota opcional e interruptor. */
@Composable
internal fun SwitchRow(title: String, note: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Chip seleccionable con marca de verificación cuando está activo. */
@Composable
internal fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SwatchPicker(label: String, options: List<Swatch>, selected: Long, onPick: (Long) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { sw ->
            val on = sw.argb == selected
            Box(
                Modifier.size(48.dp)
                    .semantics { contentDescription = sw.name + if (on) ", seleccionado" else "" }
                    .clickable(role = Role.RadioButton) { onPick(sw.argb) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(sw.argb.toInt())),
                )
                if (on) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: DashboardViewModel, state: UiState, needLogin: Boolean, onDone: () -> Unit) {
    val s = vm.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (needLogin) AccountCard(vm, state, onDone)

        SettingsCard {
            SectionTitle("Módulos visibles")
            Text(
                "Elige qué pestañas aparecen en la barra inferior. Debe quedar al menos una.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val visible = s.visibleModules()
            Modules.ALL.forEach { id ->
                val on = id in visible
                SwitchRow(
                    title = Modules.TITLE.getValue(id), note = Modules.NOTE[id], checked = on,
                    enabled = !(on && visible.size == 1),
                ) { v -> vm.updateSettings { it.copy(modules = if (v) it.visibleModules().toSet() + id else it.visibleModules().toSet() - id) } }
            }
        }

        SettingsCard {
            SectionTitle("Bloques dentro de Entreno")
            TrainingParts.ALL.forEach { id ->
                SwitchRow(TrainingParts.TITLE.getValue(id), checked = id in s.training) { v ->
                    vm.updateSettings { it.copy(training = if (v) it.training + id else it.training - id) }
                }
            }
        }

        SettingsCard {
            SectionTitle("Apariencia")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Claro", s.theme == ThemeMode.LIGHT) { vm.updateSettings { it.copy(theme = ThemeMode.LIGHT) } }
                ToggleChip("Oscuro", s.theme == ThemeMode.DARK) { vm.updateSettings { it.copy(theme = ThemeMode.DARK) } }
                ToggleChip("Según el sistema", s.theme == ThemeMode.SYSTEM) { vm.updateSettings { it.copy(theme = ThemeMode.SYSTEM) } }
            }
            SwatchPicker("Color principal", Swatches.PRIMARY, s.primary) { c -> vm.updateSettings { it.copy(primary = c) } }
            SwatchPicker("Color de resalte", Swatches.ACCENT, s.accent) { c -> vm.updateSettings { it.copy(accent = c) } }
            OutlinedButton(
                onClick = {
                    vm.updateSettings { it.copy(primary = Swatches.PRIMARY.first().argb, accent = Swatches.ACCENT.first().argb, theme = ThemeMode.SYSTEM) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restablecer colores") }
        }

        if (!needLogin) AccountCard(vm, state, onDone)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Conexión con la cuenta de Garmin: pegar garmin_tokens.json o cerrar sesión. */
@Composable
private fun AccountCard(vm: DashboardViewModel, state: UiState, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val connected = state is UiState.Ready || state is UiState.Loading

    SettingsCard {
        SectionTitle("Cuenta de Garmin")
        Text(
            if (connected) "Conectado a Garmin Connect. Las credenciales están cifradas en este móvil."
            else "Sin conexión. Pega aquí tus credenciales para empezar.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state is UiState.Failed) {
            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Conectar o cambiar de cuenta",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "La app lee tus datos directamente de Garmin Connect y no guarda tu contraseña. En tu PC abre " +
                "garmin_tokens.json (carpeta .garminconnect de tu usuario), cópialo entero y pégalo aquí.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = null },
            label = { Text("Contenido de garmin_tokens.json") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
        )
        OutlinedButton(
            onClick = { clipboard.getText()?.text?.let { text = it; error = null } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Pegar del portapapeles") }
        Button(
            onClick = {
                val e = vm.importTokens(text)
                if (e != null) error = e else { text = ""; onDone() }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Conectar") }
        OutlinedButton(
            onClick = { vm.logout() },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) { Text("Cerrar sesión y borrar credenciales", color = MaterialTheme.colorScheme.error) }
        Text(
            "Las credenciales equivalen a tu contraseña: bórralas del chat o del correo con el que las pases al móvil.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
