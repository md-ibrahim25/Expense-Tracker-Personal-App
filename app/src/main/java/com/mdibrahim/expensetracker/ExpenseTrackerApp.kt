package com.mdibrahim.expensetracker

import android.app.Application
import com.mdibrahim.expensetracker.data.seed.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ExpenseTrackerApp : Application() {

    @Inject
    lateinit var seeder: DatabaseSeeder

    /**
     * Application-scoped IO work. SupervisorJob so one failed child cannot cancel
     * unrelated startup work.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            appScope.launch { seeder.seedIfEmpty() }
        }
    }
}
