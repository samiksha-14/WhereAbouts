package com.example.whereabouts.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whereabouts.data.model.CircleContact
import com.example.whereabouts.data.repository.CircleRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CircleUiState(
    val accepted: List<CircleContact> = emptyList(),
    val incoming: List<CircleContact> = emptyList(), // pending, others invited me
    val sent: List<CircleContact>     = emptyList(), // pending, I invited them
    val isLoading: Boolean     = false,
    val emailInput: String     = "",
    val inviteError: String?   = null,
    val inviteSuccess: Boolean = false
)

@HiltViewModel
class CircleViewModel @Inject constructor(
    private val circleRepository: CircleRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val myUid: String get() = auth.currentUser?.uid ?: ""

    private val _isLoading     = MutableStateFlow(false)
    private val _emailInput    = MutableStateFlow("")
    private val _inviteError   = MutableStateFlow<String?>(null)
    private val _inviteSuccess = MutableStateFlow(false)

    private val circleFlow = circleRepository.observeCircle(myUid)

    val uiState: StateFlow<CircleUiState> = combine(
        circleFlow,
        _isLoading,
        _emailInput,
        _inviteError,
        _inviteSuccess
    ) { contacts, loading, email, error, success ->
        val uid = myUid
        CircleUiState(
            accepted      = contacts.filter { it.status == "accepted" },
            incoming      = contacts.filter { it.status == "pending" && it.initiatedBy != uid },
            sent          = contacts.filter { it.status == "pending" && it.initiatedBy == uid },
            isLoading     = loading,
            emailInput    = email,
            inviteError   = error,
            inviteSuccess = success
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = CircleUiState()
    )

    // ── Email input ───────────────────────────────────────────────────────────

    fun onEmailChanged(value: String) {
        _emailInput.value    = value
        _inviteError.value   = null
        _inviteSuccess.value = false
    }

    // ── Send invite ───────────────────────────────────────────────────────────

    fun sendInvite() {
        val email       = _emailInput.value.trim()
        val currentUser = auth.currentUser ?: return

        if (email.isBlank()) {
            _inviteError.value = "Enter an email address"
            return
        }
        if (email.equals(currentUser.email, ignoreCase = true)) {
            _inviteError.value = "You can't invite yourself"
            return
        }

        viewModelScope.launch {
            _isLoading.value   = true
            _inviteError.value = null

            // 1) Look up target user
            val target = circleRepository.findUserByEmail(email)
            if (target == null) {
                _inviteError.value = "No WhereAbouts account found for that email"
                _isLoading.value   = false
                return@launch
            }

            // 2) Prevent duplicate invites
            val state = uiState.value
            val alreadyExists = (state.accepted + state.incoming + state.sent)
                .any { it.uid == target.uid }
            if (alreadyExists) {
                _inviteError.value = "Already in your circle or invite pending"
                _isLoading.value   = false
                return@launch
            }

            // 3) Build my profile for the mirrored doc
            val me = CircleContact(
                uid      = currentUser.uid,
                name     = currentUser.displayName ?: "",
                email    = currentUser.email       ?: "",
                photoUrl = currentUser.photoUrl?.toString() ?: ""
            )

            // 4) Write to Firestore
            val result = circleRepository.sendInvite(me, target)
            if (result.isSuccess) {
                _emailInput.value    = ""
                _inviteSuccess.value = true
            } else {
                _inviteError.value = "Failed to send invite. Please try again."
            }
            _isLoading.value = false
        }
    }

    // ── Accept / reject / remove ──────────────────────────────────────────────

    fun acceptInvite(contact: CircleContact) {
        viewModelScope.launch { circleRepository.acceptInvite(myUid, contact.uid) }
    }

    fun rejectInvite(contact: CircleContact) {
        viewModelScope.launch { circleRepository.removeContact(myUid, contact.uid) }
    }

    fun removeContact(contact: CircleContact) {
        viewModelScope.launch { circleRepository.removeContact(myUid, contact.uid) }
    }

    fun clearInviteSuccess() { _inviteSuccess.value = false }
}
