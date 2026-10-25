package com.smarttransparentmess.erp.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.members.InvitationDeliveryStatus
import com.smarttransparentmess.erp.domain.members.InvitationStatus
import com.smarttransparentmess.erp.domain.members.ManagedLifecycleStatus
import com.smarttransparentmess.erp.domain.members.ManagedMember
import com.smarttransparentmess.erp.domain.members.MemberCapacity
import com.smarttransparentmess.erp.domain.members.MemberInvitation
import com.smarttransparentmess.erp.domain.members.MemberManagementSnapshot
import com.smarttransparentmess.erp.domain.members.MemberRoleFilter
import com.smarttransparentmess.erp.domain.members.MemberSort
import com.smarttransparentmess.erp.domain.members.MemberStatusFilter
import com.smarttransparentmess.erp.domain.members.filterAndSortMembers
import com.smarttransparentmess.erp.domain.model.AppRole
import com.smarttransparentmess.erp.presentation.AdminFinanceUiState
import com.smarttransparentmess.erp.presentation.MemberManagementNotice
import com.smarttransparentmess.erp.presentation.MemberManagementUiState
import com.smarttransparentmess.erp.presentation.MemberManagementViewModel
import com.smarttransparentmess.erp.presentation.OperationsUiState
import com.smarttransparentmess.erp.ui.FormCard
import com.smarttransparentmess.erp.ui.InfoCard
import com.smarttransparentmess.erp.ui.KeyValueRow
import com.smarttransparentmess.erp.ui.LoadingContent
import com.smarttransparentmess.erp.ui.PrimaryActionButton
import com.smarttransparentmess.erp.ui.SelectionField
import com.smarttransparentmess.erp.ui.StatusPill
import com.smarttransparentmess.erp.ui.theme.Spacing

private enum class MemberManagementTab(val titleRes: Int) {
    MEMBERS(R.string.members_tab_members),
    INVITATIONS(R.string.members_tab_invitations),
    ROLES(R.string.members_tab_roles),
    AUDIT(R.string.members_tab_audit),
}

@Composable
fun MemberManagementScreen(
    state: MemberManagementUiState,
    financeState: AdminFinanceUiState,
    operationsState: OperationsUiState,
    viewModel: MemberManagementViewModel,
    currentUserId: String,
    currentRole: AppRole,
    onRefreshAuxiliary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    val snapshot = state.snapshot
    val selectedMember = snapshot?.members?.firstOrNull { it.userId == selectedMemberId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.XLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.members_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.members_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    viewModel.refresh()
                    onRefreshAuxiliary()
                },
                enabled = !state.isLoading && !state.isSubmitting,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
            }
        }

        MemberManagementFeedback(state, viewModel::clearFeedback)
        if (state.isLoading && snapshot != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        when {
            snapshot == null && state.isLoading -> LoadingContent(
                message = stringResource(R.string.members_loading),
                modifier = Modifier.fillMaxWidth(),
            )
            snapshot == null -> InfoCard(
                title = stringResource(R.string.members_title),
                body = stringResource(R.string.members_no_data),
            )
            else -> {
                CapacityCard(snapshot)
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    MemberManagementTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                viewModel.clearFeedback()
                            },
                            text = { Text(stringResource(tab.titleRes)) },
                        )
                    }
                }
                when (MemberManagementTab.entries[selectedTab]) {
                    MemberManagementTab.MEMBERS -> MembersSection(
                        snapshot = snapshot,
                        state = state,
                        viewModel = viewModel,
                        currentRole = currentRole,
                        onSelectMember = { selectedMemberId = it },
                    )
                    MemberManagementTab.INVITATIONS -> InvitationsSection(
                        snapshot = snapshot,
                        state = state,
                        viewModel = viewModel,
                        currentRole = currentRole,
                    )
                    MemberManagementTab.ROLES -> RolesSection(
                        snapshot = snapshot,
                        onSelectMember = {
                            selectedMemberId = it
                            selectedTab = MemberManagementTab.MEMBERS.ordinal
                        },
                    )
                    MemberManagementTab.AUDIT -> AuditSection(snapshot)
                }
                if (selectedMember != null && selectedTab == MemberManagementTab.MEMBERS.ordinal) {
                    MemberDetailSection(
                        member = selectedMember,
                        currentUserId = currentUserId,
                        currentRole = currentRole,
                        state = state,
                        financeState = financeState,
                        operationsState = operationsState,
                        viewModel = viewModel,
                        onClose = { selectedMemberId = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun CapacityCard(snapshot: MemberManagementSnapshot) {
    val capacity = remember(snapshot) { MemberCapacity.from(snapshot) }
    FormCard(title = stringResource(R.string.members_capacity_title)) {
        KeyValueRow(
            stringResource(R.string.members_capacity_members),
            stringResource(R.string.members_capacity_value, capacity.totalMembers, MemberCapacity.MAX_MEMBERS),
        )
        KeyValueRow(
            stringResource(R.string.members_capacity_pending),
            capacity.pendingMembers.toString(),
        )
        KeyValueRow(
            stringResource(R.string.members_capacity_admins),
            stringResource(R.string.members_capacity_value, capacity.totalAdmins, MemberCapacity.MAX_ADMINS),
        )
        KeyValueRow(stringResource(R.string.members_capacity_main_admin), capacity.activeMainAdmins.toString())
        Text(
            stringResource(R.string.members_capacity_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MembersSection(
    snapshot: MemberManagementSnapshot,
    state: MemberManagementUiState,
    viewModel: MemberManagementViewModel,
    currentRole: AppRole,
    onSelectMember: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var statusName by rememberSaveable { mutableStateOf(MemberStatusFilter.ALL.name) }
    var roleName by rememberSaveable { mutableStateOf(MemberRoleFilter.ALL.name) }
    var sortName by rememberSaveable { mutableStateOf(MemberSort.NAME.name) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val status = MemberStatusFilter.valueOf(statusName)
    val role = MemberRoleFilter.valueOf(roleName)
    val sort = MemberSort.valueOf(sortName)
    val members = remember(snapshot.members, query, status, role, sort) {
        filterAndSortMembers(snapshot.members, query, status, role, sort)
    }

    if (showAdd) {
        AddMemberForm(
            state = state,
            viewModel = viewModel,
            currentRole = currentRole,
            snapshot = snapshot,
            onClose = { showAdd = false },
        )
    } else {
        PrimaryActionButton(
            text = stringResource(R.string.members_add_new),
            onClick = { showAdd = true },
            enabled = !state.isSubmitting,
        )
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.members_search)) },
    )
    SelectionField(
        label = stringResource(R.string.members_filter_status),
        options = MemberStatusFilter.entries.toList(),
        selected = status,
        display = { stringResource(it.titleRes()) },
        onSelected = { statusName = it.name },
    )
    SelectionField(
        label = stringResource(R.string.members_filter_role),
        options = MemberRoleFilter.entries.toList(),
        selected = role,
        display = { stringResource(it.titleRes()) },
        onSelected = { roleName = it.name },
    )
    SelectionField(
        label = stringResource(R.string.members_sort),
        options = MemberSort.entries.toList(),
        selected = sort,
        display = { stringResource(it.titleRes()) },
        onSelected = { sortName = it.name },
    )
    Text(
        stringResource(R.string.members_result_count, members.size),
        style = MaterialTheme.typography.labelLarge,
    )
    if (members.isEmpty()) {
        InfoCard(
            title = stringResource(R.string.members_no_results),
            body = stringResource(R.string.members_no_results_body),
        )
    } else {
        members.forEach { member ->
            MemberListCard(member = member, onDetails = { onSelectMember(member.userId) })
        }
    }
}

@Composable
private fun AddMemberForm(
    state: MemberManagementUiState,
    viewModel: MemberManagementViewModel,
    currentRole: AppRole,
    snapshot: MemberManagementSnapshot,
    onClose: () -> Unit,
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var memberId by rememberSaveable { mutableStateOf("") }
    var joinDate by rememberSaveable { mutableStateOf("") }
    var roleName by rememberSaveable { mutableStateOf(AppRole.MEMBER.name) }
    val role = AppRole.valueOf(roleName)
    val capacity = remember(snapshot) { MemberCapacity.from(snapshot) }
    val roles = if (currentRole == AppRole.MAIN_ADMIN) {
        listOf(AppRole.MEMBER, AppRole.ADMIN)
    } else {
        listOf(AppRole.MEMBER)
    }
    val roleCapacityAvailable = role != AppRole.ADMIN || capacity.canAddAdmin

    FormCard(
        title = stringResource(R.string.members_add_title),
        subtitle = stringResource(R.string.members_add_subtitle),
    ) {
        OutlinedTextField(
            value = fullName, onValueChange = { fullName = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.full_name_label)) },
        )
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.email_label)) },
        )
        OutlinedTextField(
            value = phone, onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.phone_label)) },
        )
        OutlinedTextField(
            value = memberId, onValueChange = { memberId = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.member_id_label)) },
        )
        OutlinedTextField(
            value = joinDate, onValueChange = { joinDate = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.members_join_date_input)) },
        )
        SelectionField(
            label = stringResource(R.string.role_label),
            options = roles,
            selected = role,
            display = { stringResource(it.titleRes()) },
            onSelected = { roleName = it.name },
        )
        if (!capacity.canAddMember || !roleCapacityAvailable) {
            Text(
                stringResource(R.string.members_capacity_reached),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.members_send_invitation),
            onClick = { viewModel.addMember(fullName, email, phone, memberId, joinDate, role) },
            enabled = capacity.canAddMember && roleCapacityAvailable,
            loading = state.isSubmitting,
        )
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.close))
        }
    }
}

@Composable
private fun MemberListCard(member: ManagedMember, onDetails: () -> Unit) {
    FormCard(title = member.fullName) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(member.memberId ?: stringResource(R.string.not_assigned), style = MaterialTheme.typography.bodyMedium)
            StatusPill(stringResource(member.lifecycleStatus.titleRes()))
        }
        Text(member.email, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.members_role_status, stringResource(member.effectiveRole.titleRes())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.members_view_details))
        }
    }
}

@Composable
private fun InvitationsSection(
    snapshot: MemberManagementSnapshot,
    state: MemberManagementUiState,
    viewModel: MemberManagementViewModel,
    currentRole: AppRole,
) {
    var cancelTarget by remember { mutableStateOf<MemberInvitation?>(null) }
    Text(
        stringResource(R.string.members_invitation_explanation),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (snapshot.invitations.isEmpty()) {
        InfoCard(
            title = stringResource(R.string.members_tab_invitations),
            body = stringResource(R.string.members_no_invitations),
        )
    } else {
        snapshot.invitations.forEach { invitation ->
            FormCard(title = invitation.fullName, subtitle = invitation.email) {
                KeyValueRow(stringResource(R.string.member_id_label), invitation.memberId)
                KeyValueRow(stringResource(R.string.role_label), stringResource(invitation.intendedRole.titleRes()))
                KeyValueRow(stringResource(R.string.members_invitation_status), stringResource(invitation.status.titleRes()))
                KeyValueRow(stringResource(R.string.members_delivery_status), stringResource(invitation.deliveryStatus.titleRes()))
                KeyValueRow(stringResource(R.string.members_delivery_attempts), invitation.deliveryAttempts.toString())
                invitation.lastDeliveryError?.let {
                    Text(
                        stringResource(R.string.members_safe_delivery_code, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (invitation.status == InvitationStatus.PENDING) {
                    val allowed = currentRole == AppRole.MAIN_ADMIN || invitation.intendedRole == AppRole.MEMBER
                    PrimaryActionButton(
                        text = stringResource(R.string.members_resend_invitation),
                        onClick = { viewModel.resendInvitation(invitation.id) },
                        enabled = allowed && !state.isSubmitting,
                    )
                    TextButton(
                        onClick = { cancelTarget = invitation },
                        enabled = allowed && !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.members_cancel_invitation))
                    }
                }
            }
        }
    }
    cancelTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text(stringResource(R.string.members_cancel_invitation)) },
            text = { Text(stringResource(R.string.members_cancel_invitation_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelInvitation(target.id)
                    cancelTarget = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RolesSection(snapshot: MemberManagementSnapshot, onSelectMember: (String) -> Unit) {
    val managers = snapshot.members.filter { it.effectiveRole != AppRole.MEMBER || it.isCurrentManager }
    InfoCard(
        title = stringResource(R.string.members_roles_title),
        body = stringResource(R.string.members_roles_body),
    )
    if (managers.isEmpty()) {
        InfoCard(
            title = stringResource(R.string.members_tab_roles),
            body = stringResource(R.string.members_no_managers),
        )
    } else {
        managers.forEach { member -> MemberListCard(member = member, onDetails = { onSelectMember(member.userId) }) }
    }
}

@Composable
private fun AuditSection(snapshot: MemberManagementSnapshot) {
    val memberNames = snapshot.members.associate { it.userId to it.fullName }
    InfoCard(
        title = stringResource(R.string.members_audit_title),
        body = stringResource(R.string.members_audit_body),
    )
    if (snapshot.auditEvents.isEmpty()) {
        InfoCard(
            title = stringResource(R.string.members_tab_audit),
            body = stringResource(R.string.members_no_audit),
        )
    } else {
        snapshot.auditEvents.forEach { event ->
            FormCard(title = event.action.replace('_', ' '), subtitle = event.createdAt) {
                KeyValueRow(
                    stringResource(R.string.members_audit_actor),
                    event.actorUserId?.let { memberNames[it] ?: it } ?: event.actorType,
                )
                event.targetUserId?.let {
                    KeyValueRow(stringResource(R.string.members_audit_target), memberNames[it] ?: it)
                }
                event.reason?.let { KeyValueRow(stringResource(R.string.reason_label), it) }
                if (event.details.isNotBlank()) {
                    Text(
                        event.details,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberManagementFeedback(state: MemberManagementUiState, onClear: () -> Unit) {
    val errorText = when {
        state.validationError != null -> stringResource(state.validationError.titleRes())
        state.operationFailed -> stringResource(R.string.members_operation_error)
        state.loadFailed -> stringResource(R.string.members_load_error)
        else -> null
    }
    if (errorText != null) {
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text(errorText, color = MaterialTheme.colorScheme.error)
        }
    }
    state.notice?.let { notice ->
        val isError = notice in setOf(
            MemberManagementNotice.DELIVERY_FAILED,
            MemberManagementNotice.DELIVERY_UNCONFIRMED,
            MemberManagementNotice.REQUEST_REJECTED,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(notice.titleRes()),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            state.deliveryDependency?.let {
                Text(
                    stringResource(R.string.members_delivery_dependency, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun AppRole.titleRes(): Int = when (this) {
    AppRole.MEMBER -> R.string.member_role
    AppRole.ADMIN -> R.string.admin_role
    AppRole.MAIN_ADMIN -> R.string.main_admin_role
}

private fun ManagedLifecycleStatus.titleRes(): Int = when (this) {
    ManagedLifecycleStatus.ACTIVE -> R.string.active_status
    ManagedLifecycleStatus.PENDING -> R.string.pending_status
    ManagedLifecycleStatus.SUSPENDED -> R.string.suspended_status
    ManagedLifecycleStatus.DEPARTED -> R.string.departed_status
}

private fun MemberStatusFilter.titleRes(): Int = when (this) {
    MemberStatusFilter.ALL -> R.string.all_status
    MemberStatusFilter.ACTIVE -> R.string.active_status
    MemberStatusFilter.PENDING -> R.string.pending_status
    MemberStatusFilter.SUSPENDED -> R.string.suspended_status
    MemberStatusFilter.DEPARTED -> R.string.departed_status
}

private fun MemberRoleFilter.titleRes(): Int = when (this) {
    MemberRoleFilter.ALL -> R.string.members_all_roles
    MemberRoleFilter.MEMBER -> R.string.member_role
    MemberRoleFilter.ADMIN -> R.string.admin_role
    MemberRoleFilter.MAIN_ADMIN -> R.string.main_admin_role
}

private fun MemberSort.titleRes(): Int = when (this) {
    MemberSort.NAME -> R.string.members_sort_name
    MemberSort.MEMBER_ID -> R.string.members_sort_id
    MemberSort.JOIN_DATE -> R.string.members_sort_join_date
    MemberSort.STATUS -> R.string.members_sort_status
}

private fun InvitationStatus.titleRes(): Int = when (this) {
    InvitationStatus.PENDING -> R.string.pending_status
    InvitationStatus.ACCEPTED -> R.string.members_invitation_accepted
    InvitationStatus.CANCELLED -> R.string.members_invitation_cancelled
}

private fun InvitationDeliveryStatus.titleRes(): Int = when (this) {
    InvitationDeliveryStatus.PENDING -> R.string.pending_status
    InvitationDeliveryStatus.SENT -> R.string.members_delivery_sent
    InvitationDeliveryStatus.FAILED -> R.string.members_delivery_failed
    InvitationDeliveryStatus.UNCONFIRMED -> R.string.members_delivery_unconfirmed
    InvitationDeliveryStatus.NOT_REQUIRED -> R.string.members_delivery_not_required
}

private fun MemberManagementNotice.titleRes(): Int = when (this) {
    MemberManagementNotice.MEMBER_ADDED -> R.string.members_notice_added
    MemberManagementNotice.INVITATION_SENT -> R.string.members_notice_invitation_sent
    MemberManagementNotice.DELIVERY_FAILED -> R.string.members_notice_delivery_failed
    MemberManagementNotice.DELIVERY_UNCONFIRMED -> R.string.members_notice_delivery_unconfirmed
    MemberManagementNotice.REQUEST_REJECTED -> R.string.members_notice_request_rejected
    MemberManagementNotice.INVITATION_CANCELLED -> R.string.members_notice_invitation_cancelled
    MemberManagementNotice.PROFILE_UPDATED -> R.string.members_notice_profile_updated
    MemberManagementNotice.LIFECYCLE_UPDATED -> R.string.members_notice_lifecycle_updated
    MemberManagementNotice.ROLE_UPDATED -> R.string.members_notice_role_updated
    MemberManagementNotice.MAIN_ADMIN_TRANSFERRED -> R.string.members_notice_main_admin_transferred
}

private fun com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError.titleRes(): Int = when (this) {
    com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError.REQUIRED_FIELDS,
    com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError.NAME -> R.string.name_invalid
    com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError.EMAIL -> R.string.members_validation_email
    com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError.PHONE -> R.string.phone_invalid
    com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError.MEMBER_ID -> R.string.members_validation_member_id
    com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError.JOIN_DATE -> R.string.validation_date
    com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError.ROLE -> R.string.members_validation_role
    com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError.REASON -> R.string.validation_reason
}
