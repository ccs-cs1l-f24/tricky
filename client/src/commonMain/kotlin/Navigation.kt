sealed interface Page {
    data object Home : Page
    data class Game(val id: String) : Page
}

interface Router {
    val page: Page?
    fun navigateTo(page: Page)
}
