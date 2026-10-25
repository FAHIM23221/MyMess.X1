package com.smarttransparentmess.erp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarttransparentmess.erp.presentation.AuthViewModel
import com.smarttransparentmess.erp.ui.SmartMessApp
import com.smarttransparentmess.erp.ui.theme.SmartMessTheme

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer
        get() = (application as SmartMessApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer.handleAuthIntent(intent)
        setContent {
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(
                    authRepository = appContainer.authRepository,
                    profileRepository = appContainer.profileRepository,
                    isConfigured = appContainer.supabaseClient != null,
                    passwordRecoveryPending = appContainer.passwordRecoveryPending,
                    onPasswordRecoveryComplete = appContainer::markPasswordRecoveryComplete,
                ),
            )
            SmartMessTheme {
                SmartMessApp(
                    viewModel = authViewModel,
                    versionName = BuildConfig.VERSION_NAME,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appContainer.handleAuthIntent(intent)
    }
}
