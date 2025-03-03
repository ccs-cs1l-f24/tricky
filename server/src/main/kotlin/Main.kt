import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Action
import model.Action.*
import model.ActionFrame
import model.CreateGameResponse
import model.EnterGameRequest
import model.Event
import model.GameListEntry
import model.ServerGameState
import org.slf4j.event.Level
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val actionFlow = MutableSharedFlow<Action>()
    val eventFlow = actionFlow.transform<Action, Event> { processInput(it) }
        .shareIn(CoroutineScope(coroutineContext), started = SharingStarted.Eagerly)
    embeddedServer(Netty, 8080) {
        install(CallLogging) { level = Level.INFO }
        install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json) }
        install(ContentNegotiation) { json() }
        routing {
            post("/create-game") {
                val gameId = Uuid.random().toString()
                val gamesFile = File("serverData/games.jsonl").also { it.parentFile.mkdirs() }
                gamesFile.appendText(Json.encodeToString(GameListEntry(gameId = gameId)) + "\n")
                val gameDir = File("serverData/$gameId").also { it.mkdirs() }
                File(gameDir, "gameState.json").writeText(
                    Json.encodeToString(ServerGameState.Waiting(players = emptyList()) as ServerGameState)
                )
                call.respond(CreateGameResponse(gameId = gameId))
            }
            get("/game/{id}") {
                call.respondFile(File("./clientAssets/index.html"))
            }
            webSocket("/game/{id}") {
                val gameId = call.parameters["id"]!!
                val gamesFile = File("serverData/games.jsonl").also { it.parentFile.mkdirs() }
                if (gamesFile.readLines().none { Json.decodeFromString<GameListEntry>(it).gameId == gameId }) close(
                    CloseReason(CloseReason.Codes.INTERNAL_ERROR, "game $gameId not found")
                )
                val enterRequest = receiveDeserialized<EnterGameRequest>()
                actionFlow.emit(PlayerEnter(gameId = gameId, uid = enterRequest.uid))
                CoroutineScope(coroutineContext).launch {
                    try {
                        while (true) when (val action = receiveDeserialized<ActionFrame>()) {
                            is ActionFrame.ChatMessage -> actionFlow.emit(
                                ChatMessage(
                                    gameId = gameId, uid = enterRequest.uid, message = action.message
                                )
                            )

                            ActionFrame.StartGame -> actionFlow.emit(
                                StartGame(gameId = gameId, uid = enterRequest.uid)
                            )

                            is ActionFrame.PlayCard -> actionFlow.emit(
                                PlayCard(gameId = gameId, uid = enterRequest.uid, card = action.card)
                            )

                            ActionFrame.PassTurn -> actionFlow.emit(
                                PassTurn(gameId = gameId, uid = enterRequest.uid)
                            )
                        }
                    } catch (_: ClosedReceiveChannelException) {
                        actionFlow.emit(PlayerLeave(gameId = gameId, uid = enterRequest.uid))
                        return@launch
                    } catch (_: Throwable) {
                        actionFlow.emit(PlayerLeave(gameId = gameId, uid = enterRequest.uid))
                        return@launch
                    }
                }
                eventFlow.transform { processBroadcast(it, gameId = gameId, uid = enterRequest.uid) }
                    .collect { eventFrame -> sendSerialized(eventFrame) }
            }
            staticFiles("/", File("clientAssets"))
        }
    }.start(wait = false)
}
