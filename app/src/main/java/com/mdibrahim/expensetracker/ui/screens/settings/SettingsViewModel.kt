package com.mdibrahim.expensetracker.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdibrahim.expensetracker.data.sms.SmsImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isImporting: Boolean = false,
    val importMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val smsImporter: SmsImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun importInbox() {
        if (_uiState.value.isImporting) return
        _uiState.update { it.copy(isImporting = true, importMessage = null) }
        viewModelScope.launch {
            val message = try {
                val result = smsImporter.importInbox()
                when {
                    result.bankMessages == 0 -> "No Canara Bank SMS found in the inbox."
                    else -> "Found ${result.bankMessages} Canara Bank SMS; added ${result.added} new " +
                        "transaction(s). Messages that aren't debits/credits (OTPs, alerts) are skipped."
                }
            } catch (e: SecurityException) {
                Log.e("Settings", "SMS import without permission", e)
                "SMS permission is needed to read the inbox."
            }
            _uiState.update { it.copy(isImporting = false, importMessage = message) }
        }
    }
}
