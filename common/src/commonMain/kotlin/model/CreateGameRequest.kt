package model

import kotlinx.serialization.Serializable

@Serializable
data class CreateGameRequest(val uid: String)
