import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.CanvasBasedWindow

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "app") {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Text("hello")
            }
        }
    }
}
