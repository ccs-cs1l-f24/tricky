package model

import kotlinx.serialization.Serializable

@Serializable
sealed interface ClientGameState {
    @Serializable
    data class Waiting(val players: List<String>) : ClientGameState

    @Serializable
    data class Playing(
        val players: List<String>,
        val turn: String,
        val lastCard: PlayingCard?,
        val lastPlayer: String?,
        val hand: List<PlayingCard>,
        val rankings: List<String>
    ) : ClientGameState

    @Serializable
    data class Finished(val rankings: List<String>) : ClientGameState
}
