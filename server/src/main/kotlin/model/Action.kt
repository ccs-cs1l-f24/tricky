package model

sealed interface Action {
    data class PlayerEntry(val gameId: String, val uid: String) : Action
    data class ChatMessage(val gameId: String, val uid: String, val message: String) : Action
}
