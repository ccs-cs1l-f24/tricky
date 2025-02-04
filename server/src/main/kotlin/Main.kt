import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Action
import model.ChatMessage
import model.ChatMessageRequest
import model.CreateGameRequest
import model.CreateGameResponse
import model.EnterGameRequest
import model.GameListEntry
import model.GamePlayers
import org.slf4j.event.Level
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val actionFlow = MutableSharedFlow<Action>()
    embeddedServer(Netty, 8080) {
        install(CallLogging) { level = Level.INFO }
        install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json) }
        install(ContentNegotiation) { json() }
        routing {
            post("/create-game") {
                val uid = call.receive<CreateGameRequest>().uid
                val gameId = Uuid.random().toString()
                val gamesFile = File("serverData/games.jsonl").also { it.parentFile.mkdirs() }
                gamesFile.appendText(Json.encodeToString(GameListEntry(gameId = gameId)) + "\n")
                val gameDir = File("serverData/$gameId").also { it.mkdirs() }
                File(gameDir, "players.json").writeText(
                    Json.encodeToString(GamePlayers(owner = uid, players = listOf(uid)))
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
                actionFlow.emit(Action.PlayerEntry(gameId = gameId, uid = enterRequest.uid))
                val scope = CoroutineScope(coroutineContext)
                scope.launch {
                    while (true) {
                        val message = receiveDeserialized<ChatMessageRequest>().message
                        actionFlow.emit(Action.ChatMessage(gameId = gameId, uid = enterRequest.uid, message = message))
                    }
                }
                actionFlow.collect {
                    if (it is Action.ChatMessage && it.gameId == gameId) {
                        sendSerialized(ChatMessage(uid = it.uid, message = it.message))
                    }
                }
            }
            staticFiles("/", File("clientAssets"))
        }
    }.start(wait = false)
    launch {
        actionFlow.collect {
            if (it is Action.PlayerEntry) {
                File(File("serverData/${it.gameId}").also { d -> d.mkdirs() }, "players.json").let { file ->
                    val players = Json.decodeFromString<GamePlayers>(file.readText())
                    val added = players.copy(owner = players.owner, players = players.players + it.uid)
                    file.writeText(Json.encodeToString(added))
                }
            }
        }
    }
}
