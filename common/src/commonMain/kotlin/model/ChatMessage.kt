package model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(val uid: String, val message: String)
