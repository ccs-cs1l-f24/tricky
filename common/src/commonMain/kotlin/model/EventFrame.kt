package model

import kotlinx.serialization.Serializable

@Serializable
sealed interface EventFrame {
    @Serializable
    data class ChatMessage(val uid: String, val message: String) : EventFrame

    @Serializable
    data class NewGameState(val state: ClientGameState) : EventFrame

    @Serializable
    data class Error(val reason: String) : EventFrame
}
