package model

import kotlinx.serialization.Serializable

@Serializable
data class CreateGameResponse(val gameId: String)
