package com.example.whereabouts.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.whereabouts.data.model.ContactLocation
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

private val SharingGreen = Color(0xFF2E7D32)
private val StopRed = Color(0xFFC62828)

// Marker colour palette — assigned by contact index
private val MarkerColors = listOf(
    Color(0xFF1565C0), // blue
    Color(0xFF6A1B9A), // purple
    Color(0xFF00695C), // teal
    Color(0xFFE65100), // orange
    Color(0xFF4527A0), // deep purple
    Color(0xFF558B2F), // light green
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    sharingViewModel: SharingViewModel = hiltViewModel(),
    mapViewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val session by sharingViewModel.sessionState.collectAsState()
    val mapState by mapViewModel.uiState.collectAsState()

    var showDurationSheet by remember { mutableStateOf(false) }
    val durationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contactSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 5f) // India default
    }

    // Runtime POST_NOTIFICATIONS permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { showDurationSheet = true }

    fun onFabClick() {
        if (session.isActive) {
            sharingViewModel.stopSharing()
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

        // ── Google Map ──────────────────────────────────────────────────────
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            mapState.contacts.forEachIndexed { index, contact ->
                val color = MarkerColors[index % MarkerColors.size]
                val icon = remember(contact.uid, contact.sessionEnded) {
                    createInitialsMarkerIcon(contact.initials, color, contact.sessionEnded)
                }
                Marker(
                    state = MarkerState(position = LatLng(contact.lat, contact.lng)),
                    icon = icon,
                    title = contact.name,
                    onClick = {
                        mapViewModel.onContactSelected(contact)
                        true
                    }
                )
            }
        }

        // ── Sharing active banner ───────────────────────────────────────────
        if (session.isActive) {
            SharingBanner(
                session = session,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // ── Empty state ─────────────────────────────────────────────────────
        if (mapState.contacts.isEmpty()) {
            EmptyMapState(modifier = Modifier.align(Alignment.Center))
        }

        // ── FAB — animated green ↔ red ──────────────────────────────────────
        val fabColor by animateColorAsState(
            targetValue    = if (session.isActive) StopRed else SharingGreen,
            animationSpec  = tween(durationMillis = 300),
            label          = "fabColor"
        )
        FloatingActionButton(
            onClick = { onFabClick() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = fabColor,
            contentColor   = Color.White,
            shape = if (session.isActive) RoundedCornerShape(12.dp) else CircleShape
        ) {
            Icon(
                imageVector        = if (session.isActive) Icons.Filled.Stop else Icons.Filled.Add,
                contentDescription = if (session.isActive) "Stop sharing" else "Start sharing"
            )
        }
    }

    // ── Duration bottom sheet ───────────────────────────────────────────────
    if (showDurationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDurationSheet = false },
            sheetState = durationSheetState
        ) {
            DurationPickerSheet(
                onDurationSelected = { durationMinutes ->
                    scope.launch {
                        durationSheetState.hide()
                        showDurationSheet = false
                        sharingViewModel.startSharing(durationMinutes)
                    }
                }
            )
        }
    }

    // ── Contact detail bottom sheet ─────────────────────────────────────────
    if (mapState.selectedContact != null) {
        ModalBottomSheet(
            onDismissRequest = { mapViewModel.onContactDismissed() },
            sheetState = contactSheetState
        ) {
            ContactDetailSheet(
                contact = mapState.selectedContact!!,
                address = mapState.selectedContactAddress,
                isLoadingAddress = mapState.isLoadingAddress
            )
        }
    }
}

// ── Sharing banner ────────────────────────────────────────────────────────────

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

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyMapState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No one sharing yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Add people to your circle\nfrom the Circle tab",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── Contact detail sheet ──────────────────────────────────────────────────────

@Composable
private fun ContactDetailSheet(
    contact: ContactLocation,
    address: String?,
    isLoadingAddress: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp)
    ) {
        // Avatar + name row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.initials,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = contact.name.ifBlank { contact.email },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Updated ${contact.lastSeenLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Battery row
        if (contact.batteryPercent >= 0) {
            val batteryColor = when {
                contact.batteryPercent < 10 -> Color(0xFFD32F2F) // red
                contact.batteryPercent < 20 -> Color(0xFFF57C00) // amber
                else                        -> Color(0xFF388E3C) // green
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.BatteryFull,
                    contentDescription = "Battery",
                    tint = batteryColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Battery",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${contact.batteryPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = batteryColor
                )
            }
            // Battery bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(contact.batteryPercent / 100f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(batteryColor)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Address row
        Text(
            text = "Last known location",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        when {
            isLoadingAddress -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            address != null -> Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Session status
        if (contact.sessionEnded) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "Session ended · Last seen ${contact.lastSeenLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ── Duration picker sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPickerSheet(onDurationSelected: (Long?) -> Unit) {
    data class DurationOption(val label: String, val subtitle: String, val minutes: Long?)

    val options = listOf(
        DurationOption("1 hour", "Quick trip or errand", 60L),
        DurationOption("8 hours", "Work day or long outing", 480L),
        DurationOption("Until I stop", "Manual control", null)
    )

    var selected by remember { mutableStateOf<Long?>(-2L) }

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
                Text(text = "›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Custom marker icon (initials on a coloured circle) ────────────────────────

private fun createInitialsMarkerIcon(
    initials: String,
    color: Color,
    isStale: Boolean
): com.google.android.gms.maps.model.BitmapDescriptor {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val bgColor = if (isStale) android.graphics.Color.argb(255, 158, 158, 158)
                  else color.toArgb()

    // Background circle
    val circlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = bgColor
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)

    // White border for stale markers
    if (isStale) {
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, borderPaint)
    }

    // Initials text
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = size * 0.38f
        isFakeBoldText = true
    }
    val yPos = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(initials.take(2), size / 2f, yPos, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
