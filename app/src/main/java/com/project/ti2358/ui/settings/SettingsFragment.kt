package com.project.ti2358.ui.settings

import android.os.Bundle
import androidx.preference.*
import com.project.ti2358.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey)

        val sandboxKey: String = getString(R.string.setting_key_sandbox)
        val sandboxPreference: SwitchPreferenceCompat? = findPreference(sandboxKey)

        sandboxPreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            updateSandbox(newValue as Boolean)
            true
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val isSandbox = sharedPreferences.getBoolean(sandboxKey, true)

        updateSandbox(isSandbox)
    }

    private fun updateSandbox(sandbox: Boolean) {
        val sandboxTokenKey: String = getString(R.string.setting_key_token_sandbox)
        val marketTokenKey: String = getString(R.string.setting_key_token_market)

        val marketPreference: EditTextPreference? = findPreference(marketTokenKey)
        val sandboxPreference: EditTextPreference? = findPreference(sandboxTokenKey)

        marketPreference?.isVisible = !sandbox
        sandboxPreference?.isVisible = sandbox
    }
}