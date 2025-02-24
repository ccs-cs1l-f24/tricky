package model

import kotlinx.serialization.Serializable

@Serializable
sealed interface PlayingCard {
    companion object {
        val ranks = listOf("3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2")
    }

    enum class Suit {
        CLUBS, DIAMONDS, HEARTS, SPADES
    }

    @Serializable
    data class Normal(val rank: String, val suit: Suit) : PlayingCard

    @Serializable
    data class Joker(val red: Boolean) : PlayingCard
}

infix fun PlayingCard.beats(other: PlayingCard?) = when (this) {
    is PlayingCard.Joker -> if (other is PlayingCard.Joker) red && !other.red else true
    is PlayingCard.Normal -> when (other) {
        null -> true
        is PlayingCard.Joker -> false
        is PlayingCard.Normal -> PlayingCard.ranks.indexOf(rank) > PlayingCard.ranks.indexOf(other.rank)
    }
}
