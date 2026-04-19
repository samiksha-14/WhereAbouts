package com.example.whereabouts.ui.screens

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whereabouts.data.model.ContactLocation
import com.example.whereabouts.data.repository.MapRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

data class MapUiState(
    val contacts: List<ContactLocation> = emptyList(),
    val selectedContact: ContactLocation? = null,
    val selectedContactAddress: String? = null,
    val isLoadingAddress: Boolean = false
)

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapRepository: MapRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _selectedContact = MutableStateFlow<ContactLocation?>(null)
    private val _selectedContactAddress = MutableStateFlow<String?>(null)
    private val _isLoadingAddress = MutableStateFlow(false)

    // ── Contact list: Firestore circle UIDs → per-contact RTDB flows ─────────

    private val myUid: String get() = auth.currentUser?.uid ?: ""

    /**
     * Watches accepted contact UIDs from Firestore.
     * For each UID, fetches their profile then subscribes to their RTDB location.
     * Uses flatMapLatest so changing the contact list cancels old listeners automatically.
     */
    private val contactsFlow = flow {
        emitAll(
            mapRepository.observeAcceptedContactUids(myUid)
                .flatMapLatest { uids ->
                    if (uids.isEmpty()) return@flatMapLatest flowOf(emptyList())

                    // Fetch all profiles (one-shot), then build per-contact location flows
                    flow {
                        val profiledFlows = uids.map { uid ->
                            val (name, email, photo) = mapRepository.getUserProfile(uid)
                            mapRepository.observeContactLocation(uid, name, email, photo)
                        }

                        // Combine dynamic list of flows into a single list emission
                        emitAll(
                            combine(profiledFlows) { array ->
                                array.filterNotNull()
                            }
                        )
                    }
                }
        )
    }

    // ── Public UI state ───────────────────────────────────────────────────────

    val uiState: StateFlow<MapUiState> = combine(
        contactsFlow,
        _selectedContact,
        _selectedContactAddress,
        _isLoadingAddress
    ) { contacts, selected, address, loadingAddress ->
        MapUiState(
            contacts             = contacts,
            selectedContact      = selected,
            selectedContactAddress = address,
            isLoadingAddress     = loadingAddress
        )
    }.stateIn(
        scope         = viewModelScope,
        started       = SharingStarted.WhileSubscribed(5_000),
        initialValue  = MapUiState()
    )

    // ── Contact selection (tap marker) ────────────────────────────────────────

    fun onContactSelected(contact: ContactLocation) {
        _selectedContact.value = contact
        _selectedContactAddress.value = null
        reverseGeocode(contact.lat, contact.lng)
    }

    fun onContactDismissed() {
        _selectedContact.value = null
        _selectedContactAddress.value = null
    }

    // ── Reverse geocoding ─────────────────────────────────────────────────────

    private fun reverseGeocode(lat: Double, lng: Double) {
        viewModelScope.launch {
            _isLoadingAddress.value = true
            val address = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Non-blocking API on Android 13+
                        var result: String? = null
                        geocoder.getFromLocation(lat, lng, 1) { addresses ->
                            result = addresses.firstOrNull()?.let { formatAddress(it) }
                        }
                        // Give it a moment to complete
                        kotlinx.coroutines.delay(500)
                        result
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lng, 1)
                            ?.firstOrNull()
                            ?.let { formatAddress(it) }
                    }
                } catch (_: Exception) {
                    null
                }
            }
            _selectedContactAddress.value = address ?: "Address unavailable"
            _isLoadingAddress.value = false
        }
    }

    private fun formatAddress(address: android.location.Address): String {
        val parts = listOfNotNull(
            address.subThoroughfare,
            address.thoroughfare,
            address.locality ?: address.subAdminArea,
            address.adminArea
        )
        return parts.joinToString(", ").ifBlank { "Unknown location" }
    }
}
