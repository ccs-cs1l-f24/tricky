package model

sealed interface Action {
    data class PlayerEnter(val gameId: String, val uid: String) : Action
    data class PlayerLeave(val gameId: String, val uid: String) : Action
    data class ChatMessage(val gameId: String, val uid: String, val message: String) : Action
    data class StartGame(val gameId: String, val uid: String) : Action
    data class PlayCard(val gameId: String, val uid: String, val card: PlayingCard) : Action
    data class PassTurn(val gameId: String, val uid: String) : Action
}
