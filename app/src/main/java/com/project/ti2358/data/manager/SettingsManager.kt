package com.project.ti2358.data.service

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.data.model.dto.Currency
import java.lang.Integer.parseInt

class SettingsManager {

    companion object {
        lateinit var context : Context
        lateinit var preferences : SharedPreferences

        fun setSettingsContext(applicationContext : Context) {
            context = applicationContext
            preferences = PreferenceManager.getDefaultSharedPreferences(context)
        }

        public fun isSandbox() : Boolean {
            val sandboxKey: String = context.getString(R.string.setting_key_sandbox)
            return preferences.getBoolean(sandboxKey, true)
        }

        fun getActiveToken() : String {
            val tokenKey : String
            if (isSandbox()) {
                tokenKey = context.getString(R.string.setting_key_token_sandbox)
            } else {
                tokenKey = context.getString(R.string.setting_key_token_market)
            }

            val token : String? = preferences.getString(tokenKey, "")
            return token ?: "TODO"
        }

        fun getActiveBaseUrl() : String {
            return if (isSandbox()) {
                "https://api-invest.tinkoff.ru/openapi/sandbox/"
            } else {
                "https://api-invest.tinkoff.ru/openapi/"
            }
        }

        fun isAllowCurrency(currency : Currency) : Boolean {
//            return true;
            if (currency == Currency.USD) return true
            return false
        }

        /******************** common *************************/

        fun getCommonPriceMin() : Int {
            val key: String = context.getString(R.string.setting_key_stocks_usd_price_min)
            val value: String? = preferences.getString(key, "0")
            return parseInt(value ?: "0")
        }

        fun getCommonPriceMax() : Int {
            val key: String = context.getString(R.string.setting_key_stocks_usd_price_max)
            val value: String? = preferences.getString(key, "10000")
            return parseInt(value ?: "10000")
        }

        /******************** 2358 *************************/

        fun get2358ChangePercent() : Double {
            val key: String = context.getString(R.string.setting_key_2358_price_change_percent)
            val value: String? = preferences.getString(key, "-1")
            return (value ?: "-1").toDouble()
        }

        fun get2358PurchaseTime() : String {
            val key: String = context.getString(R.string.setting_key_2358_purchase_time)
            val value : String? = preferences.getString(key, "")
            return value ?: "23:58:00"
        }

        fun get2358PurchaseVolume() : Int {
            val key: String = context.getString(R.string.setting_key_2358_purchase_volume)
            val value: String? = preferences.getString(key, "1000")
            return parseInt(value ?: "1000")
        }

        fun get2358TakeProfit() : Double {
            val key: String = context.getString(R.string.setting_key_2358_take_profit)
            val value: String? = preferences.getString(key, "1")
            return (value ?: "1").toDouble()
        }

        fun get2358VolumeDayPieces() : Int {
            val key: String = context.getString(R.string.setting_key_2358_volume_min_day)
            val value: String? = preferences.getString(key, "10000")
            return parseInt(value ?: "10000")
        }

        fun get2358VolumeDayCash() : Double {
            val key: String = context.getString(R.string.setting_key_2358_volume_min_cash)
            val value: String? = preferences.getString(key, "0.1")
            return (value ?: "1").toDouble()
        }
    }
}
