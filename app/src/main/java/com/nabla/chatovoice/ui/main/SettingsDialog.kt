package com.nabla.chatovoice.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nabla.chatovoice.R

@Composable
fun SettingsDialog(
    initialUrl: String,
    initialToken: String,
    initialAzureKey: String,
    initialAzureRegion: String,
    onDismiss: () -> Unit,
    onSave: (url: String, token: String, azureKey: String, azureRegion: String) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var azureKey by remember { mutableStateOf(initialAzureKey) }
    var azureRegion by remember { mutableStateOf(initialAzureRegion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // --- Gateway section ---
                Text(
                    text = "Gateway",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.label_gateway_url)) },
                    placeholder = { Text(stringResource(R.string.hint_gateway_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.label_gateway_token)) },
                    placeholder = { Text(stringResource(R.string.hint_gateway_token)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- Azure Speech section ---
                Text(
                    text = "Azure Speech",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = azureKey,
                    onValueChange = { azureKey = it },
                    label = { Text(stringResource(R.string.label_azure_speech_key)) },
                    placeholder = { Text(stringResource(R.string.hint_azure_speech_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = azureRegion,
                    onValueChange = { azureRegion = it },
                    label = { Text(stringResource(R.string.label_azure_speech_region)) },
                    placeholder = { Text(stringResource(R.string.hint_azure_speech_region)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Version ${com.nabla.chatovoice.BuildConfig.VERSION_NAME} (${com.nabla.chatovoice.BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url, token, azureKey, azureRegion) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
