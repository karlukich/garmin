@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.carlos.garmin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlos.garmin.Blocks
import com.carlos.garmin.DashboardViewModel
import com.carlos.garmin.HomeBlock

/** Pantalla "Personalizar inicio": orden, visibilidad y cifras de cada bloque. */
@Composable
fun CustomizeScreen(vm: DashboardViewModel) {
    val s = vm.settings

    fun edit(id: String, change: (HomeBlock) -> HomeBlock) {
        vm.updateSettings { st -> st.copy(blocks = st.blocks.map { if (it.id == id) change(it) else it }) }
    }

    /** Intercambia el bloque con el activo anterior (dir = -1) o siguiente (dir = 1). */
    fun move(id: String, dir: Int) {
        vm.updateSettings { st ->
            val list = st.blocks.toMutableList()
            val i = list.indexOfFirst { it.id == id }
            var j = i + dir
            while (j in list.indices && !list[j].enabled) j += dir
            if (i >= 0 && j in list.indices) {
                val tmp = list[i]
                list[i] = list[j]
                list[j] = tmp
            }
            st.copy(blocks = list)
        }
    }

    val active = s.blocks.filter { it.enabled }
    val inactive = s.blocks.filter { !it.enabled }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Ordena los bloques con las flechas, ocúltalos con el interruptor y elige qué cifras ver en cada uno.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        active.forEachIndexed { idx, b ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            IconButton(onClick = { move(b.id, -1) }, enabled = idx > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir ${Blocks.TITLE[b.id]}")
                            }
                        }
                        Column {
                            IconButton(onClick = { move(b.id, 1) }, enabled = idx < active.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar ${Blocks.TITLE[b.id]}")
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(Blocks.TITLE.getValue(b.id), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(Blocks.NOTE[b.id].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = true, onCheckedChange = { edit(b.id) { it.copy(enabled = false) } })
                    }
                    BlockOptions(vm, b, ::edit)
                }
            }
        }

        if (inactive.isNotEmpty()) {
            Text(
                "Añadir bloques", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    inactive.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Blocks.TITLE.getValue(b.id), style = MaterialTheme.typography.bodyLarge)
                                Text(Blocks.NOTE[b.id].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { edit(b.id) { it.copy(enabled = true) } }) {
                                Icon(Icons.Default.Add, contentDescription = "Añadir ${Blocks.TITLE[b.id]}")
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { vm.updateSettings { it.copy(blocks = com.carlos.garmin.Defaults.blocks(), reco = com.carlos.garmin.RecoOptions()) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restablecer el inicio") }
    }
}

@Composable
private fun BlockOptions(vm: DashboardViewModel, b: HomeBlock, edit: (String, (HomeBlock) -> HomeBlock) -> Unit) {
    val reco = vm.settings.reco
    when (b.id) {
        Blocks.RECO -> {
            Label("Qué incluye")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Veredicto", reco.verdict) { vm.updateSettings { it.copy(reco = reco.copy(verdict = !reco.verdict)) } }
                ToggleChip("Sesión sugerida", reco.session) { vm.updateSettings { it.copy(reco = reco.copy(session = !reco.session)) } }
                ToggleChip("Motivos", reco.reasons) { vm.updateSettings { it.copy(reco = reco.copy(reasons = !reco.reasons)) } }
                ToggleChip("Y mañana", reco.tomorrow) { vm.updateSettings { it.copy(reco = reco.copy(tomorrow = !reco.tomorrow)) } }
            }
        }
        Blocks.GOAL -> {
            Label("Objetivo de km al año")
            val goal = vm.settings.goalKm
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.updateSettings { it.copy(goalKm = (it.goalKm - 500).coerceAtLeast(0)) } }) { Text("−500") }
                Text(if (goal == 0) "Sin fijar" else "${Fmt.n(goal.toDouble())} km", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { vm.updateSettings { it.copy(goalKm = (if (it.goalKm == 0) 5000 else it.goalKm) + 500) } }) { Text("+500") }
            }
        }
        Blocks.TRENDS -> {}
        Blocks.LAST -> {}
        else -> {
            if (b.id == Blocks.RANGE) {
                Label("Periodo")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { d ->
                        ToggleChip("$d días", b.days == d) { edit(b.id) { it.copy(days = d) } }
                    }
                }
            }
            if (b.id == Blocks.YESTERDAY) {
                Label("Varias actividades")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip("Una tarjeta por actividad", !b.sumAll) { edit(b.id) { it.copy(sumAll = false) } }
                    ToggleChip("Sumar todas", b.sumAll) { edit(b.id) { it.copy(sumAll = true) } }
                }
            }
            val defs = Blocks.METRICS[b.id].orEmpty()
            if (defs.isNotEmpty()) {
                Label("Cifras visibles")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    defs.forEach { m ->
                        ToggleChip(m.label, m.id in b.metrics) {
                            edit(b.id) { cur -> cur.copy(metrics = if (m.id in cur.metrics) cur.metrics - m.id else cur.metrics + m.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
