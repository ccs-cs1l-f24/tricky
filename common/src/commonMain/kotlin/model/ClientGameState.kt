package model

interface ClientGameState {
    data class Waiting(val players: List<String>) : ClientGameState
    data class Playing(
        val players: List<String>,
        val turn: String,
        val lastCard: PlayingCard?,
        val lastPlayer: String?,
        val hand: List<PlayingCard>,
        val rankings: List<String>
    ) : ClientGameState

    data class Finished(val rankings: List<String>) : ClientGameState
}
