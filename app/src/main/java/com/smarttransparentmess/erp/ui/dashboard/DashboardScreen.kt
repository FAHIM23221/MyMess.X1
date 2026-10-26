package com.smarttransparentmess.erp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.model.AppRole
import com.smarttransparentmess.erp.domain.model.MemberProfile
import com.smarttransparentmess.erp.presentation.AuthNotice
import com.smarttransparentmess.erp.presentation.FinanceUiState
import com.smarttransparentmess.erp.ui.BrandMark
import com.smarttransparentmess.erp.ui.InfoCard
import com.smarttransparentmess.erp.ui.StatusPill
import com.smarttransparentmess.erp.ui.labelRes
import com.smarttransparentmess.erp.ui.textOrNull

@Composable
fun DashboardScreen(
    profile: MemberProfile,
    financeState: FinanceUiState,
    modifier: Modifier = Modifier,
    notice: AuthNotice? = null,
    onOpenProfile: () -> Unit,
    onRetryFinance: () -> Unit,
    onSignOut: () -> Unit,
    onClearNotice: () -> Unit,
) {
    val titleRes = when (profile.role) {
        AppRole.MEMBER -> R.string.member_dashboard_title
        AppRole.ADMIN -> R.string.admin_dashboard_title
        AppRole.MAIN_ADMIN -> R.string.main_admin_dashboard_title
    }
    val noticeIsInformational =
        notice == AuthNotice.PROFILE_SAVED ||
            notice == AuthNotice.PASSWORD_UPDATED ||
            notice == AuthNotice.SIGNED_OUT_LOCAL_ONLY

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BrandMark(compact = true)
            TextButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.welcome_name, profile.fullName),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            StatusPill(text = stringResource(profile.role.labelRes()))
        }

        notice?.let { authNotice ->
            val message = authNotice.textOrNull()
            if (!message.isNullOrBlank()) {
                TextButton(onClick = onClearNotice, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        color = if (noticeIsInformational) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        InfoCard(
            title = stringResource(titleRes),
            body = stringResource(R.string.phase_two_dashboard_body),
        )

        FinanceSection(
            state = financeState,
            onRetry = onRetryFinance,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.profile_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            InfoCard(
                title = profile.memberId?.let { "${stringResource(R.string.member_id_label)} · $it" }
                    ?: stringResource(R.string.member_id_label),
                body = "${stringResource(R.string.role_label)}: ${stringResource(profile.role.labelRes())}\n" +
                    "${stringResource(R.string.status_label)}: ${stringResource(profile.accountStatus.labelRes())}",
            )
        }

        Text(
            text = stringResource(R.string.no_demo_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(stringResource(R.string.profile))
        }
        Spacer(Modifier.height(4.dp))
    }
}
