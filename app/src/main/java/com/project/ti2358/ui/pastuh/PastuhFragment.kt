package com.project.ti2358.ui.pastuh

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.project.ti2358.R
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.databinding.FragmentPastuhBinding
import com.project.ti2358.service.StrategyFollowerService
import com.project.ti2358.service.StrategyTelegramService
import com.project.ti2358.service.Utils
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class PastuhFragment : Fragment(R.layout.fragment_pastuh) {
    val depositManager: DepositManager by inject()
    val stockManager: StockManager by inject()

    private var fragmentPastuhBinding: FragmentPastuhBinding? = null

    override fun onDestroy() {
        fragmentPastuhBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPastuhBinding.bind(view)
        fragmentPastuhBinding = binding

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

            startFollowerButton.setOnClickListener {
                if (SettingsManager.getTelegramBotApiKey() == "") {
                    Utils.showMessageAlert(requireContext(), "API KEY телеграм бота не задан")
                    return@setOnClickListener
                }

                if (Utils.isServiceRunning(requireContext(), StrategyFollowerService::class.java)) {
                    requireContext().stopService(Intent(context, StrategyFollowerService::class.java))
                } else {
                    Utils.startService(requireContext(), StrategyFollowerService::class.java)
                }
                updateServiceButtonText()
            }
        }
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyTelegramService::class.java)) {
            fragmentPastuhBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragmentPastuhBinding?.startButton?.text = getString(R.string.start_telegram)
        }

        if (Utils.isServiceRunning(requireContext(), StrategyFollowerService::class.java)) {
            fragmentPastuhBinding?.startFollowerButton?.text = getString(R.string.stop)
        } else {
            fragmentPastuhBinding?.startFollowerButton?.text = getString(R.string.start_follower)
        }
    }
}