package com.hayat.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.core.common.AppError
import com.hayat.core.common.AppResult
import com.hayat.core.common.ValidationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Base ViewModel: one-off messages and crash-safe launching (errors become a snackbar, never a crash). */
abstract class HayatViewModel : ViewModel() {
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    protected fun message(res: Int, vararg args: Any) { _messages.trySend(UiMessage(res, args.toList())) }
    protected fun emit(event: UiEvent) { _events.trySend(event) }

    protected fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message(R.string.error_generic) }
    }

    /** Handles a result: shows the error (if any) and returns field errors for forms. */
    protected fun <T> AppResult<T>.handle(onSuccess: (T) -> Unit = {}): List<ValidationError> = when (this) {
        is AppResult.Success -> { onSuccess(value); emptyList() }
        is AppResult.Failure -> {
            message(error.messageRes())
            (error as? AppError.Validation)?.errors.orEmpty()
        }
    }

    protected fun <T> Flow<T>.stateInVm(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}

sealed interface UiEvent {
    data object NavigateBack : UiEvent
    data class Navigate(val route: Any) : UiEvent
}

/** Maps field errors to a lookup used by forms. */
fun List<ValidationError>.forField(field: String) = firstOrNull { it.field == field }?.reason
