package com.project.ti2358.data.manager

import com.project.ti2358.BuildConfig
import com.project.ti2358.data.alor.service.AlorOrdersService
import com.project.ti2358.data.alor.service.AlorPortfolioService
import com.project.ti2358.data.alor.service.StreamingAlorService
import com.project.ti2358.data.common.AuthInterceptor
import com.project.ti2358.data.daager.service.ThirdPartyService
import com.project.ti2358.data.pantini.service.StreamingPantiniService
import com.project.ti2358.data.tinkoff.service.*
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
    private val alorPortfolioManager: AlorPortfolioManager by inject()
    private val stockManager: StockManager by inject()
    private val tinkoffPortfolioManager: TinkoffPortfolioManager by inject()
    private val strategySpeaker: StrategySpeaker by inject()

    fun startApp() {
        stockManager.loadStocks()
        tinkoffPortfolioManager.startUpdatePortfolio()
        stockManager.startUpdateIndices()

        if (SettingsManager.getAlorToken() != "") {
            alorPortfolioManager.start()
        }

        strategySpeaker.start()
    }

    companion object {
        private val processingModule = module {
            fun provideWorkflowManager(): WorkflowManager = WorkflowManager()
            fun provideStocksManager(): StockManager = StockManager()
            fun providePortfolioManager(): TinkoffPortfolioManager = TinkoffPortfolioManager()
            fun provideOrderbookManager(): OrderbookManager = OrderbookManager()
            fun provideChartManager(): ChartManager = ChartManager()
            fun providePositionManager(): PositionManager = PositionManager()

            fun provideAlorAuthManager(): AlorAuthManager = AlorAuthManager()
            fun provideAlorPortfolioManager(): AlorPortfolioManager = AlorPortfolioManager()

            fun provideBrokerManager(): BrokerManager = BrokerManager()

            fun provideStrategyFavorites(): StrategyLove = StrategyLove()
            fun provideStrategyBlacklist(): StrategyBlacklist = StrategyBlacklist()
            fun provideStrategyPremarket(): StrategyPremarket = StrategyPremarket()
            fun provideStrategy1000Sell(): Strategy1000Sell = Strategy1000Sell()
            fun provideStrategy1000Buy(): Strategy1000Buy = Strategy1000Buy()
            fun provideStrategy2358(): Strategy2358 = Strategy2358()
            fun provideStrategy2225(): Strategy2225 = Strategy2225()
            fun provideStrategy1728Up(): Strategy1728Up = Strategy1728Up()
            fun provideStrategy1728Down(): Strategy1728Down = Strategy1728Down()
            fun provideStrategyTazik(): StrategyTazik = StrategyTazik()
            fun provideStrategyReports(): StrategyReports = StrategyReports()
            fun provideStrategyTrailingStop(): StrategyTrailingStop = StrategyTrailingStop()
            fun provideStrategyFixPrice(): StrategyFixPrice = StrategyFixPrice()
            fun provideStrategyRocket(): StrategyRocket = StrategyRocket()
            fun provideStrategyTazikEndless(): StrategyTazikEndless = StrategyTazikEndless()
            fun provideStrategyZontikEndless(): StrategyZontikEndless = StrategyZontikEndless()
            fun provideStrategySpeaker(): StrategySpeaker = StrategySpeaker()
            fun provideStrategyTelegram(): StrategyTelegram = StrategyTelegram()
            fun provideStrategyFollower(): StrategyTelegramCommands = StrategyTelegramCommands()
            fun provideStrategyTrend(): StrategyTrend = StrategyTrend()
            fun provideStrategyLimits(): StrategyLimits = StrategyLimits()
            fun provideStrategySector(): StrategySector = StrategySector()
            fun provideStrategyArbitration(): StrategyArbitration = StrategyArbitration()
            fun provideStrategyDayLow(): StrategyDayLow = StrategyDayLow()

            // unused yet
            fun provideStrategyShorts(): StrategyShorts = StrategyShorts()
            fun provideStrategyTA(): StrategyTA = StrategyTA()

            // tinkoff
            single { provideStocksManager() }
            single { providePortfolioManager() }
            single { provideWorkflowManager() }
            single { provideOrderbookManager() }
            single { provideChartManager() }
            single { providePositionManager() }

            // alor
            single { provideAlorAuthManager() }
            single { provideAlorPortfolioManager() }

            // broker
            single { provideBrokerManager() }

            single { provideStrategyFavorites() }
            single { provideStrategyBlacklist() }
            single { provideStrategyPremarket() }
            single { provideStrategy1000Sell() }
            single { provideStrategy1000Buy() }
            single { provideStrategy2358() }
            single { provideStrategy2225() }
            single { provideStrategy1728Up() }
            single { provideStrategy1728Down() }
            single { provideStrategyTazik() }
            single { provideStrategyReports() }
            single { provideStrategyTrailingStop() }
            single { provideStrategyFixPrice() }
            single { provideStrategyRocket() }
            single { provideStrategyTazikEndless() }
            single { provideStrategyZontikEndless() }
            single { provideStrategySpeaker() }
            single { provideStrategyTelegram() }
            single { provideStrategyFollower() }
            single { provideStrategyTrend() }
            single { provideStrategyLimits() }
            single { provideStrategySector() }
            single { provideStrategyArbitration() }
            single { provideStrategyDayLow() }

            // unused yet
            single { provideStrategyShorts() }
            single { provideStrategyTA() }
        }

        private val apiModule = module {
            fun provideMarketService(retrofit: Retrofit): MarketService = MarketService(retrofit)
            fun provideOrdersService(retrofit: Retrofit): OrdersService = OrdersService(retrofit)
            fun providePortfolioService(retrofit: Retrofit): PortfolioService = PortfolioService(retrofit)
            fun provideOperationsService(retrofit: Retrofit): OperationsService = OperationsService(retrofit)
            fun provideStreamingTinkoffService(): StreamingTinkoffService = StreamingTinkoffService()
            fun provideStreamingAlorService(): StreamingAlorService = StreamingAlorService()
            fun provideStreamingPantiniService(): StreamingPantiniService = StreamingPantiniService()
            fun provideThirdPartyService(retrofit: Retrofit): ThirdPartyService = ThirdPartyService(retrofit)

            fun provideAlorPortfolioService(retrofit: Retrofit): AlorPortfolioService = AlorPortfolioService(retrofit)
            fun provideAlorOrdersService(retrofit: Retrofit): AlorOrdersService = AlorOrdersService(retrofit)

            single { provideMarketService(get()) }
            single { provideOrdersService(get()) }
            single { providePortfolioService(get()) }
            single { provideOperationsService(get()) }
            single { provideStreamingTinkoffService() }
            single { provideStreamingAlorService() }
            single { provideStreamingPantiniService() }
            single { provideThirdPartyService(get()) }

            single { provideAlorPortfolioService(get()) }
            single { provideAlorOrdersService(get()) }
        }

        private val retrofitModule = module {
            fun provideRetrofit(): Retrofit {
                var level = HttpLoggingInterceptor.Level.NONE
                if (BuildConfig.DEBUG) {
                    level = HttpLoggingInterceptor.Level.BASIC
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