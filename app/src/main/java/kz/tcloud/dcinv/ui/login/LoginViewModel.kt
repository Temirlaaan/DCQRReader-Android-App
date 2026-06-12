package kz.tcloud.dcinv.ui.login

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.auth.AuthManager
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** Intent that opens the Keycloak login (Custom Tab). */
    fun authRequestIntent(): Intent = authManager.createAuthRequestIntent()

    fun onAuthResult(data: Intent?) {
        if (data == null) {
            _uiState.update { it.copy(error = "Вход отменён") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = authManager.handleAuthResponse(data)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(loading = false, success = true)
                } else {
                    it.copy(
                        loading = false,
                        error = result.exceptionOrNull()?.message ?: "Ошибка входа",
                    )
                }
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(error = null) }
}
