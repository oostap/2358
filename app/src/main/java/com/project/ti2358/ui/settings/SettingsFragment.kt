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
import com.project.ti2358.service.Utils
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class SettingsFragment : PreferenceFragmentCompat() {
    private val alorManager: AlorManager by inject()
    private val stockManager: StockManager by inject()

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
        /////////// ALOR

        val alorQoutesKey: String = getString(R.string.setting_key_alor_quotes)
        val alorQoutesPreference: SwitchPreferenceCompat? = findPreference(alorQoutesKey)

        val alorOrderbookKey: String = getString(R.string.setting_key_alor_orderbook)
        val alorOrderbookPreference: SwitchPreferenceCompat? = findPreference(alorOrderbookKey)

        val alorListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            Utils.showToastAlert("Необходим перезапуск!")
            true
        }

        alorQoutesPreference?.onPreferenceChangeListener = alorListener
        alorOrderbookPreference?.onPreferenceChangeListener = alorListener

        val alorTokenKey: String = getString(R.string.setting_key_token_market_alor)
        val alorToken: EditTextPreference? = findPreference(alorTokenKey)
        alorToken?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            alorManager.refreshToken()
            stockManager.loadStocks(true)
            true
        }
    }
}