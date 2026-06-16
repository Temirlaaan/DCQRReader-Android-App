package kz.tcloud.dcinv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.BuildConfig
import kz.tcloud.dcinv.data.auth.AuthManager
import kz.tcloud.dcinv.data.auth.IdTokenClaims
import kz.tcloud.dcinv.data.auth.parseIdTokenClaims
import kz.tcloud.dcinv.data.network.ApiException
import kz.tcloud.dcinv.data.network.userMessageOrNull
import kz.tcloud.dcinv.data.network.dto.SessionInfo
import kz.tcloud.dcinv.data.prefs.ThemeMode
import kz.tcloud.dcinv.data.prefs.ThemePreferences
import kz.tcloud.dcinv.data.repository.SessionRepository
import kz.tcloud.dcinv.data.scan.ScanHistory
import kz.tcloud.dcinv.ui.theme.DcInvTheme
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class SettingsUiState(
    val claims: IdTokenClaims? = null,
    val shift: SessionInfo? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val scanHistory: ScanHistory,
    private val authManager: AuthManager,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val themeMode = themePreferences.mode

    private val _state = MutableStateFlow(
        SettingsUiState(claims = parseIdTokenClaims(authManager.state.value.idToken)),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refreshShift()
    }

    fun setTheme(mode: ThemeMode) = themePreferences.setMode(mode)
    fun clearHistory() = scanHistory.clear()
    fun signOut() = authManager.signOut()

    fun refreshShift() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runShift { sessionRepository.activeSession() }
        }
    }

    fun startShift() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val shift = sessionRepository.startShift()
                _state.update { it.copy(loading = false, shift = shift) }
            } catch (e: ApiException) {
                if (e.code == "SESSION_ALREADY_ACTIVE") runShift { sessionRepository.activeSession() }
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

    fun consumeError() = _state.update { it.copy(error = null) }

    private suspend fun runShift(block: suspend () -> SessionInfo?) {
        try {
            _state.update { it.copy(loading = false, shift = block()) }
        } catch (e: ApiException) {
            _state.update { it.copy(loading = false, error = e.userMessage()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    private fun ApiException.userMessage(): String? = userMessageOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IdentityCard(
                name = state.claims?.name ?: state.claims?.username ?: "Сотрудник",
                username = state.claims?.username,
                email = state.claims?.email,
            )

            ShiftCard(
                active = state.shift != null,
                shiftId = state.shift?.id,
                startedAt = state.shift?.shiftStartAt,
                loading = state.loading,
                onStart = viewModel::startShift,
                onEnd = viewModel::endShift,
            )

            SettingsCard(title = "Оформление") {
                ThemeRow("Как в системе", ThemeMode.SYSTEM, themeMode, viewModel::setTheme)
                ThemeRow("Светлая", ThemeMode.LIGHT, themeMode, viewModel::setTheme)
                ThemeRow("Тёмная", ThemeMode.DARK, themeMode, viewModel::setTheme)
            }

            SettingsCard(title = "Данные") {
                OutlinedButton(
                    onClick = {
                        viewModel.clearHistory()
                        scope.launch { snackbarHostState.showSnackbar("История сканирований очищена") }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Очистить историю сканирований", modifier = Modifier.padding(start = 8.dp))
                }
            }

            SettingsCard(title = "О приложении") {
                AboutRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                AboutRow("Сервер", BuildConfig.API_BASE_URL.removePrefix("https://"))
                AboutRow("SSO realm", BuildConfig.KEYCLOAK_REALM)
            }

            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onSignedOut()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Выйти из аккаунта", modifier = Modifier.padding(start = 8.dp))
            }

            // Keep content clear of the floating nav island.
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun IdentityCard(name: String, username: String?, email: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, DcInvTheme.extra.border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    )
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
            username?.let { InfoRow(Icons.Filled.Badge, it) }
            email?.let { InfoRow(Icons.Filled.Email, it) }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(icon, contentDescription = null, tint = DcInvTheme.extra.secondaryText, modifier = Modifier.size(15.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = DcInvTheme.extra.secondaryText,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun ShiftCard(
    active: Boolean,
    shiftId: String?,
    startedAt: String?,
    loading: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, DcInvTheme.extra.border),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (active) Icons.Filled.CheckCircle else Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = if (active) DcInvTheme.extra.accent else DcInvTheme.extra.secondaryText,
                )
                Text(
                    text = if (active) "Смена активна" else "Смена не начата",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
            }
            if (active) {
                Text(
                    text = "#${shiftId?.take(8)} · с ${startedAt?.prettyTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = DcInvTheme.extra.secondaryText,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    active -> OutlinedButton(onClick = onEnd, shape = RoundedCornerShape(12.dp)) {
                        Text("Завершить смену")
                    }
                    else -> Button(
                        onClick = onStart,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Начать смену") }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, DcInvTheme.extra.border),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ThemeRow(label: String, mode: ThemeMode, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) },
    ) {
        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AboutRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = DcInvTheme.extra.secondaryText,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** ISO-8601 → "YYYY-MM-DD HH:MM" without java.time (minSdk 24). */
private fun String.prettyTime(): String =
    replace('T', ' ').substringBeforeLast('.').take(16)
