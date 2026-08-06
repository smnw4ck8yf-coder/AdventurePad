package com.jamesmoran.adventurepad

/** Pure state guard that keeps Messenger binding requests single-flight. */
internal class BindingRequestTracker {
    var bindingDesired: Boolean = false
        private set
    var bindingRequested: Boolean = false
        private set
    var reconnectAttemptCount: Int = 0
        private set

    fun start(): Boolean {
        if (bindingDesired) return false
        bindingDesired = true
        bindingRequested = false
        reconnectAttemptCount = 0
        return true
    }

    fun canRequestBinding(): Boolean = bindingDesired && !bindingRequested

    fun recordRequestResult(accepted: Boolean) {
        check(canRequestBinding()) { "A binding result requires an active request opportunity" }
        bindingRequested = accepted
    }

    fun beginReconnectAttempt(): Boolean {
        if (!canRequestBinding()) return false
        reconnectAttemptCount++
        return true
    }

    fun discardBinding() {
        bindingRequested = false
    }

    fun stop() {
        bindingDesired = false
        bindingRequested = false
    }
}
