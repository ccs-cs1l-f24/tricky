import kotlinx.coroutines.flow.FlowCollector
import model.Event
import model.EventFrame
import model.toClientGameState

suspend fun FlowCollector<EventFrame>.processBroadcast(event: Event, gameId: String, uid: String) {
    if (event.game == gameId) {
        if ((event.player ?: uid) == uid) emit(
            when (val eventData = event.eventBroadcast) {
                is Event.Broadcast.ChatMessage -> EventFrame.ChatMessage(
                    uid = eventData.uid, message = eventData.message
                )

                is Event.Broadcast.NewGameState -> EventFrame.NewGameState(eventData.state.toClientGameState(player = uid))
                is Event.Broadcast.Error -> EventFrame.Error(eventData.reason)
            }
        )
    }
}
