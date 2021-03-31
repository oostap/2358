package com.project.ti2358.data.manager

import com.project.ti2358.BuildConfig
import com.project.ti2358.data.service.*
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
    private val strategyTelegram: StrategyTelegram by inject()

    fun startApp() {
        stockManager.loadStocks()
        depositManager.startUpdatePortfolio()
        stockManager.startUpdateIndices()

        if (SettingsManager.isAlorQoutes()) alorManager.refreshToken()

        strategyTelegram.start()
    }

    companion object {
        private val processingModule = module {
            fun provideWorkflowManager(): WorkflowManager = WorkflowManager()
            fun provideStocksManager(): StockManager = StockManager()
            fun provideDepoManager(): DepositManager = DepositManager()
            fun provideAlorManager(): AlorManager = AlorManager()
            fun provideOrderbookManager(): OrderbookManager = OrderbookManager()
            fun provideChartManager(): ChartManager = ChartManager()
            fun providePositionManager(): PositionManager = PositionManager()

            fun provideStrategyFavorites(): StrategyFavorites = StrategyFavorites()
            fun provideStrategyBlacklist(): StrategyBlacklist = StrategyBlacklist()
            fun provideStrategyPremarket(): StrategyPremarket = StrategyPremarket()
            fun provideStrategy1000Sell(): Strategy1000Sell = Strategy1000Sell()
            fun provideStrategy1000Buy(): Strategy1000Buy = Strategy1000Buy()
            fun provideStrategy2358(): Strategy2358 = Strategy2358()
            fun provideStrategy1728Up(): Strategy1728Up = Strategy1728Up()
            fun provideStrategy1728Down(): Strategy1728Down = Strategy1728Down()
            fun provideStrategyTazik(): StrategyTazik = StrategyTazik()
            fun provideStrategyReports(): StrategyReports = StrategyReports()
            fun provideStrategyTrailingStop(): StrategyTrailingStop = StrategyTrailingStop()
            fun provideStrategyFixPrice(): StrategyFixPrice = StrategyFixPrice()
            fun provideStrategyRocket(): StrategyRocket = StrategyRocket()

            // unused yet
            fun provideStrategyShorts(): StrategyShorts = StrategyShorts()
            fun provideStrategyTelegram(): StrategyTelegram = StrategyTelegram()
            fun provideStrategyTazikEndless(): StrategyTazikEndless = StrategyTazikEndless()

            single { provideStocksManager() }
            single { provideDepoManager() }
            single { provideWorkflowManager() }
            single { provideAlorManager() }
            single { provideOrderbookManager() }
            single { provideChartManager() }
            single { providePositionManager() }

            single { provideStrategyFavorites() }
            single { provideStrategyBlacklist() }
            single { provideStrategyPremarket() }
            single { provideStrategy1000Sell() }
            single { provideStrategy1000Buy() }
            single { provideStrategy2358() }
            single { provideStrategy1728Up() }
            single { provideStrategy1728Down() }
            single { provideStrategyTazik() }
            single { provideStrategyReports() }
            single { provideStrategyTrailingStop() }
            single { provideStrategyFixPrice() }
            single { provideStrategyRocket() }

            // unused yet
            single { provideStrategyShorts() }
            single { provideStrategyTelegram() }
            single { provideStrategyTazikEndless() }
        }

        private val apiModule = module {
            fun provideMarketService(retrofit: Retrofit): MarketService = MarketService(retrofit)
            fun provideOrdersService(retrofit: Retrofit): OrdersService = OrdersService(retrofit)
            fun providePortfolioService(retrofit: Retrofit): PortfolioService = PortfolioService(retrofit)
            fun provideOperationsService(retrofit: Retrofit): OperationsService = OperationsService(retrofit)
            fun provideStreamingTinkoffService(): StreamingTinkoffService = StreamingTinkoffService()
            fun provideStreamingAlorService(): StreamingAlorService = StreamingAlorService()
            fun provideThirdPartyService(retrofit: Retrofit): ThirdPartyService = ThirdPartyService(retrofit)

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