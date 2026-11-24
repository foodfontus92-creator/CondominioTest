package com.accesolibre.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accesolibre.app.data.remote.StompConnectionState
import com.accesolibre.app.domain.model.IncomingCall
import com.accesolibre.app.ui.viewmodel.IntercomViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun IntercomScreen(viewModel: IntercomViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connState by viewModel.connectionState.collectAsStateWithLifecycle()
    val incoming by viewModel.incomingCall.collectAsStateWithLifecycle()
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm:ss") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Citofonía Virtual", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = when (connState) {
                        StompConnectionState.CONNECTED -> "WebSocket STOMP conectado"
                        StompConnectionState.CONNECTING -> "Conectando citofonía..."
                        StompConnectionState.DISCONNECTED -> "Desconectado de citofonía"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connState == StompConnectionState.CONNECTED) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Row {
                if (connState == StompConnectionState.DISCONNECTED) {
                    OutlinedButton(onClick = viewModel::reconnectStomp) { Text("Reconectar") }
                }
                IconButton(onClick = viewModel::refreshCalls) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar llamadas")
                }
            }
        }

        incoming?.let { call ->
            IncomingCallCard(
                call = call,
                busy = state.processingCallId == call.callId,
                onAllow = { viewModel.allowEntry(call.callId) },
                onReject = { viewModel.rejectCall(call.callId) },
            )
        }

        state.feedback?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(msg, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(12.dp))
            }
        }

        state.errorMessage?.let { err ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
            }
        }

        Text("Llamadas recientes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.calls, key = { it.id }) { call ->
                val timeText = Instant.ofEpochMilli(call.startedAtMs).atZone(ZoneId.systemDefault()).format(formatter)
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
                            Text(call.visitorName ?: "Visitante", fontWeight = FontWeight.SemiBold)
                            call.visitorNote?.takeIf { it.isNotBlank() }?.let { note ->
                                Text(note, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = "$timeText • ${call.status}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (call.status == "RINGING" || call.status == "ANSWERED") {
                            Button(
                                onClick = { viewModel.allowEntry(call.id) },
                                enabled = state.processingCallId == null,
                            ) {
                                Text("Permitir")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingCallCard(
    call: IncomingCall,
    busy: Boolean,
    onAllow: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Llamada desde ${call.deviceName ?: "Puerta principal"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("Visitante: ${call.visitorName}", style = MaterialTheme.typography.bodyLarge)
            call.visitorNote?.takeIf { it.isNotBlank() }?.let {
                Text("Nota: $it", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAllow,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Permitir Ingreso")
                }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !busy,
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Rechazar")
                }
            }
        }
    }
}
