package model

import kotlinx.serialization.Serializable

@Serializable
sealed interface ServerGameState {
    @Serializable
    data class Waiting(val players: List<String>) : ServerGameState

    @Serializable
    data class Playing(
        val players: List<String>,
        val turn: String,
        val lastCard: PlayingCard?,
        val lastPlayer: String?,
        val hands: Map<String, List<PlayingCard>>,
        val rankings: List<String>
    ) : ServerGameState

    @Serializable
    data class Finished(val rankings: List<String>) : ServerGameState
}

fun ServerGameState.toClientGameState(player: String) = when (this) {
    is ServerGameState.Waiting -> ClientGameState.Waiting(players)
    is ServerGameState.Playing -> ClientGameState.Playing(
        players, turn, lastCard, lastPlayer, hands[player] ?: emptyList(), rankings
    )

    is ServerGameState.Finished -> ClientGameState.Finished(rankings)
}
