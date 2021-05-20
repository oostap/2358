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

        fun getAlorQuotes(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_alor_quotes)
            return preferences.getBoolean(key, false)
        }

        fun getAlorOrdebook(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_alor_orderbook)
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
            val value: String? = preferences.getString(key, "0")
            return try {
                parseInt(value ?: "0")
            } catch (e: Exception) {
                0
            }
        }

        fun getCommonPriceMax(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_stocks_usd_price_max)
            val value: String? = preferences.getString(key, "5000")
            return try {
                parseInt(value ?: "5000")
            } catch (e: Exception) {
                5000
            }
        }

        fun isAllowRus(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_stocks_allow_rus)
            return preferences.getBoolean(key, false)
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

        fun getTrailingStopSellBestBid(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_trailing_stop_sell_best_bid)
            return preferences.getBoolean(key, false)
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
            val value: String? = preferences.getString(key, "22:58:00")
            return value ?: "22:58:00"
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
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get2358ProtectStockUp(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2358_protect_stock_up)
            return preferences.getBoolean(key, true)
        }

        /******************** Love *************************/

        fun getLoveSet(): List<String> {
            val key = TheApplication.application.applicationContext.getString(R.string.setting_key_love_set)
            val value: String? = preferences.getString(key, "")?.trim()
            val array = value?.split(" ")
            return array ?: emptyList()
        }

        /******************** Premarket *************************/

        fun getPremarketChangePercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_premarket_price_change_percent)
            val value: String? = preferences.getString(key, "0")
            return try {
                (value ?: "0").toDouble()
            } catch (e: Exception) {
                0.0
            }
        }

        fun getPremarketVolumeMin(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_premarket_volume_min_day)
            val value: String? = preferences.getString(key, "0")
            return try {
                parseInt(value ?: "0")
            } catch (e: Exception) {
                0
            }
        }

        fun getPremarketVolumeMax(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_premarket_volume_max_day)
            val value: String? = preferences.getString(key, "10000000")
            return try {
                parseInt(value ?: "10000000")
            } catch (e: Exception) {
                10000000
            }
        }

        fun getPremarketOnlyLove(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_premarket_only_love)
            return preferences.getBoolean(key, false)
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

        /******************** 1000 buy *************************/

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

        fun get1000BuyOrderLifeTimeSeconds(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1000_buy_order_lifetime_seconds)
            val value: String? = preferences.getString(key, "120")
            return try {
                parseInt(value ?: "120")
            } catch (e: Exception) {
                120
            }
        }

        /******************** 1728 *************************/

        fun get1728ChangePercent(step: Int): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1728_price_change_steps)
            val value: String? = preferences.getString(key, "1.0 1.0 1.0")
            val volumes = value?.split(" ") ?: return 1.0
            if (volumes.size < 3) return 1.0

            return try {
                volumes[step].toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get1728PurchaseVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1728_purchase_volume)
            val value: String? = preferences.getString(key, "0")
            return try {
                parseInt(value ?: "0")
            } catch (e: Exception) {
                0
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

        fun get1728CalcFromOS(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1728_calc_from_os)
            return preferences.getBoolean(key, false)
        }

        fun get1728Volume(step: Int): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_1728_volume_min_each_steps)
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

        fun getTazikSet1(): List<String> {
            val key = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_set_1)
            val value: String? = preferences.getString(key, "")?.trim()
            val array = value?.split(" ")
            return array ?: emptyList()
        }

        fun getTazikSet2(): List<String> {
            val key = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_set_2)
            val value: String? = preferences.getString(key, "")?.trim()
            val array = value?.split(" ")
            return array ?: emptyList()
        }

        fun getTazikSet3(): List<String> {
            val key = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_set_3)
            val value: String? = preferences.getString(key, "")?.trim()
            val array = value?.split(" ")
            return array ?: emptyList()
        }

        fun getTazikChangePercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_min_percent_to_buy)
            val value: String? = preferences.getString(key, "-1.0")
            return try {
                (value ?: "-1.0").toDouble()
            } catch (e: Exception) {
                -1.0
            }
        }

        fun getTazikMinVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_min_volume)
            val value: String? = preferences.getString(key, "100")
            return try {
                parseInt(value ?: "100")
            } catch (e: Exception) {
                100
            }
        }

        fun getTazikOrderLifeTimeSeconds(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_order_lifetime_seconds)
            val value: String? = preferences.getString(key, "120")
            return try {
                parseInt(value ?: "120")
            } catch (e: Exception) {
                120
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

        fun getTazikApproximationFactor(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_approximation_factor)
            val value: String? = preferences.getString(key, "0.65")
            return try {
                (value ?: "0.65").toDouble()
            } catch (e: Exception) {
                0.65
            }
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

        fun getTazikExcludeReports(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_exclude_report)
            return preferences.getBoolean(key, true)
        }

        fun getTazikExcludeDivs(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_exclude_divs)
            return preferences.getBoolean(key, true)
        }

        fun getTazikExcludeDepo(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_exclude_depo)
            return preferences.getBoolean(key, true)
        }

        fun getTazikVoice(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_voice)
            return preferences.getBoolean(key, true)
        }
        /******************** THE TAZIK ENDLESS *************************/

        fun getTazikEndlessSet1(): List<String> {
            val key = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_set)
            val value: String? = preferences.getString(key, "")?.trim()
            val array = value?.split("SPCE GTHX ARCT BLUE")
            return array ?: emptyList()
        }

        fun getTazikEndlessSet2(): List<String> {
            val key = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_set_2)
            val value: String? = preferences.getString(key, "")?.trim()
            val array = value?.split("VIPS ACH BABA JD BIDU")
            return array ?: emptyList()
        }

        fun getTazikEndlessSet3(): List<String> {
            val key = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_set_3)
            val value: String? = preferences.getString(key, "")?.trim()
            val array = value?.split("VTBR OZON MGNT BELU")
            return array ?: emptyList()
        }

        fun getTazikEndlessResetIntervalSeconds(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_reset_interval_seconds)
            val value: String? = preferences.getString(key, "300")
            return try {
                parseInt(value ?: "300")
            } catch (e: Exception) {
                300
            }
        }

        fun getTazikEndlessMinVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_min_volume)
            val value: String? = preferences.getString(key, "100")
            return try {
                parseInt(value ?: "100")
            } catch (e: Exception) {
                100
            }
        }

        fun getTazikEndlessDayMinVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_day_min_volume)
            val value: String? = preferences.getString(key, "100")
            return try {
                parseInt(value ?: "100")
            } catch (e: Exception) {
                100
            }
        }

        fun getTazikEndlessOrderLifeTimeSeconds(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_order_lifetime_seconds)
            val value: String? = preferences.getString(key, "120")
            return try {
                parseInt(value ?: "120")
            } catch (e: Exception) {
                120
            }
        }

        fun getTazikEndlessChangePercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_min_percent_to_buy)
            val value: String? = preferences.getString(key, "-1.0")
            return try {
                (value ?: "-1.0").toDouble()
            } catch (e: Exception) {
                -1.0
            }
        }

        fun getTazikEndlessPurchaseVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_purchase_volume)
            val value: String? = preferences.getString(key, "500")
            return try {
                parseInt(value ?: "500")
            } catch (e: Exception) {
                500
            }
        }

        fun getTazikEndlessPurchaseParts(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_purchase_parts)
            val value: String? = preferences.getString(key, "2")
            return try {
                (value ?: "2").toInt()
            } catch (e: Exception) {
                2
            }
        }

        fun getTazikEndlessTakeProfit(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_take_profit)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun getTazikEndlessApproximationFactor(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_approximation_factor)
            val value: String? = preferences.getString(key, "0.65")
            return try {
                (value ?: "0.65").toDouble()
            } catch (e: Exception) {
                0.65
            }
        }

        fun getTazikEndlessAllowAveraging(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_allow_averaging)
            return preferences.getBoolean(key, false)
        }

        fun getTazikEndlessExcludeReports(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_exclude_report)
            return preferences.getBoolean(key, true)
        }

        fun getTazikEndlessExcludeDivs(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_exclude_divs)
            return preferences.getBoolean(key, true)
        }

        fun getTazikEndlessExcludeDepo(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_exclude_depo)
            return preferences.getBoolean(key, true)
        }

        fun getTazikEndlessSpikeProtection(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_spike_protection)
            val value: String? = preferences.getString(key, "1")
            return try {
                parseInt(value ?: "1")
            } catch (e: Exception) {
                1
            }
        }

        fun getTazikEndlessClosePriceProtection(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_close_price_protection)
            return preferences.getBoolean(key, false)
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
            val value: String? = preferences.getString(key, "10")
            return try {
                (value ?: "10").toInt()
            } catch (e: Exception) {
                10
            }
        }

        fun getRocketChangeVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_rocket_change_volume)
            val value: String? = preferences.getString(key, "0")
            return try {
                (value ?: "0").toInt()
            } catch (e: Exception) {
                0
            }
        }

        fun getRocketNotifyAlive(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_rocket_notify_alive_seconds)
            val value: String? = preferences.getString(key, "60")
            return try {
                (value ?: "60").toInt()
            } catch (e: Exception) {
                60
            }
        }

        fun getRocketVoice(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_rocket_voice)
            return preferences.getBoolean(key, true)
        }

        /******************** Telegram *************************/
        fun getTelegramAutostart(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_autostart)
            return preferences.getBoolean(key, false)
        }

        fun getTelegramBotApiKey(): String {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_bot_api_key)
            val value: String? = preferences.getString(key, "")
            return value ?: ""
        }

        fun getTelegramChatID(): List<Long> {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_channel_id)
            val value: String? = preferences.getString(key, "")
            val ids = value?.split(" ") ?: return emptyList()
            return ids.map { it.toLong() }
        }


        fun getTelegramUpdateDelay(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_update_delay)
            val value: String? = preferences.getString(key, "10")
            return try {
                parseInt(value ?: "10")
            } catch (e: Exception) {
                10
            }
        }

        fun getTelegramHello(): String {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_hello)
            val value: String? = preferences.getString(key, "Бот запущен 🦌")
            return value ?: "Бот запущен 🦌"
        }

        fun getTelegramBye(): String {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_bye)
            val value: String? = preferences.getString(key, "Бот остановлен! 🐹")
            return value ?: "Бот остановлен! 🐹"
        }

        fun getTelegramSendTrades(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_send_trades)
            return preferences.getBoolean(key, true)
        }

        fun getTelegramSendOrders(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_send_orders)
            return preferences.getBoolean(key, false)
        }

        fun getTelegramSendRockets(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_send_rockets)
            return preferences.getBoolean(key, true)
        }

        fun getTelegramSendTaziks(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_send_taziks)
            return preferences.getBoolean(key, true)
        }

        fun getTelegramSendTrends(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_telegram_send_trends)
            return preferences.getBoolean(key, true)
        }
        /******************** 2225 short *************************/

        fun get2225ChangePercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2225_price_change_percent)
            val value: String? = preferences.getString(key, "1")
            return try {
                (value ?: "1").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get2225PurchaseTime(): String {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2225_purchase_time)
            val value: String? = preferences.getString(key, "22:24:00")
            return value ?: "22:24:00"
        }

        fun get2225PurchaseVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2225_purchase_volume)
            val value: String? = preferences.getString(key, "0")
            return try {
                parseInt(value ?: "0")
            } catch (e: Exception) {
                0
            }
        }

        fun get2225TakeProfitFrom(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2225_take_profit_from)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get2225TakeProfitTo(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2225_take_profit_to)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get2225TakeProfitStep(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2225_take_profit_step)
            val value: String? = preferences.getString(key, "1")
            return try {
                parseInt(value ?: "1")
            } catch (e: Exception) {
                1
            }
        }

        fun get2225VolumeDayPieces(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2225_volume_min_day)
            val value: String? = preferences.getString(key, "150000")
            return try {
                parseInt(value ?: "150000")
            } catch (e: Exception) {
                150000
            }
        }

        fun get2225VolumeDayCash(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2225_volume_min_cash)
            val value: String? = preferences.getString(key, "1.0")
            return try {
                (value ?: "1.0").toDouble()
            } catch (e: Exception) {
                1.0
            }
        }

        fun get2225ProtectStockUp(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_2225_protect_stock_up)
            return preferences.getBoolean(key, false)
        }

        /******************** Follower *************************/
        fun getFollowerIds(): List<Long> {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_follower_pastuh_id)
            val value: String? = preferences.getString(key, "")
            val ids = value?.split(" ") ?: return emptyList()
            return ids.map { it.toLong() }
        }

        fun getFollowerPurchaseVolume(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_follower_purchase_volume)
            val value: String? = preferences.getString(key, "250")
            return try {
                parseInt(value ?: "250")
            } catch (e: Exception) {
                250
            }
        }

        /******************** Trends *************************/
        fun getTrendMinDownPercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_trend_change_min_down_change_percent)
            val value: String? = preferences.getString(key, "5.0")
            return try {
                (value ?: "5.0").toDouble()
            } catch (e: Exception) {
                5.0
            }
        }

        fun getTrendMinUpPercent(): Double {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_trend_change_min_up_change_percent)
            val value: String? = preferences.getString(key, "20.0")
            return try {
                (value ?: "20.0").toDouble()
            } catch (e: Exception) {
                20.0
            }
        }

        fun getTrendAfterMinutes(): Int {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_trend_after_minutes)
            val value: String? = preferences.getString(key, "15")
            return try {
                parseInt(value ?: "15")
            } catch (e: Exception) {
                15
            }
        }

        fun getTrendLove(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_trend_only_love)
            return preferences.getBoolean(key, false)
        }

        fun getTrendVoice(): Boolean {
            val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_trend_voice)
            return preferences.getBoolean(key, true)
        }
    }
}
