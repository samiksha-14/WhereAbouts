package com.example.whereabouts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val thresholdOptions = listOf(15, 30, 60, 120)
private val thresholdLabels  = listOf("15 min", "30 min", "1 hr", "2 hr")

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ── Profile card ──────────────────────────────────────────────────────
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Initials avatar
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = uiState.userName
                        .split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                        .ifBlank { uiState.userEmail.take(2).uppercase() }

                    if (initials.isNotBlank()) {
                        Text(
                            text       = initials,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 20.sp
                        )
                    } else {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        text  = uiState.userName.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = uiState.userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Alerts section ────────────────────────────────────────────────────
        SectionCard {
            Column(modifier = Modifier.padding(16.dp)) {

                // Header row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = "Inactivity Alert",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked         = uiState.alertEnabled,
                        onCheckedChange = viewModel::setAlertEnabled
                    )
                }

                Text(
                    text  = "Notifies you if your location hasn't updated while sharing is active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Threshold picker — only visible when alerts are on
                if (uiState.alertEnabled) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text  = "Alert after",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        thresholdOptions.forEachIndexed { index, minutes ->
                            FilterChip(
                                selected = uiState.alertThresholdMinutes == minutes,
                                onClick  = { viewModel.setAlertThreshold(minutes) },
                                label    = { Text(thresholdLabels[index]) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor     = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── Account section ───────────────────────────────────────────────────
        SectionCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(
                    onClick  = { showSignOutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Logout,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }

        // App version note at the bottom
        Text(
            text      = "WhereAbouts v1.0",
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.outline,
            modifier  = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        )
    }

    // ── Sign-out confirmation dialog ──────────────────────────────────────────
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title   = { Text("Sign Out?") },
            text    = { Text("You'll stop sharing your location and need to sign in again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                        onSignOut()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper — card wrapper for each settings group
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        content()
    }
}
