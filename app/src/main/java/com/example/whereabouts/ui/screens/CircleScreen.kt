package com.example.whereabouts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.whereabouts.data.model.CircleContact

@Composable
fun CircleScreen(
    innerPadding: PaddingValues,
    viewModel: CircleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // ── Invite card ───────────────────────────────────────────────────────
        item {
            InviteCard(
                email        = uiState.emailInput,
                onEmailChange = viewModel::onEmailChanged,
                onSendInvite  = viewModel::sendInvite,
                isLoading    = uiState.isLoading,
                error        = uiState.inviteError,
                success      = uiState.inviteSuccess
            )
        }

        // ── Incoming requests ─────────────────────────────────────────────────
        if (uiState.incoming.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Requests",
                    count = uiState.incoming.size,
                    color = MaterialTheme.colorScheme.error
                )
            }
            items(uiState.incoming, key = { "in_${it.uid}" }) { contact ->
                IncomingRequestCard(
                    contact  = contact,
                    onAccept = { viewModel.acceptInvite(contact) },
                    onReject = { viewModel.rejectInvite(contact) }
                )
            }
        }

        // ── Accepted circle members ───────────────────────────────────────────
        if (uiState.accepted.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "My Circle",
                    count = uiState.accepted.size,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(uiState.accepted, key = { "ok_${it.uid}" }) { contact ->
                AcceptedContactCard(
                    contact  = contact,
                    onRemove = { viewModel.removeContact(contact) }
                )
            }
        }

        // ── Sent / pending invites ────────────────────────────────────────────
        if (uiState.sent.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Sent",
                    count = uiState.sent.size,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            items(uiState.sent, key = { "sent_${it.uid}" }) { contact ->
                SentInviteCard(
                    contact  = contact,
                    onCancel = { viewModel.rejectInvite(contact) }
                )
            }
        }

        // ── Empty state ───────────────────────────────────────────────────────
        if (uiState.accepted.isEmpty() && uiState.incoming.isEmpty() && uiState.sent.isEmpty()) {
            item { EmptyCircleState() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Invite card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InviteCard(
    email: String,
    onEmailChange: (String) -> Unit,
    onSendInvite: () -> Unit,
    isLoading: Boolean,
    error: String?,
    success: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text  = "Invite to Circle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = "The person must already have WhereAbouts and be signed in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value         = email,
                onValueChange = onEmailChange,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Email address") },
                leadingIcon   = { Icon(Icons.Filled.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onSendInvite() }),
                isError   = error != null,
                singleLine = true,
                shape      = RoundedCornerShape(12.dp)
            )

            // Error message
            AnimatedVisibility(visible = error != null) {
                Text(
                    text  = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Success confirmation
            AnimatedVisibility(visible = success) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector  = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint     = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text  = "Invite sent!",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick  = onSendInvite,
                modifier = Modifier.fillMaxWidth(),
                enabled  = !isLoading && email.isNotBlank(),
                shape    = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send Invite")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(Modifier.width(8.dp))
        Badge(containerColor = color.copy(alpha = 0.15f)) {
            Text(
                text  = count.toString(),
                color = color,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Avatar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContactAvatar(contact: CircleContact, sizeDp: Int = 44) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = contact.initials,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize   = (sizeDp / 2.6).sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Incoming request card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IncomingRequestCard(
    contact: CircleContact,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(contact)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = contact.name.ifBlank { contact.email },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    text  = contact.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow  = TextOverflow.Ellipsis
                )
                Text(
                    text  = "Wants to join your circle",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Accept
                FilledIconButton(
                    onClick  = onAccept,
                    modifier = Modifier.size(36.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Check,
                        contentDescription = "Accept",
                        modifier           = Modifier.size(18.dp),
                        tint               = Color.White
                    )
                }
                // Reject
                FilledIconButton(
                    onClick  = onReject,
                    modifier = Modifier.size(36.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Close,
                        contentDescription = "Reject",
                        modifier           = Modifier.size(18.dp),
                        tint               = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Accepted contact card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AcceptedContactCard(
    contact: CircleContact,
    onRemove: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(contact)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = contact.name.ifBlank { contact.email },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    text  = contact.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow  = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector        = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint     = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
            IconButton(onClick = { showConfirm = true }) {
                Icon(
                    imageVector        = Icons.Filled.PersonRemove,
                    contentDescription = "Remove from circle",
                    tint               = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title   = { Text("Remove from Circle?") },
            text    = { Text("${contact.name.ifBlank { contact.email }} will no longer see your location and vice versa.") },
            confirmButton = {
                TextButton(
                    onClick = { showConfirm = false; onRemove() },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sent invite card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SentInviteCard(
    contact: CircleContact,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(contact)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = contact.name.ifBlank { contact.email },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    text  = contact.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow  = TextOverflow.Ellipsis
                )
                Text(
                    text  = "Waiting for response…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector        = Icons.Filled.Cancel,
                    contentDescription = "Cancel invite",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyCircleState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector        = Icons.Filled.Group,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.outline
        )
        Text(
            text  = "Your circle is empty",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text      = "Invite trusted people above\nto share locations with each other.",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
