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
            return try {
                parseInt(value ?: "0")
            } catch (e: Exception) {
                0
            }
        }

        fun getCommonPriceMax() : Int {
            val key: String = context.getString(R.string.setting_key_stocks_usd_price_max)
            val value: String? = preferences.getString(key, "10000")
            return try {
                parseInt(value ?: "10000")
            } catch (e: Exception) {
                10000
            }
        }

        /******************** 2358 *************************/

        fun get2358ChangePercent() : Double {
            val key: String = context.getString(R.string.setting_key_2358_price_change_percent)
            val value: String? = preferences.getString(key, "-1")
            return try {
                (value ?: "-1").toDouble()
            } catch (e: Exception) {
                -1.0
            }
        }

        fun get2358PurchaseTime() : String {
            val key: String = context.getString(R.string.setting_key_2358_purchase_time)
            val value : String? = preferences.getString(key, "")
            return value ?: "23:58:00"
        }

        fun get2358PurchaseVolume() : Int {
            val key: String = context.getString(R.string.setting_key_2358_purchase_volume)
            val value: String? = preferences.getString(key, "1000")
            return try {
                parseInt(value ?: "1000")
            } catch (e: Exception) {
                1000
            }
        }

        fun get2358TakeProfit() : Double {
            val key: String = context.getString(R.string.setting_key_2358_take_profit)
            val value: String? = preferences.getString(key, "1")
            return try {
                (value ?: "1").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get2358VolumeDayPieces() : Int {
            val key: String = context.getString(R.string.setting_key_2358_volume_min_day)
            val value: String? = preferences.getString(key, "10000")
            return try {
                parseInt(value ?: "10000")
            } catch (e: Exception) {
                10000
            }
        }

        fun get2358VolumeDayCash() : Double {
            val key: String = context.getString(R.string.setting_key_2358_volume_min_cash)
            val value: String? = preferences.getString(key, "0.1")
            return try {
                (value ?: "0.1").toDouble()
            } catch (e: Exception) {
                0.1
            }
        }

        /******************** 1005 *************************/

        fun get1005ChangePercent() : Double {
            val key: String = context.getString(R.string.setting_key_1005_price_change_percent)
            val value: String? = preferences.getString(key, "2")
            return try {
                (value ?: "2").toDouble()
            } catch (e: Exception) {
                2.0
            }
        }

        fun get1005VolumeDayPieces() : Int {
            val key: String = context.getString(R.string.setting_key_1005_volume_min_day)
            val value: String? = preferences.getString(key, "5000")
            return try {
                parseInt(value ?: "5000")
            } catch (e: Exception) {
                10000
            }
        }

        /******************** 1000 sell *************************/

        fun get1000SellTakeProfit() : Double {
            val key: String = context.getString(R.string.setting_key_1000_sell_take_profit)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        /******************** 1000 sell *************************/

        fun get1000BuyTakeProfit() : Double {
            val key: String = context.getString(R.string.setting_key_1000_buy_take_profit)
            val value: String? = preferences.getString(key, "1")
            return try {
                (value ?: "1").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get1000BuyPurchaseVolume() : Int {
            val key: String = context.getString(R.string.setting_key_1000_buy_purchase_volume)
            val value: String? = preferences.getString(key, "500")
            return try {
                parseInt(value ?: "500")
            } catch (e: Exception) {
                500
            }
        }

        /******************** 1728 *************************/

        fun get1728ChangePercent() : Double {
            val key: String = context.getString(R.string.setting_key_1728_price_change)
            val value: String? = preferences.getString(key, "-1")
            return try {
                (value ?: "-1").toDouble()
            } catch (e: Exception) {
                -1.0
            }
        }

        fun get1728PurchaseVolume() : Int {
            val key: String = context.getString(R.string.setting_key_1728_purchase_volume)
            val value: String? = preferences.getString(key, "1000")
            return try {
                parseInt(value ?: "1000")
            } catch (e: Exception) {
                1000
            }
        }

        fun get1728TakeProfit() : Double {
            val key: String = context.getString(R.string.setting_key_1728_take_profit)
            val value: String? = preferences.getString(key, "1")
            return try {
                (value ?: "1").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get1728VolumeBeforeStart() : Int {
            val key: String = context.getString(R.string.setting_key_1728_volume_before_start)
            val value: String? = preferences.getString(key, "10000")
            return try {
                parseInt(value ?: "10000")
            } catch (e: Exception) {
                10000
            }
        }

        fun get1728VolumeAfterStart() : Int {
            val key: String = context.getString(R.string.setting_key_1728_volume_after_start)
            val value: String? = preferences.getString(key, "10000")
            return try {
                parseInt(value ?: "10000")
            } catch (e: Exception) {
                10000
            }
        }

        fun get1728QuickBuy() : Boolean {
            val key: String = context.getString(R.string.setting_key_1728_quick_buy)
            return preferences.getBoolean(key, false)
        }
    }
}
