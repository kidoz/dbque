package su.kidoz.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mu.KotlinLogging

/**
 * Base ViewModel for MVI architecture pattern.
 * Manages state, events, and effects for a feature.
 *
 * @param S The state type that extends [UiState]
 * @param E The event type that extends [UiEvent]
 * @param F The effect type that extends [UiEffect]
 * @param initialState The initial state of the ViewModel
 */
abstract class MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(
    initialState: S,
) {
    protected val logger = KotlinLogging.logger {}

    protected val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effect = Channel<F>(Channel.BUFFERED)
    val effect: Flow<F> = _effect.receiveAsFlow()

    /**
     * Current state value for quick access.
     */
    protected val currentState: S
        get() = _state.value

    /**
     * Updates the state using a reducer function.
     *
     * @param reducer A function that takes the current state and returns a new state
     */
    protected fun updateState(reducer: S.() -> S) {
        _state.update { it.reducer() }
    }

    /**
     * Sends a one-time effect to the UI.
     *
     * @param effect The effect to send
     */
    protected fun sendEffect(effect: F) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }

    /**
     * Handles incoming UI events.
     * Subclasses must implement this to process events and update state accordingly.
     *
     * @param event The event to handle
     */
    abstract fun onEvent(event: E)

    /**
     * Called when the ViewModel is no longer needed.
     * Cancels all coroutines and cleans up resources.
     */
    open fun onCleared() {
        viewModelScope.cancel()
    }
}
