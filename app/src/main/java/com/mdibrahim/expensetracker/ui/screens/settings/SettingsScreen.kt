package com.mdibrahim.expensetracker.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

private fun hasSmsPermission(context: Context) = SMS_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(hasSmsPermission(context)) }
    var deniedOnce by remember { mutableStateOf(false) }

    // Re-check when returning from system settings, where the user may have changed it.
    LifecycleResumeEffect(Unit) {
        granted = hasSmsPermission(context)
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        granted = results.values.all { it }
        deniedOnce = !granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Bank SMS capture", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Reads Canara Bank debit/credit SMS and records them automatically. " +
                        "Messages are processed on this phone only and never leave it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (granted) {
                    Text("Status: on — new bank SMS are captured as they arrive.")
                    Button(onClick = viewModel::importInbox, enabled = !state.isImporting) {
                        Text(if (state.isImporting) "Importing…" else "Import past SMS from inbox")
                    }
                    state.importMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                } else {
                    Text("Status: off — SMS permission not granted.")
                    Button(onClick = { launcher.launch(SMS_PERMISSIONS) }) {
                        Text("Allow SMS access")
                    }
                    if (deniedOnce) {
                        // After repeated denials Android stops showing the dialog; the
                        // only way back is the app's system settings page.
                        Text(
                            "If no prompt appears, enable SMS under App info → Permissions.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = { openAppSettings(context) }) {
                            Text("Open app settings")
                        }
                    }
                }
            }
        }

        BackupSection()
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
