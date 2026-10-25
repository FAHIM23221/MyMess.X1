package com.smarttransparentmess.erp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.model.MemberProfile
import com.smarttransparentmess.erp.presentation.AppUiState
import com.smarttransparentmess.erp.presentation.AuthViewModel
import com.smarttransparentmess.erp.presentation.FinanceUiState
import com.smarttransparentmess.erp.presentation.FinanceViewModel
import com.smarttransparentmess.erp.ui.auth.AccessUnavailableScreen
import com.smarttransparentmess.erp.ui.auth.AdminReauthScreen
import com.smarttransparentmess.erp.ui.auth.LoginScreen
import com.smarttransparentmess.erp.ui.auth.PasswordResetScreen
import com.smarttransparentmess.erp.ui.auth.PasswordUpdateScreen
import com.smarttransparentmess.erp.ui.dashboard.DashboardScreen
import com.smarttransparentmess.erp.ui.navigation.LocalRoute
import com.smarttransparentmess.erp.ui.profile.AppInformationScreen
import com.smarttransparentmess.erp.ui.profile.ProfileScreen

@Composable
fun SmartMessApp(
    viewModel: AuthViewModel,
    financeViewModel: FinanceViewModel,
    versionName: String,
) {
    val uiState by viewModel.uiState.collectAsState()
    val financeUiState by financeViewModel.uiState.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val recoveryPending by viewModel.recoveryPending.collectAsState()

    var route by rememberSaveable { mutableStateOf(LocalRoute.HOME) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var adminLoginMode by rememberSaveable { mutableStateOf(false) }
    var adminCredentialsSubmitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState, notice, adminCredentialsSubmitted) {
        if (adminCredentialsSubmitted) {
            when {
                uiState is AppUiState.Authorized -> {
                    adminLoginMode = false
                    adminCredentialsSubmitted = false
                    selectedTab = 0
                    route = LocalRoute.HOME
                }
                uiState is AppUiState.SignedOut && notice != null -> {
                    adminCredentialsSubmitted = false
                }
            }
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AppUiState.Authorized -> financeViewModel.loadForUser(state.profile.userId)
            else -> financeViewModel.clear()
        }
    }

    when (val state = uiState) {
        AppUiState.CheckingSession -> LoadingContent(
            message = stringResource(R.string.loading),
            modifier = Modifier.fillMaxSize().padding(24.dp),
        )
        AppUiState.SetupRequired -> ConfigurationScreen()
        AppUiState.SignedOut -> {
            when (route) {
                LocalRoute.PASSWORD_RESET -> PasswordResetScreen(
                    isBusy = isBusy,
                    notice = notice,
                    onSubmit = viewModel::requestPasswordReset,
                    onBack = {
                        viewModel.clearNotice()
                        route = LocalRoute.HOME
                    },
                    onClearNotice = viewModel::clearNotice,
                )
                else -> LoginScreen(
                    adminOnly = adminLoginMode,
                    isBusy = isBusy,
                    notice = notice,
                    onSignIn = { email, password, adminOnly ->
                        if (adminOnly) adminCredentialsSubmitted = true
                        viewModel.signIn(email, password, adminOnly)
                    },
                    onForgotPassword = {
                        viewModel.clearNotice()
                        route = LocalRoute.PASSWORD_RESET
                    },
                    onClearNotice = viewModel::clearNotice,
                )
            }
        }
        is AppUiState.Authorized -> {
            if (recoveryPending) {
                PasswordUpdateScreen(
                    isBusy = isBusy,
                    notice = notice,
                    onSubmit = viewModel::updatePassword,
                )
            } else if (adminLoginMode) {
                AdminReauthScreen(
                    profile = state.profile,
                    isBusy = isBusy,
                    onContinueToAdminLogin = {
                        viewModel.clearNotice()
                        viewModel.signOut()
                    },
                    onCancel = {
                        adminLoginMode = false
                        route = LocalRoute.HOME
                    },
                )
            } else if (route == LocalRoute.APP_INFORMATION) {
                AppInformationScreen(
                    versionName = versionName,
                    onBack = { route = LocalRoute.HOME },
                    onAdminEntry = {
                        adminLoginMode = true
                        adminCredentialsSubmitted = false
                    },
                )
            } else {
                AuthorizedShell(
                    profile = state.profile,
                    financeState = financeUiState,
                    selectedTab = selectedTab,
                    isBusy = isBusy,
                    notice = notice,
                    onTabSelected = { selectedTab = it },
                    onRetryFinance = financeViewModel::refresh,
                    onSaveProfile = viewModel::saveProfile,
                    onOpenAppInformation = { route = LocalRoute.APP_INFORMATION },
                    onSignOut = viewModel::signOut,
                    onClearNotice = viewModel::clearNotice,
                )
            }
        }
        is AppUiState.ProfileUnavailable -> AccessUnavailableScreen(
            reason = state.reason,
            isBusy = isBusy,
            onRetry = viewModel::retryProfileLoad,
            onSignOut = viewModel::signOut,
        )
    }
}

@Composable
private fun ConfigurationScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark()
        Spacer(Modifier.height(30.dp))
        Text(stringResource(R.string.config_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.config_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        InfoCard(
            title = "Supabase",
            body = stringResource(R.string.config_variables),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.config_security_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun AuthorizedShell(
    profile: MemberProfile,
    financeState: FinanceUiState,
    selectedTab: Int,
    isBusy: Boolean,
    notice: com.smarttransparentmess.erp.presentation.AuthNotice?,
    onTabSelected: (Int) -> Unit,
    onRetryFinance: () -> Unit,
    onSaveProfile: (String, String) -> Unit,
    onOpenAppInformation: () -> Unit,
    onSignOut: () -> Unit,
    onClearNotice: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home)) },
                    label = { Text(stringResource(R.string.home)) },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.profile)) },
                    label = { Text(stringResource(R.string.profile)) },
                )
            }
        },
    ) { contentPadding: PaddingValues ->
        if (selectedTab == 0) {
            DashboardScreen(
                profile = profile,
                financeState = financeState,
                modifier = Modifier.padding(contentPadding),
                notice = notice,
                onOpenProfile = { onTabSelected(1) },
                onRetryFinance = onRetryFinance,
                onSignOut = onSignOut,
                onClearNotice = onClearNotice,
            )
        } else {
            ProfileScreen(
                profile = profile,
                modifier = Modifier.padding(contentPadding),
                isBusy = isBusy,
                notice = notice,
                onSave = onSaveProfile,
                onOpenAppInformation = onOpenAppInformation,
                onSignOut = onSignOut,
                onClearNotice = onClearNotice,
            )
        }
    }
}
