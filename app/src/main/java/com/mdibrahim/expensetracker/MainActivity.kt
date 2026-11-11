package com.mdibrahim.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mdibrahim.expensetracker.ui.navigation.AppScaffold
import com.mdibrahim.expensetracker.ui.theme.ExpenseTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draws behind the system bars, which is required behaviour on Android 15+.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ExpenseTrackerTheme {
                AppScaffold()
            }
        }
    }
}
