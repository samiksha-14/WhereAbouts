package com.example.whereabouts.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class SignInState {
    object Idle : SignInState()
    object Loading : SignInState()
    object Success : SignInState()
    data class Error(val message: String) : SignInState()
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _state.value = SignInState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user ?: throw Exception("Authentication failed")

                // Upsert Firestore profile on every sign-in (merge so existing
                // fields like fcmToken are not overwritten on returning users).
                val profile = mapOf(
                    "name"      to (user.displayName ?: ""),
                    "email"     to (user.email?.lowercase() ?: ""),
                    "photoUrl"  to (user.photoUrl?.toString() ?: ""),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("users")
                    .document(user.uid)
                    .set(profile, SetOptions.merge())
                    .await()

                _state.value = SignInState.Success
            } catch (e: Exception) {
                _state.value = SignInState.Error(e.message ?: "Sign in failed")
            }
        }
    }
}
