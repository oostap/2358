package com.project.ti2358.ui.settings

import android.os.Bundle
import androidx.preference.*
import com.project.ti2358.R
import com.project.ti2358.data.manager.AlorManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.service.StreamingAlorService
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class SettingsFragment : PreferenceFragmentCompat() {
    private val alorManager: AlorManager by inject()
    private val stockManager: StockManager by inject()
    private val streamingAlorService: StreamingAlorService by inject()

    var tazikAskPreference: SwitchPreferenceCompat? = null
    var tazikBidPreference: SwitchPreferenceCompat? = null
    var tazikMarketPreference: SwitchPreferenceCompat? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey)

        val tazikAskKey: String = getString(R.string.setting_key_tazik_buy_ask)
        tazikAskPreference = findPreference(tazikAskKey)

        val tazikMarketKey: String = getString(R.string.setting_key_tazik_buy_market)
        tazikMarketPreference = findPreference(tazikMarketKey)

        val tazikBidKey: String = getString(R.string.setting_key_tazik_buy_bid)
        tazikBidPreference = findPreference(tazikBidKey)

        val listener = Preference.OnPreferenceChangeListener { preference, newValue ->
            if (newValue as Boolean) {
                tazikMarketPreference?.isChecked = false
                tazikAskPreference?.isChecked = false
                tazikBidPreference?.isChecked = false
                true
            } else {
                if (preference.key == tazikAskKey) {
                    false
                } else {
                    tazikAskPreference?.isChecked = true
                    true
                }
            }
        }

        tazikMarketPreference?.onPreferenceChangeListener = listener
        tazikAskPreference?.onPreferenceChangeListener = listener
        tazikBidPreference?.onPreferenceChangeListener = listener

        /////////// ALOR

        val alorKey: String = getString(R.string.setting_key_market_alor)
        val alorPreference: SwitchPreferenceCompat? = findPreference(alorKey)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val isAlor = sharedPreferences.getBoolean(alorKey, false)

        alorPreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            updateAlor(newValue as Boolean)
            alorManager.refreshToken()
            if (!newValue) {
                streamingAlorService.disconnect()
            } else {
                streamingAlorService.connect()
            }
            stockManager.loadStocks(true)
            true
        }

        updateAlor(isAlor)

        val alorTokenKey: String = getString(R.string.setting_key_token_market_alor)
        val alorToken: EditTextPreference? = findPreference(alorTokenKey)
        alorToken?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            alorManager.refreshToken()
            stockManager.loadStocks(true)
            true
        }
    }

    private fun updateAlor(alor: Boolean) {
        val alorTokenKey: String = getString(R.string.setting_key_token_market_alor)
        val alorPreference: EditTextPreference? = findPreference(alorTokenKey)
        alorPreference?.isVisible = alor
    }
}