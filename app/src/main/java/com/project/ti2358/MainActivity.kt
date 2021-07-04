package com.project.ti2358

import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.marginBottom
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.manager.WorkflowManager
import com.project.ti2358.data.model.dto.daager.Index
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import com.project.ti2358.service.toPercent
import com.project.ti2358.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*
import kotlin.math.abs


@KoinApiExtension
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration

    val stockManager: StockManager by inject()
    val depositManager: DepositManager by inject()
    val workflowManager: WorkflowManager by inject()

    var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        // add back arrow to toolbar
        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        }

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // угловая круглая кнопка
        val fab: FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener { view ->
            var key = ""
            navController.currentDestination?.let {
                if (it.id == R.id.nav_premarket) {
                    key = "premarket_price_change_percent"
                } else if (it.id in listOf(R.id.nav_tazik_endless_start, R.id.nav_tazik_endless_finish, R.id.nav_tazik_endless_status)) {
                    key = "tazik_endless_set"
                } else if (it.id in listOf(R.id.nav_zontik_endless_start, R.id.nav_zontik_endless_finish, R.id.nav_zontik_endless_status)) {
                    key = "zontik_endless_set"
                } else if (it.id in listOf(R.id.nav_tazik_start, R.id.nav_tazik_finish, R.id.nav_tazik_status)) {
                    key = "tazik_set_1"
                } else if (it.id in listOf(R.id.nav_2358_start, R.id.nav_2358_finish)) {
                    key = "2358_price_change_percent"
                } else if (it.id in listOf(R.id.nav_1000_buy_start, R.id.nav_1000_buy_finish)) {
                    key = "1000_sell_buy_1"
                } else if (it.id in listOf(R.id.nav_1000_sell_start, R.id.nav_1000_sell_finish)) {
                    key = "1000_sell_set_1"
                } else if (it.id in listOf(R.id.nav_2225_start, R.id.nav_2225_finish)) {
                    key = "2225_price_change_percent"
                } else if (it.id in listOf(R.id.nav_1728_up, R.id.nav_1728_down)) {
                    key = "1728_volume_min_each_steps"
                } else if (it.id in listOf(R.id.nav_rockets)) {
                    key = "rocket_change_percent"
                } else if (it.id in listOf(R.id.nav_trends)) {
                    key = "trend_change_min_down_change_percent"
                } else if (it.id in listOf(R.id.nav_telegram)) {
                    key = "telegram_autostart"
                } else if (it.id in listOf(R.id.nav_favorites)) {
                    key = "love_set"
                } else if (it.id in listOf(R.id.nav_blacklist)) {
                    key = "black_set"
                } else if (it.id in listOf(R.id.nav_limits)) {
                    key = "limits_change_up"
                }
            }

            val bundle = bundleOf("key" to key)
            navController.navigate(R.id.nav_settings, bundle)
        }

        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            if (destination.id in listOf(R.id.nav_settings, R.id.nav_orderbook, R.id.nav_orders,
                    R.id.nav_chart, R.id.nav_donate, R.id.nav_reports, R.id.nav_premarket, R.id.nav_accounts, R.id.nav_chat)) {
                fab.visibility = View.INVISIBLE
            } else {
                fab.visibility = View.VISIBLE
            }
        }

        val header = navView.getHeaderView(0)
        val freeCashView: TextView = header.findViewById(R.id.free_cash)
        val activeAccountView: TextView = header.findViewById(R.id.active_account)

        val index1NameView: TextView = header.findViewById(R.id.index_1_name)
        val index2NameView: TextView = header.findViewById(R.id.index_2_name)
        val index3NameView: TextView = header.findViewById(R.id.index_3_name)
        val index4NameView: TextView = header.findViewById(R.id.index_4_name)
        val index5NameView: TextView = header.findViewById(R.id.index_5_name)
        val index6NameView: TextView = header.findViewById(R.id.index_6_name)

        val index2ValueView: TextView = header.findViewById(R.id.index_2_value)
        val index3ValueView: TextView = header.findViewById(R.id.index_3_value)
        val index4ValueView: TextView = header.findViewById(R.id.index_4_value)
        val index5ValueView: TextView = header.findViewById(R.id.index_5_value)
        val index6ValueView: TextView = header.findViewById(R.id.index_6_value)

        val index1ChangeView: TextView = header.findViewById(R.id.index_1_change)
        val index2ChangeView: TextView = header.findViewById(R.id.index_2_change)
        val index3ChangeView: TextView = header.findViewById(R.id.index_3_change)
        val index4ChangeView: TextView = header.findViewById(R.id.index_4_change)
        val index5ChangeView: TextView = header.findViewById(R.id.index_5_change)
        val index6ChangeView: TextView = header.findViewById(R.id.index_6_change)

        val index1EmojiView: TextView = header.findViewById(R.id.index_1_emoji)
        val index2EmojiView: TextView = header.findViewById(R.id.index_2_emoji)
        val index3EmojiView: TextView = header.findViewById(R.id.index_3_emoji)
        val index4EmojiView: TextView = header.findViewById(R.id.index_4_emoji)
        val index5EmojiView: TextView = header.findViewById(R.id.index_5_emoji)
        val index6EmojiView: TextView = header.findViewById(R.id.index_6_emoji)

        val accountView: LinearLayout = header.findViewById(R.id.account_view)
        accountView.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            navController.navigate(R.id.nav_accounts)
        }

        val versionView: TextView = header.findViewById(R.id.version)
        val pInfo: PackageInfo = TheApplication.application.applicationContext.packageManager.getPackageInfo(
            TheApplication.application.applicationContext.packageName,
            0
        )
        versionView.text = pInfo.versionName

        val actionBarDrawerToggle: ActionBarDrawerToggle = object : ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.nav_header_desc,
            R.string.copy
        ) {
            override fun onDrawerOpened(drawerView: View) {
                updateInfo()
                super.onDrawerOpened(drawerView)
            }

            override fun onDrawerClosed(drawerView: View) {
                updateInfo()
                super.onDrawerClosed(drawerView)
            }

            override fun onDrawerStateChanged(newState: Int) {
                if (newState == DrawerLayout.STATE_DRAGGING || newState == DrawerLayout.STATE_IDLE) updateInfo()
                super.onDrawerStateChanged(newState)
            }

            fun updateInfo() {
                val cash = depositManager.getFreeCashEUR() + "\n" + depositManager.getFreeCashRUB() + "\n" + depositManager.getFreeCashUSD()
                freeCashView.text = cash

                activeAccountView.text = depositManager.getActiveBrokerAccountId()

                val indices = stockManager.indices

                if (indices.size >= 5) {
                    processIndex(indices[0], index2NameView, index2ValueView, index2ChangeView, index2EmojiView)
                    processIndex(indices[1], index3NameView, index3ValueView, index3ChangeView, index3EmojiView)
                    processIndex(indices[2], index4NameView, index4ValueView, index4ChangeView, index4EmojiView)
                    processIndex(indices[3], index5NameView, index5ValueView, index5ChangeView, index5EmojiView)
                    processIndex(indices[4], index6NameView, index6ValueView, index6ChangeView, index6EmojiView, true)

                    index1NameView.text = "SUPER"
                    val superChange = indices[0].change_per + indices[1].change_per + indices[2].change_per + indices[3].change_per
                    index1ChangeView.text = superChange.toPercent()

                    index1EmojiView.text = Utils.getEmojiSuperIndex(superChange)
                    index1ChangeView.setTextColor(Utils.getColorForValue(superChange, false))
                }
            }

            fun processIndex(index: Index, name: TextView, value: TextView, change: TextView, emoji: TextView, invertedEmoji: Boolean = false) {
                index.let {
                    name.text = it.short
                    value.text = "${it.value}"
                    if (it.change_per < 0) {
                        change.text = "${it.change_per.toPercent()}"
                        if (it.change_per < -1) {
                            emoji.text = if (invertedEmoji) "🤑" else "😱"
                        } else {
                            emoji.text = if (invertedEmoji) "😍" else "😰"
                        }
                    } else {
                        change.text = "+${it.change_per.toPercent()}"
                        if (it.change_per > 1) {
                            emoji.text = if (invertedEmoji) "😱" else "🤑"
                        } else {
                            emoji.text = if (invertedEmoji) "😰" else "😍"
                        }
                    }

                    val sign = if (invertedEmoji) -1 else 1

                    change.setTextColor(Utils.getColorForValue(it.change_per * sign, false))
                    value.setTextColor(Utils.getColorForValue(it.change_per * sign, false))

                    if (abs(it.change_per) <= 0.15) {
                        emoji.text = "😐"
                    }
                }
            }
        }
        actionBarDrawerToggle.isDrawerIndicatorEnabled = false
        actionBarDrawerToggle.setToolbarNavigationClickListener {
            onSupportNavigateUp()
        }
        drawerLayout.addDrawerListener(actionBarDrawerToggle)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_settings,
                R.id.nav_portfolio,
                R.id.nav_premarket,
                R.id.nav_1000_sell_start,
                R.id.nav_1000_buy_start,
                R.id.nav_2358_start,
                R.id.nav_1728_up,
                R.id.nav_1728_down,
                R.id.nav_rockets,
                R.id.nav_fixprice,
                R.id.nav_reports,
                R.id.nav_shorts,
                R.id.nav_diagnostics,
                R.id.nav_favorites,
                R.id.nav_donate,
                R.id.nav_tazik_start,
                R.id.nav_tazik_endless_start,
                R.id.nav_telegram,
                R.id.nav_chat,
                R.id.nav_trends,
                R.id.nav_limits,
                R.id.nav_sectors,
                R.id.nav_zontik_endless_start
            ),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        workflowManager.startApp()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.requestFocus()

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Default) {
            stockManager.reloadClosePrices()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        log("TEST: onCreateOptionsMenu")

        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.main, menu)
        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        log("TEST: onSupportNavigateUp")
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        log("TEST: onOptionsItemSelected")
//        val navController = findNavController(R.id.nav_host_fragment)
//        when (item.itemId) {
//            android.R.id.home -> {
//                navController.popBackStack()
//                return true
//            }
//        }
//        return super.onOptionsItemSelected(item)
//    }

    override fun onBackPressed() {
//        log("TEST: onBackPressed")

        val drawer = findViewById<View>(R.id.drawer_layout) as DrawerLayout
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}