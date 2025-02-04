package model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageRequest(val message: String)
