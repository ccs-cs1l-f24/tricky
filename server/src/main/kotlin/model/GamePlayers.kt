package model

import kotlinx.serialization.Serializable

@Serializable
data class GamePlayers(val owner: String, val players: List<String>)
