import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.CanvasBasedWindow
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import model.CreateGameRequest
import model.CreateGameResponse
import org.w3c.dom.events.Event
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalComposeUiApi::class, ExperimentalUuidApi::class, DelicateCoroutinesApi::class)
fun main() {
    val httpClient = HttpClient(Js) {
        install(ContentNegotiation) { json() }
        install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json) }
    }
    CanvasBasedWindow(canvasElementId = "app") {
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
                                GlobalScope.launch {
                                    uid?.let {
                                        val response = httpClient.post("/create-game") {
                                            contentType(ContentType.Application.Json)
                                            setBody(CreateGameRequest(uid = it))
                                        }
                                        router.navigateTo(Page.Game(response.body<CreateGameResponse>().gameId))
                                    }
                                }
                            }, modifier = Modifier.padding(24.dp)) {
                                Text("Create game")
                            }
                        }

                        is Page.Game -> {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("Game: ${page.id}")
                                Text("User: $uid")
                            }
                        }
                    }
                }
            }
        }
    }
}
