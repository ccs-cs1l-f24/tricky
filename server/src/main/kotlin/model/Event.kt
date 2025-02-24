package model

data class Event(val game: String, val player: String? = null, val eventBroadcast: Broadcast) {
    sealed interface Broadcast {
        data class ChatMessage(val uid: String, val message: String) : Broadcast
        data class NewGameState(val state: ServerGameState) : Broadcast
        data class Error(val reason: String) : Broadcast
    }
}
