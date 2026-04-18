package com.example.whereabouts.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

private val SharingGreen = Color(0xFF2E7D32)
private val StopRed     = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: SharingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val session by viewModel.sessionState.collectAsState()
    var showDurationSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Runtime POST_NOTIFICATIONS permission (Android 13+)
    // Requested when user taps FAB — after grant/deny, open the duration sheet
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed regardless — notification is nice-to-have, not a blocker
        showDurationSheet = true
    }

    fun onFabClick() {
        if (session.isActive) {
            viewModel.stopSharing()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            showDurationSheet = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {

        // ── Map placeholder (replaced in Phase 4) ──────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE8EAF6)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Map loads here in Phase 4",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Sharing active banner ───────────────────────────────────────────
        if (session.isActive) {
            SharingBanner(
                session = session,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // ── FAB ─────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { onFabClick() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = if (session.isActive) StopRed else SharingGreen,
            contentColor = Color.White,
            shape = if (session.isActive) RoundedCornerShape(12.dp) else CircleShape
        ) {
            Icon(
                imageVector = if (session.isActive) Icons.Filled.Stop else Icons.Filled.Add,
                contentDescription = if (session.isActive) "Stop sharing" else "Start sharing"
            )
        }
    }

    // ── Duration bottom sheet ───────────────────────────────────────────────
    if (showDurationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDurationSheet = false },
            sheetState = sheetState
        ) {
            DurationPickerSheet(
                onDurationSelected = { durationMinutes ->
                    scope.launch {
                        sheetState.hide()
                        showDurationSheet = false
                        viewModel.startSharing(durationMinutes)
                    }
                }
            )
        }
    }
}

// ── Sharing active banner ─────────────────────────────────────────────────────

@Composable
private fun SharingBanner(
    session: com.example.whereabouts.data.repository.SessionState,
    modifier: Modifier = Modifier
) {
    val label = if (!session.hasFixedDuration) {
        "Sharing · Until you stop"
    } else {
        val mins = session.remainingMinutes()
        when {
            mins > 60 -> "Sharing · ${mins / 60}h ${mins % 60}m remaining"
            mins > 0  -> "Sharing · ${mins}m remaining"
            else      -> "Sharing · Ending soon…"
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SharingGreen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing dot indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Duration picker sheet ─────────────────────────────────────────────────────

@Composable
private fun DurationPickerSheet(
    onDurationSelected: (Long?) -> Unit
) {
    data class DurationOption(
        val label: String,
        val subtitle: String,
        val minutes: Long?  // null = Until I stop
    )

    val options = listOf(
        DurationOption("1 hour",       "Quick trip or errand",      60L),
        DurationOption("8 hours",      "Work day or long outing",   480L),
        DurationOption("Until I stop", "Manual control",            null)
    )

    var selected by remember { mutableStateOf<Long?>(-2L) } // sentinel = nothing picked

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "How long to share?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Your circle sees your location.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        options.forEach { option ->
            val isSelected = selected == option.minutes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else Color.Transparent
                    )
                    .clickable {
                        selected = option.minutes
                        onDurationSelected(option.minutes)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = option.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "›",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
