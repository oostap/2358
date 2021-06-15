package com.project.ti2358.ui.chat

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.bassaer.chatmessageview.model.ChatUser
import com.github.bassaer.chatmessageview.model.Message
import com.google.gson.internal.LinkedTreeMap
import com.project.ti2358.R
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.service.ThirdPartyService
import com.project.ti2358.databinding.FragmentChatBinding
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import kotlin.collections.ArrayList

@KoinApiExtension
class ChatFragment : Fragment(R.layout.fragment_chat) {
    private val thirdPartyService: ThirdPartyService by inject()
    private val stockManager: StockManager by inject()

    private var fragmentChatBinding: FragmentChatBinding? = null

    override fun onDestroy() {
        fragmentChatBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentChatBinding.bind(view)
        fragmentChatBinding = binding

        with(binding) {
            val myId = 0
            val myIcon = BitmapFactory.decodeResource(resources, R.drawable.face_2)
            val myName = "Я"

            val me = ChatUser(myId, myName, myIcon)

            chatView.setLeftBubbleColor(Color.WHITE)
            chatView.setLeftMessageTextColor(Color.BLACK)

            chatView.setRightBubbleColor(ContextCompat.getColor(requireContext(), R.color.teal_200))
            chatView.setRightMessageTextColor(ContextCompat.getColor(requireContext(), R.color.main_bg))

            chatView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.main_bg))
            chatView.setSendButtonColor(ContextCompat.getColor(requireContext(), R.color.main_bg))

            chatView.setUsernameTextColor(Color.WHITE)
            chatView.setSendTimeTextColor(Color.WHITE)
            chatView.setDateSeparatorColor(Color.WHITE)
            chatView.setInputTextHint("сообщение...")
            chatView.setMessageMarginTop(20)
            chatView.setMessageMarginBottom(20)

            chatView.setMessageFontSize(35f)
            chatView.setDateSeparatorFontSize(30f)
            chatView.setUsernameFontSize(30f)
            chatView.setTimeLabelFontSize(30f)

            chatView.inputTextColor = Color.BLACK

            chatView.setOnClickSendButtonListener {
                val message: Message = Message.Builder()
                    .setUser(me)
                    .setRight(true)
                    .setText(chatView.inputText)
                    .hideIcon(true)
                    .setDateCell(false)
                    .setUsernameVisibility(false)
                    .build()
                chatView.send(message)
                sendMessage(chatView.inputText)
                chatView.inputText = ""
            }
            sendMessage("")
        }
    }

    private fun sendMessage(myText: String) {
        val yourId = 1
        val yourIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_hamster)
        val yourName = "Пульсянин"
        val you = ChatUser(yourId, yourName, yourIcon)
        GlobalScope.launch(Dispatchers.Main) {
            val phrase = stockManager.getPulsePhrase()

            val receivedMessage: Message = Message.Builder()
                .setUser(you)
                .setRight(false)
                .hideIcon(false)
                .setText(phrase)
                .setDateCell(false)
                .build()

            fragmentChatBinding?.chatView?.receive(receivedMessage)
        }
    }
}