package com.project.ti2358.data.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.service.Utils
import org.koin.core.component.KoinApiExtension
import java.lang.Integer.parseInt
import java.util.*

@KoinApiExtension
class SettingsManager {

    companion object {
        lateinit var preferences: SharedPreferences

        fun setSettingsContext(applicationContext: Context) {
            preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        }

        fun getDarkTheme(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_dark_theme)
            return preferences.getBoolean(key, false)
        }

        fun getActiveTokenTinkoff(): String {
            val tokenKey = TheApplication.application.applicationContext.getString(R.string.setting_key_token_market)
            val token: String? = preferences.getString(tokenKey, "")?.trim()
            return token ?: "TODO"
        }

        fun getActiveBaseUrlTinkoff(): String = "https://api-invest.tinkoff.ru/openapi/"

        fun isAlorQoutes(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_market_alor)
            return preferences.getBoolean(key, false)
        }

        fun getActiveTokenAlor(): String {
            val tokenKey = TheApplication.application.applicationContext.getString(R.string.setting_key_token_market_alor)
            val token: String? = preferences.getString(tokenKey, "")?.trim()
            return token ?: "TODO"
        }

        fun getActiveBrokerType(): String {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tinkoff_iis)
            val iis = preferences.getBoolean(key, false)

            return if (iis) {
                "TinkoffIis"
            } else {
                "Tinkoff"
            }
        }

        fun isAllowCurrency(currency: Currency?): Boolean {
            if (currency == Currency.USD) return true
            return false
        }

        /******************** common *************************/

        fun getCommonPriceMin(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_stocks_usd_price_min)
            val value: String? = preferences.getString(key, "5")
            return try {
                parseInt(value ?: "5")
            } catch (e: Exception) {
                5
            }
        }

        fun getCommonPriceMax(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_stocks_usd_price_max)
            val value: String? = preferences.getString(key, "200")
            return try {
                parseInt(value ?: "200")
            } catch (e: Exception) {
                200
            }
        }

        /******************** Trailing take *************************/

        fun getTrailingStopTakeProfitPercentActivation(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_trailing_stop_take_profit_percent_activation)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun getTrailingStopTakeProfitPercentDelta(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_trailing_stop_take_profit_percent_delta)
            val value: String? = preferences.getString(key, "0.25")
            return try {
                (value ?: "0.25").toDouble()
            } catch (e: Exception) {
                0.25
            }
        }

        fun getTrailingStopStopLossPercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_trailing_stop_stop_loss_percent)
            val value: String? = preferences.getString(key, "0.0")
            return try {
                (value ?: "0.0").toDouble()
            } catch (e: Exception) {
                0.0
            }
        }
        /******************** orderbook *************************/

        fun getOrderbookVolumes(): String {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_orderbook_volumes)
            val value: String? = preferences.getString(key, "1 5 10 50 100 500 1000 5000")
            return value ?: "1 5 10 50 100 500 1000 5000"
        }

        fun getOrderbookPrices(): String {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_orderbook_prices)
            val value: String? = preferences.getString(key, "1 2 5 10")
            return value ?: "1 2 5 10"
        }

        /******************** 2358 *************************/

        fun get2358ChangePercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2358_price_change_percent)
            val value: String? = preferences.getString(key, "-1")
            return try {
                (value ?: "-1").toDouble()
            } catch (e: Exception) {
                -1.0
            }
        }

        fun get2358PurchaseTime(): String {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2358_purchase_time)
            val value: String? = preferences.getString(key, "")
            return value ?: "23:58:00"
        }

        fun get2358PurchaseVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2358_purchase_volume)
            val value: String? = preferences.getString(key, "1000")
            return try {
                parseInt(value ?: "1000")
            } catch (e: Exception) {
                1000
            }
        }

        fun get2358TakeProfitFrom(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2358_take_profit_from)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get2358TakeProfitTo(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2358_take_profit_to)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get2358TakeProfitStep(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2358_take_profit_step)
            val value: String? = preferences.getString(key, "1")
            return try {
                parseInt(value ?: "1")
            } catch (e: Exception) {
                1
            }
        }

        fun get2358VolumeDayPieces(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2358_volume_min_day)
            val value: String? = preferences.getString(key, "10000")
            return try {
                parseInt(value ?: "10000")
            } catch (e: Exception) {
                10000
            }
        }

        fun get2358VolumeDayCash(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2358_volume_min_cash)
            val value: String? = preferences.getString(key, "0.1")
            return try {
                (value ?: "0.1").toDouble()
            } catch (e: Exception) {
                0.1
            }
        }

        /******************** Premarket *************************/

        fun getPremarketChangePercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1005_price_change_percent)
            val value: String? = preferences.getString(key, "0")
            return try {
                (value ?: "0").toDouble()
            } catch (e: Exception) {
                0.0
            }
        }

        fun getPremarketVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1005_volume_min_day)
            val value: String? = preferences.getString(key, "0")
            return try {
                parseInt(value ?: "0")
            } catch (e: Exception) {
                0
            }
        }

        /******************** 1000 sell *************************/

        fun get1000SellTakeProfit(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1000_sell_take_profit)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        /******************** 1000 sell *************************/

        fun get1000BuyTakeProfit(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1000_buy_take_profit)
            val value: String? = preferences.getString(key, "1")
            return try {
                (value ?: "1").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get1000BuyPurchaseVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1000_buy_purchase_volume)
            val value: String? = preferences.getString(key, "500")
            return try {
                parseInt(value ?: "500")
            } catch (e: Exception) {
                500
            }
        }

        /******************** 1728 *************************/

        fun get1728ChangePercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1728_price_change)
            val value: String? = preferences.getString(key, "1")
            return try {
                (value ?: "1").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get1728PurchaseVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1728_purchase_volume)
            val value: String? = preferences.getString(key, "1000")
            return try {
                parseInt(value ?: "1000")
            } catch (e: Exception) {
                1000
            }
        }

        fun get1728TakeProfit(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1728_take_profit)
            val value: String? = preferences.getString(key, "1")
            return try {
                (value ?: "1").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get1728TrailingStop(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1728_trailing_stop)
            return preferences.getBoolean(key, false)
        }

        fun get1728Volume(step: Int): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1728_volume_min_steps)
            val value: String? = preferences.getString(key, "1000 2000 3000")
            val volumes = value?.split(" ") ?: return 2000
            if (volumes.size < 3) return 2000

            return try {
                parseInt(volumes[step])
            } catch (e: Exception) {
                2000
            }
        }

        /******************** THE TAZIK *************************/

        fun getTazikChangePercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_min_percent_to_buy)
            val value: String? = preferences.getString(key, "-1.0")
            return try {
                (value ?: "-1.0").toDouble()
            } catch (e: Exception) {
                -1.0
            }
        }

        fun getTazikPurchaseVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_purchase_volume)
            val value: String? = preferences.getString(key, "500")
            return try {
                parseInt(value ?: "500")
            } catch (e: Exception) {
                500
            }
        }

        fun getTazikPurchaseParts(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_purchase_parts)
            val value: String? = preferences.getString(key, "2")
            return try {
                (value ?: "2").toInt()
            } catch (e: Exception) {
                2
            }
        }

        fun getTazikTakeProfit(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_take_profit)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun getTazikBuyAsk(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_buy_ask)
            return preferences.getBoolean(key, false)
        }

        fun getTazikBuyMarket(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_buy_market)
            return preferences.getBoolean(key, false)
        }

        fun getTazikBuyBid(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_buy_bid)
            return preferences.getBoolean(key, false)
        }

        fun getTazikAllowAveraging(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_allow_averaging)
            return preferences.getBoolean(key, false)
        }

        fun getTazikNearestTime(): String {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_times2)
            var time = preferences.getString(key, "06:59:50")

            time = time?.replace(" ", "")
            time = time?.replace("\n", "")

            if (time != null && time != "") {
                val times = time.split(",").toTypedArray()

                // отсортировать по возрастанию
                times.sortBy { t ->
                    val dayTime = t.split(":").toTypedArray()
                    parseInt(dayTime[0]) * 3600 + parseInt(dayTime[1]) * 60 + parseInt(dayTime[2])
                }

                for (t in times) {
                    val dayTime = t.split(":").toTypedArray()
                    if (dayTime.size < 3) continue

                    val hours: Int
                    val minutes: Int
                    val seconds: Int
                    try {
                        hours = parseInt(dayTime[0])
                        minutes = parseInt(dayTime[1])
                        seconds = parseInt(dayTime[2])
                    } catch (e: Exception) {
                        Utils.showToastAlert("Неверный формат времени в настройках!")
                        continue
                    }
                    val currentMskTime = Utils.getTimeMSK()

                    val hoursMsk = currentMskTime.get(Calendar.HOUR_OF_DAY)
                    val minutesMsk = currentMskTime.get(Calendar.MINUTE)
                    val secondsMsk = currentMskTime.get(Calendar.SECOND)

                    val total = hours * 3600 + minutes * 60 + seconds
                    val totalMsk = hoursMsk * 3600 + minutesMsk * 60 + secondsMsk
                    if (totalMsk < total) {
                        return t
                    }
                }
            }

            return ""
        }

        /******************** Rockets *************************/
        fun getRocketChangePercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_rocket_change_percent)
            val value: String? = preferences.getString(key, "3.0")
            return try {
                (value ?: "3.0").toDouble()
            } catch (e: Exception) {
                3.0
            }
        }

        fun getRocketChangeMinutes(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_rocket_change_minutes)
            val value: String? = preferences.getString(key, "5")
            return try {
                (value ?: "5").toInt()
            } catch (e: Exception) {
                5
            }
        }

        fun getRocketNotifyAlive(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_rocket_notify_alive)
            val value: String? = preferences.getString(key, "5")
            return try {
                (value ?: "5").toInt()
            } catch (e: Exception) {
                5
            }
        }
    }
}
