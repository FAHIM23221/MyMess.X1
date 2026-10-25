package com.smarttransparentmess.erp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.model.MemberProfile
import com.smarttransparentmess.erp.presentation.AuthNotice
import com.smarttransparentmess.erp.ui.StatusPill
import com.smarttransparentmess.erp.ui.labelRes
import com.smarttransparentmess.erp.ui.textOrNull

@Composable
fun ProfileScreen(
    profile: MemberProfile,
    modifier: Modifier = Modifier,
    isBusy: Boolean,
    notice: AuthNotice?,
    onSave: (String, String) -> Unit,
    onOpenAppInformation: () -> Unit,
    onSignOut: () -> Unit,
    onClearNotice: () -> Unit,
) {
    var editing by rememberSaveable(profile.userId) { mutableStateOf(false) }
    var fullName by rememberSaveable(profile.userId) { mutableStateOf(profile.fullName) }
    var phoneNumber by rememberSaveable(profile.userId) { mutableStateOf(profile.phoneNumber.orEmpty()) }

    LaunchedEffect(profile.userId, profile.fullName, profile.phoneNumber, profile.updatedAt) {
        fullName = profile.fullName
        phoneNumber = profile.phoneNumber.orEmpty()
    }
    LaunchedEffect(notice) {
        if (notice == AuthNotice.PROFILE_SAVED) editing = false
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.profile), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.width(70.dp).height(70.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                    ) {
                        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                profile.fullName.trim().firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(0.74f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(profile.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        StatusPill(text = stringResource(profile.role.labelRes()))
                    }
                }

                if (!notice?.textOrNull().isNullOrBlank()) {
                    Text(
                        text = notice?.textOrNull().orEmpty(),
                        color = if (notice == AuthNotice.PROFILE_SAVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; if (notice != null) onClearNotice() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.full_name_label)) },
                    singleLine = true,
                    readOnly = !editing || isBusy,
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it; if (notice != null) onClearNotice() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.phone_label)) },
                    singleLine = true,
                    readOnly = !editing || isBusy,
                )
                ReadOnlyField(stringResource(R.string.member_id_label), profile.memberId ?: stringResource(R.string.not_assigned))
                ReadOnlyField(stringResource(R.string.room_label), profile.roomNumber ?: stringResource(R.string.not_assigned))
                ReadOnlyField(stringResource(R.string.seat_label), profile.seatNumber ?: stringResource(R.string.not_assigned))
                ReadOnlyField(stringResource(R.string.join_date_label), profile.joinDate ?: stringResource(R.string.not_provided))
                ReadOnlyField(stringResource(R.string.status_label), stringResource(profile.accountStatus.labelRes()))
                ReadOnlyField(
                    stringResource(R.string.manager_status_label),
                    stringResource(if (profile.isCurrentManager) R.string.manager_yes else R.string.manager_no),
                )
                Text(
                    text = stringResource(R.string.edit_profile),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        if (editing) onSave(fullName, phoneNumber) else editing = true
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isBusy,
                ) {
                    Text(stringResource(if (isBusy) R.string.saving else if (editing) R.string.save_profile else R.string.edit_profile))
                }
                if (editing) {
                    TextButton(
                        onClick = {
                            fullName = profile.fullName
                            phoneNumber = profile.phoneNumber.orEmpty()
                            editing = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                    ) { Text(stringResource(R.string.cancel)) }
                }
            }
        }

        OutlinedButton(onClick = onOpenAppInformation, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(stringResource(R.string.app_information))
        }
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), enabled = !isBusy) {
            Text(stringResource(R.string.sign_out))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
