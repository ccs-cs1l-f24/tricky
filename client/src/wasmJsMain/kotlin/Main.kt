import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.CanvasBasedWindow
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import model.ActionFrame
import model.ClientGameState
import model.CreateGameResponse
import model.EnterGameRequest
import model.EventFrame
import model.PlayingCard
import org.w3c.dom.events.Event
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalComposeUiApi::class, ExperimentalUuidApi::class)
fun main() {
    val httpClient = HttpClient(Js) {
        install(ContentNegotiation) { json() }
        install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json) }
    }
    CanvasBasedWindow(canvasElementId = "app") {
        val coroutineScope = rememberCoroutineScope()
        var uid: String? by remember { mutableStateOf(null) }
        var currentPage: Page? by remember { mutableStateOf(null) }
        val router = object : Router {
            override val page get() = currentPage

            override fun navigateTo(page: Page) {
                val newPathName = when (page) {
                    Page.Home -> "/"
                    is Page.Game -> "/game/${page.id}"
                }
                window.history.pushState(
                    "".toJsString(), "", (window.location.origin + newPathName).removeSuffix("/")
                )
                currentPage = page
            }
        }

        fun setPathname() {
            currentPage = when {
                window.location.pathname == "/" -> Page.Home
                window.location.pathname.startsWith("/game") -> window.location.pathname.removePrefix("/game")
                    .removePrefix("/").takeIf { it.isNotBlank() }?.let { Page.Game(it) }

                else -> null
            }
        }
        LaunchedEffect(true) {
            val uidStorageKey = "tricky.uid"
            uid = localStorage.getItem(uidStorageKey) ?: Uuid.random().toString()
                .also { localStorage.setItem(uidStorageKey, it) }
            setPathname()
        }
        DisposableEffect(true) {
            val listener = { _: Event? -> setPathname() }
            window.addEventListener("popstate", listener)
            onDispose { window.removeEventListener("popstate", listener) }
        }
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box {
                    when (val page = currentPage) {
                        null -> {}
                        Page.Home -> {
                            Button(onClick = {
                                coroutineScope.launch {
                                    uid?.let {
                                        val response = httpClient.post("/create-game")
                                        router.navigateTo(Page.Game(response.body<CreateGameResponse>().gameId))
                                    }
                                }
                            }, modifier = Modifier.padding(24.dp)) {
                                Text("Create game")
                            }
                        }

                        is Page.Game -> {
                            val actionFlow = remember { MutableSharedFlow<ActionFrame>() }
                            val events = remember { mutableStateListOf<EventFrame>() }
                            var gameState: ClientGameState? by remember { mutableStateOf(null) }
                            LaunchedEffect(uid) {
                                uid?.let {
                                    httpClient.webSocket(path = "/game/${page.id}") {
                                        sendSerialized(EnterGameRequest(uid = it))
                                        launch { actionFlow.collect { sendSerialized(it) } }
                                        while (true) {
                                            val event = receiveDeserialized<EventFrame>()
                                            events.add(event)
                                            if (event is EventFrame.NewGameState) gameState = event.state
                                        }
                                    }
                                }
                            }
                            Row {
                                Chat(page, uid, coroutineScope, actionFlow, events)
                                GameDisplay(
                                    uid,
                                    gameState,
                                    onStartGame = {
                                        coroutineScope.launch { actionFlow.emit(ActionFrame.StartGame) }
                                    },
                                    onPassTurn = { coroutineScope.launch { actionFlow.emit(ActionFrame.PassTurn) } },
                                    onPlayCard = { coroutineScope.launch { actionFlow.emit(ActionFrame.PlayCard(it)) } })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chat(
    page: Page.Game,
    uid: String?,
    coroutineScope: CoroutineScope,
    actionFlow: MutableSharedFlow<ActionFrame>,
    events: SnapshotStateList<EventFrame>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("Game: ${page.id}")
        Text("User: $uid")
        var text by remember { mutableStateOf("") }
        OutlinedTextField(text, { text = it }, label = { Text("Chat") }, trailingIcon = {
            TextButton(onClick = {
                coroutineScope.launch {
                    actionFlow.emit(ActionFrame.ChatMessage(text))
                    text = ""
                }
            }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) { Text("Send") }
        })
        for (event in events) when (event) {
            is EventFrame.ChatMessage -> Text("${event.uid}: ${event.message}")
            is EventFrame.Error -> Text(
                "error: ${event.reason}", color = MaterialTheme.colorScheme.error
            )

            is EventFrame.NewGameState -> Text(
                "game state updated", fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
private fun GameDisplay(
    uid: String?,
    gameState: ClientGameState?,
    onStartGame: () -> Unit,
    onPlayCard: (PlayingCard) -> Unit,
    onPassTurn: () -> Unit
) {
    when (gameState) {
        null -> {
            Text("Loading")
        }

        is ClientGameState.Waiting -> {
            Column {
                Text("Waiting for game start")
                Button(onClick = onStartGame) {
                    Text("Start game")
                }
                Text("Players:")
                gameState.players.forEach { Text(it) }
            }
        }

        is ClientGameState.Playing -> {
            Row {
                Column {
                    Text("Players:")
                    gameState.players.forEach {
                        Text(
                            it,
                            fontStyle = if (it in gameState.rankings) FontStyle.Italic else FontStyle.Normal,
                            fontWeight = if (gameState.turn == it) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Column {
                    Text("Last card played:")
                    gameState.lastCard?.let { Text(it.toString()) }
                    gameState.lastPlayer?.let { Text(it.toString()) }
                }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Your hand:")
                    gameState.hand.forEach {
                        TextButton(
                            onClick = { onPlayCard(it) }, enabled = gameState.turn == uid
                        ) { Text(it.toString()) }
                    }
                    OutlinedButton(onClick = onPassTurn, enabled = gameState.turn == uid) { Text("Pass turn") }
                }
            }
        }

        is ClientGameState.Finished -> {
            Column {
                Text("Game finished")
                gameState.rankings.forEachIndexed { index, player -> Text("${index + 1}. $player") }
            }
        }
    }
}
