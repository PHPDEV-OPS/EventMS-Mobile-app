package com.eventms.mobile.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventms.mobile.core.Outcome
import com.eventms.mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val mode: AuthMode = AuthMode.SIGN_IN,
    /** Organizers get event management; attendees just get their tickets synced. */
    val asOrganizer: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false
) {
    val isSignUp: Boolean get() = mode == AuthMode.SIGN_UP

    val canSubmit: Boolean
        get() = !loading &&
            email.contains("@") &&
            password.length >= MIN_PASSWORD &&
            (!isSignUp || name.isNotBlank())

    companion object {
        const val MIN_PASSWORD = 8
    }
}

enum class AuthMode { SIGN_IN, SIGN_UP }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

    fun onModeChange(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null) }

    fun onRoleChange(asOrganizer: Boolean) =
        _state.update { it.copy(asOrganizer = asOrganizer, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            val outcome = if (current.isSignUp) {
                authRepository.signUp(
                    email = current.email,
                    password = current.password,
                    name = current.name,
                    asOrganizer = current.asOrganizer
                )
            } else {
                authRepository.signIn(
                    email = current.email,
                    password = current.password,
                    asOrganizer = current.asOrganizer
                )
            }

            when (outcome) {
                is Outcome.Success ->
                    _state.update { it.copy(loading = false, signedIn = true) }
                is Outcome.Failure ->
                    _state.update { it.copy(loading = false, error = outcome.message) }
            }
        }
    }

    fun consumeSignedIn() = _state.update { it.copy(signedIn = false) }
}
