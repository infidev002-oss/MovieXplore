package com.movies.moviexplore.mvi

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

/**
 * Model-View-Intent store in plain Kotlin. The screen renders one immutable [S]tate, reports every
 * user action as an [I]ntent through [onIntent], and receives one-off [E]ffects (navigation, toasts)
 * that must happen once. Screens without effects use `Nothing` for [E].
 *
 * A store lives as long as its screen: the screen calls [clear] when it is destroyed.
 */
abstract class MviStore<S, I, E>(initialState: S) {

    /** Runs the store's work on the main thread; network and parsing switch to IO in the repositories. */
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    /** The single entry point for user actions. */
    abstract fun onIntent(intent: I)

    /** Produces the next state from the current one. */
    protected fun reduce(transform: S.() -> S) = _state.update(transform)

    protected fun postEffect(effect: E) {
        _effects.trySend(effect)
    }

    /** Cancels all of this store's work. */
    fun clear() = scope.cancel()
}
