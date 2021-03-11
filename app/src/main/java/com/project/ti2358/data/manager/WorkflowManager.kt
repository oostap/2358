package com.project.ti2358.data.manager

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.project.ti2358.BuildConfig
import com.project.ti2358.TheApplication
import com.project.ti2358.data.service.*
import com.project.ti2358.service.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


@KoinApiExtension
class WorkflowManager() : KoinComponent {
    private val alorManager: AlorManager by inject()
    private val stockManager: StockManager by inject()
    private val depositManager: DepositManager by inject()

    fun startApp() {
        alorManager.refreshToken()
        stockManager.loadStocks()
        depositManager.startUpdatePortfolio()
    }

    companion object {

        private val processingModule = module {
            fun provideWorkflowManager(): WorkflowManager {
                return WorkflowManager()
            }

            fun provideStocksManager(): StockManager {
                return StockManager()
            }

            fun provideDepoManager(): DepositManager {
                return DepositManager()
            }

            fun provideAlorManager(): AlorManager {
                return AlorManager()
            }

            fun provideStrategyPremarket(): StrategyPremarket {
                return StrategyPremarket()
            }

            fun provideStrategyPostmarket(): StrategyPostmarket {
                return StrategyPostmarket()
            }

            fun provideStrategy1000Sell(): Strategy1000Sell {
                return Strategy1000Sell()
            }

            fun provideStrategy1000Buy(): Strategy1000Buy {
                return Strategy1000Buy()
            }

            fun provideStrategy1005(): Strategy1005 {
                return Strategy1005()
            }

            fun provideStrategy2358(): Strategy2358 {
                return Strategy2358()
            }

            fun provideStrategy1728(): Strategy1728 {
                return Strategy1728()
            }

            fun provideStrategy1830(): Strategy1830 {
                return Strategy1830()
            }

            fun provideStrategyRocket(): StrategyRocket {
                return StrategyRocket()
            }

            fun provideStrategyTazik(): StrategyTazik {
                return StrategyTazik()
            }

            fun provideStrategyHour(): StrategyHour {
                return StrategyHour()
            }

            fun provideStrategyReports(): StrategyReports {
                return StrategyReports()
            }

            single { provideStocksManager() }
            single { provideDepoManager() }
            single { provideWorkflowManager() }
            single { provideAlorManager() }

            single { provideStrategyPremarket() }
            single { provideStrategyPostmarket() }
            single { provideStrategy1000Sell() }
            single { provideStrategy1000Buy() }
            single { provideStrategy1005() }
            single { provideStrategy2358() }
            single { provideStrategy1728() }
            single { provideStrategy1830() }
            single { provideStrategyRocket() }
            single { provideStrategyTazik() }
            single { provideStrategyHour() }
            single { provideStrategyReports() }
        }

        private val apiModule = module {
            fun provideMarketService(retrofit: Retrofit): MarketService {
                return MarketService(retrofit)
            }

            fun provideOrdersService(retrofit: Retrofit): OrdersService {
                return OrdersService(retrofit)
            }

            fun providePortfolioService(retrofit: Retrofit): PortfolioService {
                return PortfolioService(retrofit)
            }

            fun provideOperationsService(retrofit: Retrofit): OperationsService {
                return OperationsService(retrofit)
            }

            fun provideStreamingTinkoffService(): StreamingTinkoffService {
                return StreamingTinkoffService()
            }

            fun provideStreamingAlorService(): StreamingAlorService {
                return StreamingAlorService()
            }

            fun provideThirdPartyService(retrofit: Retrofit): ThirdPartyService {
                return ThirdPartyService(retrofit)
            }

            single { provideMarketService(get()) }
            single { provideOrdersService(get()) }
            single { providePortfolioService(get()) }
            single { provideOperationsService(get()) }
            single { provideStreamingTinkoffService() }
            single { provideStreamingAlorService() }
            single { provideThirdPartyService(get()) }
        }

        private val retrofitModule = module {
            fun provideRetrofit(): Retrofit {
                var level = HttpLoggingInterceptor.Level.NONE
                if (BuildConfig.DEBUG) {
                    level = HttpLoggingInterceptor.Level.BODY
                }

                val httpClient = OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor())
                    .addNetworkInterceptor(HttpLoggingInterceptor().setLevel(level))
                    .build()

                return Retrofit.Builder()
                    .client(httpClient)
                    .baseUrl(SettingsManager.getActiveBaseUrlTinkoff())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            single { provideRetrofit() }
        }

        fun startKoin() {
            org.koin.core.context.startKoin {
                modules(retrofitModule, apiModule, processingModule)
            }
        }
    }
}