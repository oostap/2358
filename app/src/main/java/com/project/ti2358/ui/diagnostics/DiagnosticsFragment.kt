package com.project.ti2358.ui.diagnostics

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.project.ti2358.R
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.service.StreamingAlorService
import com.project.ti2358.data.service.StreamingTinkoffService
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class DiagnosticsFragment : Fragment() {
    val depositManager: DepositManager by inject()
    val stockManager: StockManager by inject()
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

        val buttonUpdate = view.findViewById<Button>(R.id.button_update)
        buttonUpdate.setOnClickListener {
            updateData()
        }

        updateData()
        return view
    }

    @SuppressLint("SetTextI18n")
    fun updateData() {
        val tinkoffREST = if (depositManager.accounts.isNotEmpty()) "ОК" else "НЕ ОК 😱"
        val tinkoffConnectedStatus = if (streamingTinkoffService.connectedStatus) "ОК" else "НЕ ОК 😱"
        val tinkoffMessagesStatus = if (streamingTinkoffService.messagesStatus) "ОК" else "НЕ ОК 😱"
        val alorConnectedStatus = if (streamingAlorService.connectedStatus) "ОК" else "НЕ ОК 😱"
        val alorMessagesStatus = if (streamingAlorService.messagesStatus) "ОК" else "НЕ ОК 😱"
        val daagerClosePricesStatus = if (stockManager.stockClosePrices.isNotEmpty()) "ОК" else "НЕ ОК 😱"
        val daagerReportsStatus = if (stockManager.stockReports.isNotEmpty()) "ОК" else "НЕ ОК 😱"
        val daagerIndicesStatus = if (stockManager.indices.isNotEmpty()) "ОК" else "НЕ ОК 😱"
        val daagerShortsStatus = if (stockManager.stockShorts.isNotEmpty()) "ОК" else "НЕ ОК 😱"

        val daager1728 = if (stockManager.stockPrice1728?.isNotEmpty() == true) "ОК" else "НЕ ОК 😱"
        var daager1728Step1 = "НЕ ОК 😱"
        var daager1728Step2 = "НЕ ОК 😱"
        var daager1728Step3 = "НЕ ОК 😱"
        stockManager.stockPrice1728?.let {
            if (it["M"] != null) {
                if (it["M"]?.from700to1200 != null) {
                    daager1728Step1 = "OK"
                }

                if (it["M"]?.from700to1600 != null) {
                    daager1728Step2 = "OK"
                }

                if (it["M"]?.from1630to1635 != null) {
                    daager1728Step3 = "OK"
                }
            }
        }

        textView.text =
                    "Tinkoff REST: $tinkoffREST\n\n" +
                    "Tinkoff OpenAPI коннект: $tinkoffConnectedStatus\n\n" +
                    "Tinkoff OpenAPI котировки: $tinkoffMessagesStatus\n\n" +

                    "ALOR OpenAPI коннект: $alorConnectedStatus\n\n" +
                    "ALOR OpenAPI котировки: $alorMessagesStatus\n\n" +

                    "daager OpenAPI цены закрытия: $daagerClosePricesStatus\n\n" +
                    "daager OpenAPI отчёты и дивы: $daagerReportsStatus\n\n" +
                    "daager OpenAPI индексы: $daagerIndicesStatus\n\n" +
                    "daager OpenAPI шорты: $daagerShortsStatus\n\n" +
                    "daager OpenAPI 1728: $daager1728\n\n" +
                    "daager OpenAPI 1728 Шаг 1: $daager1728Step1\n\n" +
                    "daager OpenAPI 1728 Шаг 2: $daager1728Step2\n\n" +
                    "daager OpenAPI 1728 Шаг 3: $daager1728Step3\n\n"


    }
}