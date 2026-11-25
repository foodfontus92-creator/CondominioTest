package com.accesolibre.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accesolibre.app.ui.viewmodel.ServerConfigViewModel

@Composable
fun ServerConfigScreen(
    viewModel: ServerConfigViewModel,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Servidor AccesoLibre",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Conecta por defecto con el servidor HTTPS (Wi-Fi o 4G/5G), o indica la IP local si usas red privada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.urlInput,
                onValueChange = viewModel::onUrlChange,
                label = { Text("URL del servidor") },
                placeholder = { Text("https://api-accesolibre.fodtech.cl") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = !state.testing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (state.testing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Probar conexión (/actuator/health)")
                }
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { viewModel.saveAndContinue(onSaved) },
                enabled = !state.testing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Guardar y continuar")
            }

            state.testSuccessMessage?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(4.dp))
                        Text(msg, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            state.errorMessage?.let { err ->
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(4.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}
