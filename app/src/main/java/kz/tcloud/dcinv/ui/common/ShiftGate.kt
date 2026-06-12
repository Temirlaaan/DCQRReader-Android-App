package kz.tcloud.dcinv.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.tcloud.dcinv.data.network.ApiException
import kz.tcloud.dcinv.data.repository.SessionRepository
import kotlin.coroutines.cancellation.CancellationException

data class ShiftGateState(
    val required: Boolean = false,
    val starting: Boolean = false,
)

/**
 * Write endpoints answer `409 NO_ACTIVE_SHIFT` when the user has no open shift.
 * Each write ViewModel owns one gate: on that error it trips the gate with a
 * retry lambda, the UI shows [ShiftRequiredDialog], and confirming starts the
 * shift and re-runs the original action.
 */
class ShiftGate(
    private val sessionRepository: SessionRepository,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
) {
    private val _state = MutableStateFlow(ShiftGateState())
    val state: StateFlow<ShiftGateState> = _state.asStateFlow()

    private var retry: (() -> Unit)? = null

    /** Returns true (and arms the dialog) when [e] is the no-active-shift 409. */
    fun trip(e: ApiException, retryAction: () -> Unit): Boolean {
        if (e.code != "NO_ACTIVE_SHIFT") return false
        retry = retryAction
        _state.value = ShiftGateState(required = true)
        return true
    }

    fun dismiss() {
        retry = null
        _state.value = ShiftGateState()
    }

    fun startShiftAndRetry() {
        scope.launch {
            _state.update { it.copy(starting = true) }
            try {
                sessionRepository.startShift()
                val action = retry
                dismiss()
                action?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dismiss()
                val msg = (e as? ApiException)?.let {
                    it.apiError?.userMessage ?: it.apiError?.message ?: it.message
                } ?: e.message
                onError(msg ?: "Не удалось открыть смену")
            }
        }
    }
}

@Composable
fun ShiftRequiredDialog(gate: ShiftGate) {
    val state by gate.state.collectAsStateWithLifecycle()
    if (!state.required) return
    AlertDialog(
        onDismissRequest = { if (!state.starting) gate.dismiss() },
        title = { Text("Смена не открыта") },
        text = { Text("Для записи изменений нужна активная смена. Открыть смену и повторить действие?") },
        confirmButton = {
            TextButton(onClick = gate::startShiftAndRetry, enabled = !state.starting) {
                Text(if (state.starting) "Открытие…" else "Открыть смену")
            }
        },
        dismissButton = {
            TextButton(onClick = gate::dismiss, enabled = !state.starting) { Text("Отмена") }
        },
    )
}
