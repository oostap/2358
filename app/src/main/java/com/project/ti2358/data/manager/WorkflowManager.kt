package com.project.ti2358.data.service

import androidx.lifecycle.MutableLiveData
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.MarketInstrument
import com.project.ti2358.data.model.dto.OperationType
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@KoinApiExtension
class WorkflowManager() : KoinComponent {
    private val stockManager: StockManager by inject()
    private val depoManager: DepoManager by inject()

    private val sandboxService: SandboxService by inject()

    private val marketService: MarketService by inject()
    private val ordersService: OrdersService by inject()

    public fun startApp() {
        if (SettingsManager.isSandbox()) { // TEST
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    sandboxService.register()
                    sandboxService.setCurrencyBalance(Currency.USD, 10000)
                    val figi = marketService.searchByTicker("TSLA").instruments[0].figi
                    ordersService.placeMarketOrder(1, figi, OperationType.BUY)

                    // !!! в sandbox больше 1 лота нельзя покупать!
                    ordersService.placeMarketOrder(1, "BBG000BH5LT6", OperationType.BUY)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        stockManager.loadStocks()
        depoManager.startUpdatePortfolio()

        // TODO: запросить вчерашние дневные свечи для вчерашних объёмов
    }

    companion object {

        private val processingModule = module {
            fun provideWorkflowManager(): WorkflowManager {
                return WorkflowManager()
            }

            fun provideStocksManager(): StockManager {
                return StockManager()
            }

            fun provideDepoManager(): DepoManager {
                return DepoManager()
            }

            fun provideStrategyScreener(): StrategyScreener {
                return StrategyScreener()
            }

            fun provideStrategy1000(): Strategy1000 {
                return Strategy1000()
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

            single { provideStocksManager() }
            single { provideDepoManager() }
            single { provideWorkflowManager() }

            single { provideStrategyScreener() }
            single { provideStrategy1000() }
            single { provideStrategy2358() }
            single { provideStrategy1728() }
            single { provideStrategy1830() }
            single { provideStrategyRocket() }
        }

        private val apiModule = module {
            fun provideSandboxService(retrofit: Retrofit): SandboxService {
                return SandboxService(retrofit)
            }

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

            fun provideStreamingService(): StreamingService {
                return StreamingService()
            }


            single { provideSandboxService(get()) }
            single { provideMarketService(get()) }
            single { provideOrdersService(get()) }
            single { providePortfolioService(get()) }
            single { provideOperationsService(get()) }
            single { provideStreamingService() }
        }

        val retrofitModule = module {
            fun provideRetrofit(): Retrofit {
                val httpClient = OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor())
//                    .addNetworkInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                    .build()

                return Retrofit.Builder()
                    .client(httpClient)
                    .baseUrl(SettingsManager.getActiveBaseUrl())
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