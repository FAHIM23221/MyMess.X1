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
import com.smarttransparentmess.erp.presentation.ProfileUnavailableReason
import com.smarttransparentmess.erp.ui.BrandMark
import com.smarttransparentmess.erp.ui.bodyRes
import com.smarttransparentmess.erp.ui.titleRes

@Composable
fun AccessUnavailableScreen(
    reason: ProfileUnavailableReason,
    isBusy: Boolean,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(compact = true)
        Spacer(Modifier.height(30.dp))
        Text(
            text = stringResource(reason.titleRes()),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(reason.bodyRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (reason == ProfileUnavailableReason.PROFILE_LOAD_FAILED) {
            Button(onClick = onRetry, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.retry))
            }
        }
        OutlinedButton(onClick = onSignOut, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sign_out))
        }
    }
}
