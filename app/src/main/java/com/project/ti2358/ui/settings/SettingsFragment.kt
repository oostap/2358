package com.project.ti2358.ui.settings

import android.os.Bundle
import androidx.preference.*
import com.project.ti2358.R

class SettingsFragment : PreferenceFragmentCompat() {

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
    }
}