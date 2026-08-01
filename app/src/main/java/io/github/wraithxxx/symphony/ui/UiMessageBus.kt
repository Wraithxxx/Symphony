package io.github.wraithxxx.symphony.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class UiMessageBus {
    private val messageChannel = Channel<String>(
        capacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val messages = messageChannel.receiveAsFlow()

    fun show(message: String) {
        messageChannel.trySend(message)
    }
}
