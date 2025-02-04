package model

import kotlinx.serialization.Serializable

@Serializable
data class EnterGameRequest(val uid: String)
