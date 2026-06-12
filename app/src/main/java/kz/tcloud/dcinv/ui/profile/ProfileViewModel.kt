package kz.tcloud.dcinv.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.auth.AuthManager
import kz.tcloud.dcinv.data.auth.IdTokenClaims
import kz.tcloud.dcinv.data.auth.parseIdTokenClaims
import kz.tcloud.dcinv.data.network.ApiException
import kz.tcloud.dcinv.data.network.dto.SessionInfo
import kz.tcloud.dcinv.data.repository.SessionRepository
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class ProfileUiState(
    val claims: IdTokenClaims? = null,
    val shift: SessionInfo? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileUiState(claims = parseIdTokenClaims(authManager.state.value.idToken)),
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val shift = sessionRepository.activeSession()
                _state.update { it.copy(loading = false, shift = shift) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun startShift() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val shift = sessionRepository.startShift()
                _state.update { it.copy(loading = false, shift = shift) }
            } catch (e: ApiException) {
                if (e.code == "SESSION_ALREADY_ACTIVE") refresh()
                else _state.update { it.copy(loading = false, error = e.userMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun endShift() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                sessionRepository.endShift()
                _state.update { it.copy(loading = false, shift = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun signOut() = authManager.signOut()

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun ApiException.userMessage(): String =
        apiError?.userMessage ?: apiError?.message ?: message ?: "Ошибка"
}
