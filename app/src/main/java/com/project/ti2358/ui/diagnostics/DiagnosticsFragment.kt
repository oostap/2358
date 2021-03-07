package com.project.ti2358.ui.diagnostics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.project.ti2358.R
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.service.StreamingAlorService
import com.project.ti2358.data.service.StreamingTinkoffService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class DiagnosticsFragment : Fragment() {
    val depositManager: DepositManager by inject()
    val streamingTinkoffService: StreamingTinkoffService by inject()
    val streamingAlorService: StreamingAlorService by inject()

    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_diagnostics, container, false)

        textView = view.findViewById(R.id.textInfo)

        val buttonUpdate = view.findViewById<Button>(R.id.buttonUpdate)
        buttonUpdate.setOnClickListener {
            updateData()
        }


        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                delay(3000)
                updateData()
            }
        }

        updateData()
        return view
    }

    fun updateData() {
        val tinkoffREST = if (depositManager.accounts.isNotEmpty()) {
            "ОК"
        } else {
            "НЕ ОК"
        }


        val tinkoffConnectedStatus = if (streamingTinkoffService.connectedStatus) {
            "ОК"
        } else {
            "НЕ ОК"
        }

        val tinkoffMessagesStatus = if (streamingTinkoffService.messagesStatus) {
            "ОК"
        } else {
            "НЕ ОК"
        }

        val alorConnectedStatus = if (streamingAlorService.connectedStatus) {
            "ОК"
        } else {
            "НЕ ОК"
        }

        val alorMessagesStatus = if (streamingAlorService.messagesStatus) {
            "ОК"
        } else {
            "НЕ ОК"
        }

        textView.text =
                    "Tinkoff REST: $tinkoffREST\n\n" +
                    "Tinkoff OpenApi коннект: $tinkoffConnectedStatus\n\n" +
                    "Tinkoff OpenApi котировки: $tinkoffMessagesStatus\n\n" +
                    "Alor OpenApi коннект: $alorConnectedStatus\n\n" +
                    "Alor OpenApi котировки: $alorMessagesStatus\n\n"
    }
}