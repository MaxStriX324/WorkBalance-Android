package ru.maxstrix.workbalance

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.maxstrix.workbalance.ui.WorkBalanceApp
import ru.maxstrix.workbalance.ui.WorkViewModel
import ru.maxstrix.workbalance.ui.theme.WorkBalanceTheme
import ru.maxstrix.workbalance.notification.ForgottenMarkReminderScheduler

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ForgottenMarkReminderScheduler.refreshAsync(applicationContext)
        setContent {
            WorkBalanceTheme {
                val model: WorkViewModel = viewModel(
                    factory = WorkViewModel.factory(
                        (application as WorkBalanceApplication).repository,
                        applicationContext
                    )
                )
                WorkBalanceApp(model)
            }
        }
    }
}
