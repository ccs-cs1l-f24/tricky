import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Action
import model.Event
import model.PlayingCard
import model.ServerGameState
import model.beats
import java.io.File

suspend fun FlowCollector<Event>.processInput(action: Action) {
    when (action) {
        is Action.PlayerEnter -> {
            val gameStateFile = File(File("serverData/${action.gameId}").also { it.mkdirs() }, "gameState.json")
            if (!gameStateFile.exists()) return emit(
                Event(
                    game = action.gameId, player = action.uid, eventBroadcast = Event.Broadcast.Error("internal error")
                )
            )
            emit(
                Event(
                    game = action.gameId,
                    player = action.uid,
                    eventBroadcast = Event.Broadcast.NewGameState(Json.decodeFromString(gameStateFile.readText()))
                )
            )
        }

        is Action.PlayerLeave -> {
            val gameStateFile = File(File("serverData/${action.gameId}").also { it.mkdirs() }, "gameState.json")
            val gameState: ServerGameState =
                if (gameStateFile.exists()) Json.decodeFromString(gameStateFile.readText()) else return
            if (gameState is ServerGameState.Waiting && action.uid in gameState.players) {
                val newGameState = gameState.copy(players = gameState.players - action.uid)
                gameStateFile.writeText(Json.encodeToString(newGameState))
                emit(Event(game = action.gameId, eventBroadcast = Event.Broadcast.NewGameState(newGameState)))
            }
        }

        is Action.ChatMessage -> {
            emit(
                Event(
                    game = action.gameId,
                    eventBroadcast = Event.Broadcast.ChatMessage(uid = action.uid, message = action.message)
                )
            )
        }

        is Action.StartGame -> {
            suspend fun emitError(reason: String) =
                emit(Event(game = action.gameId, player = action.uid, eventBroadcast = Event.Broadcast.Error(reason)))

            val gameStateFile = File(File("serverData/${action.gameId}").also { it.mkdirs() }, "gameState.json")
            if (!gameStateFile.exists()) return emitError("internal error")
            val gameState: ServerGameState = Json.decodeFromString(gameStateFile.readText())
            if (gameState !is ServerGameState.Waiting) return emitError("game has already been started")
            if (gameState.players.size < 2) return emitError("not enough players")
            if (gameState.players.size > 54) return emitError("too many players")
            val deck = PlayingCard.ranks.map { rank ->
                PlayingCard.Suit.entries.map { suit -> PlayingCard.Normal(rank, suit) }
            }.flatten() + PlayingCard.Joker(false) + PlayingCard.Joker(true)
            val hands = deck.shuffled().mapIndexed { index, card -> index to card }
                .groupBy { (index, _) -> gameState.players[index % gameState.players.size] }
                .mapValues { (_, values) -> values.map { it.second } }
            val initialGameState = ServerGameState.Playing(
                players = gameState.players,
                turn = gameState.players.first(),
                lastCard = null,
                lastPlayer = null,
                hands = hands,
                rankings = emptyList()
            )
            gameStateFile.writeText(Json.encodeToString(initialGameState))
            emit(Event(game = action.gameId, eventBroadcast = Event.Broadcast.NewGameState(initialGameState)))
        }

        is Action.PlayCard -> {
            suspend fun emitError(reason: String) =
                emit(Event(game = action.gameId, player = action.uid, eventBroadcast = Event.Broadcast.Error(reason)))

            val gameStateFile = File(File("serverData/${action.gameId}").also { it.mkdirs() }, "gameState.json")
            if (!gameStateFile.exists()) return emitError("internal error")
            val gameState: ServerGameState = Json.decodeFromString(gameStateFile.readText())
            if (gameState !is ServerGameState.Playing) return emitError("game is not in progress")
            if (gameState.turn != action.uid) return emitError("not your turn")
            if (action.card !in (gameState.hands.getOrElse(action.uid) { emptyList() })) return emitError("you don't have that card")
            if (gameState.lastPlayer != action.uid && !(action.card beats gameState.lastCard)) return emitError("must beat previous card")
            if (gameState.hands.getOrElse(action.uid) { emptyList() }.minus(action.card).isEmpty()) {
                if (gameState.hands.count { (player, hand) -> player != action.uid && hand.isNotEmpty() } <= 1) {
                    val newGameState = ServerGameState.Finished(
                        rankings = (gameState.rankings + action.uid) + gameState.hands.entries.first { (player, hand) -> player != action.uid && hand.isNotEmpty() }.key
                    )
                    gameStateFile.writeText(Json.encodeToString(newGameState))
                    return emit(
                        Event(game = action.gameId, eventBroadcast = Event.Broadcast.NewGameState(newGameState))
                    )
                }
            }
            emit(
                Event(
                    game = action.gameId, eventBroadcast = Event.Broadcast.NewGameState(
                        gameState.copy(
                            turn = gameState.players.subList(
                                gameState.players.indexOf(action.uid) + 1, gameState.players.size
                            ).firstOrNull { player -> gameState.hands[player]?.isNotEmpty() == true }
                                ?: gameState.players.first { player -> gameState.hands[player]?.isNotEmpty() == true },
                            lastCard = action.card,
                            lastPlayer = action.uid,
                            hands = gameState.hands.toMutableMap().also {
                                it[action.uid] = it[action.uid]?.minus(action.card) ?: emptyList()
                            },
                            rankings = if (gameState.hands[action.uid]?.isEmpty() == true) gameState.rankings + action.uid else gameState.rankings
                        ).also { gameStateFile.writeText(Json.encodeToString(it)) })
                )
            )
        }

        is Action.PassTurn -> {
            suspend fun emitError(reason: String) =
                emit(Event(game = action.gameId, player = action.uid, eventBroadcast = Event.Broadcast.Error(reason)))

            val gameStateFile = File(File("serverData/${action.gameId}").also { it.mkdirs() }, "gameState.json")
            if (!gameStateFile.exists()) return emitError("internal error")
            val gameState: ServerGameState = Json.decodeFromString(gameStateFile.readText())
            if (gameState !is ServerGameState.Playing) return emitError("game is not in progress")
            if (gameState.turn != action.uid) return emitError("not your turn")
            if (gameState.lastPlayer == null) return emitError("nothing played yet, play something")
            if (gameState.lastPlayer == action.uid) return emitError("everyone else passed, play something")
            emit(
                Event(
                    game = action.gameId,
                    eventBroadcast = Event.Broadcast.NewGameState(
                        gameState.copy(
                            turn = gameState.players.subList(
                                gameState.players.indexOf(action.uid) + 1, gameState.players.size
                            ).firstOrNull { player -> gameState.hands[player]?.isNotEmpty() == true }
                                ?: gameState.players.first { player -> gameState.hands[player]?.isNotEmpty() == true })
                            .also { gameStateFile.writeText(Json.encodeToString(it)) })
                )
            )
        }
    }
}
