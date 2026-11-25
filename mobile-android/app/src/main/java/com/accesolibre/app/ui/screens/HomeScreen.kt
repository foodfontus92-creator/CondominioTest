package com.accesolibre.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accesolibre.app.domain.model.DeviceKind
import com.accesolibre.app.domain.model.communityLabelsFor
import com.accesolibre.app.domain.model.formatUnitDisplay
import com.accesolibre.app.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    residentName: String,
    viewModel: HomeViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingKind by remember { mutableStateOf<DeviceKind?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val kind = pendingKind
        pendingKind = null
        if (granted && kind != null) {
            viewModel.openByKind(kind)
        }
    }

    fun requestOpen(kind: DeviceKind) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.openByKind(kind)
        } else {
            pendingKind = kind
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    val hasGate = state.devices.any { it.matches(DeviceKind.GATE) }
    val hasPedestrian = state.devices.any { it.matches(DeviceKind.PEDESTRIAN) }
    val labels = communityLabelsFor(state.communityType)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Hola, $residentName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatUnitDisplay(state.communityType, state.blockName, state.unitNumber)
                        ?.let { "$it • Apertura con GPS" }
                        ?: "Apertura remota con GPS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::refresh, enabled = !state.loadingDevices) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar dispositivos")
            }
        }

        // Large Primary Button 1: Abrir Portón (Vehicle Barrier)
        QuickAccessButton(
            title = "Abrir Portón",
            subtitle = if (hasGate) "Portón vehicular • Valida GPS" else "Sin portón vehicular registrado",
            loading = state.openingKind == DeviceKind.GATE,
            enabled = hasGate && state.openingKind == null,
            containerColor = MaterialTheme.colorScheme.primary,
            onClick = { requestOpen(DeviceKind.GATE) },
        )

        // Large Primary Button 2: Abrir Puerta Peatonal (shown only if condo has pedestrian/lobby door)
        if (hasPedestrian) {
            QuickAccessButton(
                title = "Abrir Puerta Peatonal",
                subtitle = "Acceso peatonal • Valida GPS",
                loading = state.openingKind == DeviceKind.PEDESTRIAN,
                enabled = state.openingKind == null,
                containerColor = MaterialTheme.colorScheme.secondary,
                onClick = { requestOpen(DeviceKind.PEDESTRIAN) },
            )
        }

        state.feedbackSuccess?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Text(msg, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        state.feedbackError?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        if (state.devices.isNotEmpty()) {
            Text(
                text = "Dispositivos ${labels.ofCommunity} (${state.devices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            state.devices.forEach { device ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${device.type} • ${device.status}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessButton(
    title: String,
    subtitle: String,
    loading: Boolean,
    enabled: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.width(16.dp))
            Text("Verificando GPS y abriendo...", style = MaterialTheme.typography.titleMedium)
        } else {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
