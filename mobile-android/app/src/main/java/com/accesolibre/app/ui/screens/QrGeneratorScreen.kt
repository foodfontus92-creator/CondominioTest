package com.accesolibre.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accesolibre.app.domain.model.Pass
import com.accesolibre.app.ui.qr.QrBitmapGenerator
import com.accesolibre.app.ui.qr.WhatsAppSharer
import com.accesolibre.app.ui.viewmodel.QrGeneratorViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class DurationOption { ONE_HOUR, TWENTY_FOUR_HOURS, CUSTOM }

@Composable
fun QrGeneratorScreen(viewModel: QrGeneratorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val passes by viewModel.passes.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var durationOption by remember { mutableStateOf(DurationOption.ONE_HOUR) }
    var customDateTime by remember { mutableStateOf(LocalDateTime.now().plusHours(4)) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }

    fun openCustomPicker() {
        val base = customDateTime
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        customDateTime = LocalDateTime.of(year, month + 1, day, hour, minute)
                        durationOption = DurationOption.CUSTOM
                    },
                    base.hour,
                    base.minute,
                    true,
                ).show()
            },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth,
        ).show()
    }

    fun computeExpiration(): Instant = when (durationOption) {
        DurationOption.ONE_HOUR -> Instant.now().plus(1, ChronoUnit.HOURS)
        DurationOption.TWENTY_FOUR_HOURS -> Instant.now().plus(24, ChronoUnit.HOURS)
        DurationOption.CUSTOM -> customDateTime.atZone(ZoneId.systemDefault()).toInstant()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Pases QR Offline",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Genera invitaciones firmadas con HMAC-SHA256 (< 120 caracteres) sin requerir internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = state.visitorName,
                    onValueChange = viewModel::onVisitorNameChange,
                    label = { Text("Nombre del visitante") },
                    placeholder = { Text("ej. Juan Pérez / Delivery") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = durationOption == DurationOption.ONE_HOUR,
                        onClick = { durationOption = DurationOption.ONE_HOUR },
                        label = { Text("1 hora") },
                    )
                    FilterChip(
                        selected = durationOption == DurationOption.TWENTY_FOUR_HOURS,
                        onClick = { durationOption = DurationOption.TWENTY_FOUR_HOURS },
                        label = { Text("24 horas") },
                    )
                    FilterChip(
                        selected = durationOption == DurationOption.CUSTOM,
                        onClick = { openCustomPicker() },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Fecha") },
                    )
                }

                if (durationOption == DurationOption.CUSTOM) {
                    OutlinedButton(onClick = { openCustomPicker() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Expira: ${customDateTime.format(formatter)} (cambiar)")
                    }
                }

                Button(
                    onClick = { viewModel.generate(computeExpiration()) },
                    enabled = !state.generating,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(if (state.generating) "Generando..." else "Generar QR Offline")
                }

                state.errorMessage?.let { err ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                    }
                }
                state.infoMessage?.let { msg ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(msg, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        state.selectedPass?.let { pass ->
            item(key = "preview-${pass.passId}") {
                QrPreviewCard(pass = pass, formatter = formatter, onShare = { bmp ->
                    WhatsAppSharer.sharePassImage(context, pass, bmp)
                })
            }
        }

        if (passes.isNotEmpty()) {
            item {
                Column {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Invitaciones emitidas (${passes.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            items(passes, key = { it.passId }) { pass ->
                val expText = Instant.ofEpochMilli(pass.expiresAtMs).atZone(ZoneId.systemDefault()).format(formatter)
                Card(
                    onClick = { viewModel.selectPass(pass) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pass.visitorName, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Hasta $expText • ${
                                    when {
                                        pass.revoked -> "REVOCADO"
                                        pass.synced -> "Sincronizado"
                                        else -> "Offline pendiente"
                                    }
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!pass.revoked) {
                            IconButton(onClick = { viewModel.revoke(pass.passId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Revocar pase", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrPreviewCard(
    pass: Pass,
    formatter: DateTimeFormatter,
    onShare: (android.graphics.Bitmap) -> Unit,
) {
    val bitmap = remember(pass.qrPayload) { QrBitmapGenerator.generate(pass.qrPayload) }
    val expText = remember(pass.expiresAtMs) {
        Instant.ofEpochMilli(pass.expiresAtMs).atZone(ZoneId.systemDefault()).format(formatter)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(pass.visitorName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Válido hasta $expText", style = MaterialTheme.typography.bodyMedium)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Código QR de acceso",
                modifier = Modifier.size(240.dp),
            )
            Text(
                text = "${pass.qrPayload.length} chars • Formato compacto HMAC-SHA256",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onShare(bitmap) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Compartir por WhatsApp")
            }
        }
    }
}
