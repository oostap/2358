package com.project.ti2358.ui.telegram

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.project.ti2358.R
import com.project.ti2358.data.manager.TinkoffPortfolioManager
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.databinding.FragmentTelegramBinding
import com.project.ti2358.service.StrategyTelegramService
import com.project.ti2358.service.Utils
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class TelegramFragment : Fragment(R.layout.fragment_telegram) {
    val tinkoffPortfolioManager: TinkoffPortfolioManager by inject()
    val stockManager: StockManager by inject()

    private var fragmentTelegramBinding: FragmentTelegramBinding? = null

    override fun onDestroy() {
        fragmentTelegramBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentTelegramBinding.bind(view)
        fragmentTelegramBinding = binding

        with(binding) {
            startButton.setOnClickListener {
                if (SettingsManager.getTelegramBotApiKey() == "") {
                    Utils.showMessageAlert(requireContext(), "API KEY телеграм бота не задан")
                    return@setOnClickListener
                }

                if (Utils.isServiceRunning(requireContext(), StrategyTelegramService::class.java)) {
                    requireContext().stopService(Intent(context, StrategyTelegramService::class.java))
                } else {
                    Utils.startService(requireContext(), StrategyTelegramService::class.java)
                }
                updateServiceButtonText()
            }
            updateServiceButtonText()
        }
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyTelegramService::class.java)) {
            fragmentTelegramBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragmentTelegramBinding?.startButton?.text = getString(R.string.start_telegram)
        }
    }
}