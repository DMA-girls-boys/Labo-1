package ch.heigvd.iict.dma.labo1

import android.util.Log
import androidx.fragment.app.activityViewModels
import ch.heigvd.iict.dma.labo1.models.Message
import ch.heigvd.iict.dma.labo1.repositories.MessagesRepository
import ch.heigvd.iict.dma.labo1.ui.push.PushViewModel
import ch.heigvd.iict.dma.labo1.ui.push.PushViewModelFactory
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.*
import kotlin.concurrent.thread

class FirebaseService : FirebaseMessagingService() {
    private val repository by lazy {
        MessagesRepository((application as Labo1Application).messagesDao)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("OnToken", token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.i("OnMessage", remoteMessage.data.toString())

        if (remoteMessage.data["command"] == "clear") {
            thread {
                repository.deleteAllMessage()
            }
            return
        }

        val sentDate = Calendar.getInstance()
        sentDate.timeInMillis = remoteMessage.sentTime

        thread {
            repository.insert(Message(
                sentDate = sentDate,
                receptionDate = Calendar.getInstance(),
                message = remoteMessage.data["message"],
                command = remoteMessage.data["command"]
            ))
        }
    }
}