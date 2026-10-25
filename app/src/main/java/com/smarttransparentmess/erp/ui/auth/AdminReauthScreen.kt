package com.smarttransparentmess.erp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.model.MemberProfile
import com.smarttransparentmess.erp.ui.BrandMark
import com.smarttransparentmess.erp.ui.labelRes

@Composable
fun AdminReauthScreen(
    profile: MemberProfile,
    isBusy: Boolean,
    onContinueToAdminLogin: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(compact = true)
        Spacer(Modifier.height(30.dp))
        Text(
            text = stringResource(R.string.admin_login_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.admin_reauth_body, profile.fullName, stringResource(profile.role.labelRes())),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onContinueToAdminLogin,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.continue_admin_login)) }
        OutlinedButton(
            onClick = onCancel,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.cancel)) }
    }
}
