package com.project.ti2358.ui.diagnostics

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.project.ti2358.R
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.service.StreamingAlorService
import com.project.ti2358.data.service.StreamingTinkoffService
import com.project.ti2358.databinding.FragmentDiagnosticsBinding
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class DiagnosticsFragment : Fragment(R.layout.fragment_diagnostics) {
    val depositManager: DepositManager by inject()
    val stockManager: StockManager by inject()
    val streamingTinkoffService: StreamingTinkoffService by inject()
    val streamingAlorService: StreamingAlorService by inject()

    private var fragmentDiagnosticsBinding: FragmentDiagnosticsBinding? = null

    override fun onDestroy() {
        fragmentDiagnosticsBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDiagnosticsBinding.bind(view)
        fragmentDiagnosticsBinding = binding

        binding.updateButton.setOnClickListener {
            updateData()
        }
        updateData()
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
                if (it["M"]?.from700to1200 != null) daager1728Step1 = "OK"
                if (it["M"]?.from700to1600 != null) daager1728Step2 = "OK"
                if (it["M"]?.from1630to1635 != null) daager1728Step3 = "OK"
            }
        }

        fragmentDiagnosticsBinding?.textInfoView?.text =
                    "Tinkoff REST: $tinkoffREST\n" +
                    "Tinkoff OpenAPI коннект: $tinkoffConnectedStatus\n" +
                    "Tinkoff OpenAPI котировки: $tinkoffMessagesStatus\n\n" +

                    "ALOR OpenAPI коннект: $alorConnectedStatus\n" +
                    "ALOR OpenAPI котировки: $alorMessagesStatus\n\n" +

                    "daager OpenAPI цены закрытия: $daagerClosePricesStatus\n" +
                    "daager OpenAPI отчёты и дивы: $daagerReportsStatus\n" +
                    "daager OpenAPI индексы: $daagerIndicesStatus\n" +
                    "daager OpenAPI шорты: $daagerShortsStatus\n" +
                    "daager OpenAPI 1728: $daager1728\n" +
                    "daager OpenAPI 1728 Шаг 1: $daager1728Step1\n" +
                    "daager OpenAPI 1728 Шаг 2: $daager1728Step2\n" +
                    "daager OpenAPI 1728 Шаг 3: $daager1728Step3\n"
    }
}