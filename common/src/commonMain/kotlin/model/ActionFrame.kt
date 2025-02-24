package model

import kotlinx.serialization.Serializable

@Serializable
sealed interface ActionFrame {
    @Serializable
    data class ChatMessage(val message: String) : ActionFrame

    @Serializable
    data object StartGame : ActionFrame

    @Serializable
    data class PlayCard(val card: PlayingCard) : ActionFrame

    @Serializable
    data object PassTurn : ActionFrame
}
