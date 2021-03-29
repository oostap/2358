package com.project.ti2358.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import com.project.ti2358.R
import com.project.ti2358.data.manager.AlorManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.service.StreamingAlorService
import com.project.ti2358.data.service.StreamingTinkoffService
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class SettingsFragment : PreferenceFragmentCompat() {
    private val alorManager: AlorManager by inject()
    private val stockManager: StockManager by inject()
    private val streamingAlorService: StreamingAlorService by inject()
    private val streamingTinkoffService: StreamingTinkoffService by inject()

    var tazikAskPreference: SwitchPreferenceCompat? = null
    var tazikBidPreference: SwitchPreferenceCompat? = null
    var tazikMarketPreference: SwitchPreferenceCompat? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey)

        ////////////////////////////////////////////////////////////////////////////////////////////////////////
        val darkThemeKey = getString(R.string.setting_key_dark_theme)
        val darkThemePreference: SwitchPreferenceCompat? = findPreference(darkThemeKey)

        when (context?.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> {
                darkThemePreference?.isChecked = true
            }
            Configuration.UI_MODE_NIGHT_NO -> {
                darkThemePreference?.isChecked = false
            }
            Configuration.UI_MODE_NIGHT_UNDEFINED -> {}
        }

        darkThemePreference?.onPreferenceChangeListener  = Preference.OnPreferenceChangeListener { preference, newValue ->
            if (newValue as Boolean) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            true
        }
        ////////////////////////////////////////////////////////////////////////////////////////////////////////

        val tazikAskKey = getString(R.string.setting_key_tazik_buy_ask)
        tazikAskPreference = findPreference(tazikAskKey)

        var key = getString(R.string.setting_key_tazik_buy_market)
        tazikMarketPreference = findPreference(key)

        key = getString(R.string.setting_key_tazik_buy_bid)
        tazikBidPreference = findPreference(key)

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
            streamingTinkoffService.disconnect()
            streamingTinkoffService.connect()

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
        alorToken?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
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