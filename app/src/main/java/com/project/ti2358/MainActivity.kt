package com.project.ti2358

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.manager.WorkflowManager
import com.project.ti2358.data.model.dto.daager.Index
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import kotlin.math.abs

@KoinApiExtension
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration

    val stockManager: StockManager by inject()
    val depositManager: DepositManager by inject()
    val workflowManager: WorkflowManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // угловая круглая кнопка
//        val fab: FloatingActionButton = findViewById(R.id.fab)
//        fab.setOnClickListener { view ->
//            Snackbar.make(view, "ухх! кнопка!", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show()
//        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)

        val header = navView.getHeaderView(0)
        val cashUSDView: TextView = header.findViewById(R.id.free_cash_usd)
        val cashRUBView: TextView = header.findViewById(R.id.free_cash_rub)

        val index1NameView: TextView = header.findViewById(R.id.index_1_name)
        val index2NameView: TextView = header.findViewById(R.id.index_2_name)
        val index3NameView: TextView = header.findViewById(R.id.index_3_name)
        val index4NameView: TextView = header.findViewById(R.id.index_4_name)

        val index1ValueView: TextView = header.findViewById(R.id.index_1_value)
        val index2ValueView: TextView = header.findViewById(R.id.index_2_value)
        val index3ValueView: TextView = header.findViewById(R.id.index_3_value)
        val index4ValueView: TextView = header.findViewById(R.id.index_4_value)

        val index1ChangeView: TextView = header.findViewById(R.id.index_1_change)
        val index2ChangeView: TextView = header.findViewById(R.id.index_2_change)
        val index3ChangeView: TextView = header.findViewById(R.id.index_3_change)
        val index4ChangeView: TextView = header.findViewById(R.id.index_4_change)

        val index1EmojiView: TextView = header.findViewById(R.id.index_1_emoji)
        val index2EmojiView: TextView = header.findViewById(R.id.index_2_emoji)
        val index3EmojiView: TextView = header.findViewById(R.id.index_3_emoji)
        val index4EmojiView: TextView = header.findViewById(R.id.index_4_emoji)

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
                updateInfo()
                super.onDrawerStateChanged(newState)
            }

            fun updateInfo() {
                cashUSDView.text = depositManager.getFreeCashUSD()
                cashRUBView.text = depositManager.getFreeCashRUB()

                val indices = stockManager.indexAll

                if (indices.size >= 4) {
                    processIndex(indices[0], index1NameView, index1ValueView, index1ChangeView, index1EmojiView)
                    processIndex(indices[1], index2NameView, index2ValueView, index2ChangeView, index2EmojiView)
                    processIndex(indices[2], index3NameView, index3ValueView, index3ChangeView, index3EmojiView)
                    processIndex(indices[3], index4NameView, index4ValueView, index4ChangeView, index4EmojiView, true)
                }
            }

            fun processIndex(index: Index, name: TextView, value: TextView, change: TextView, emoji: TextView, invertedEmoji: Boolean = false) {
                index.let {
                    name.text = it.name.replace(" 2000", "")
                    value.text = "${it.value}"
                    if (it.change_per < 0) {
                        change.text = "${it.change_per}%"
                        change.setTextColor(Utils.RED)
                        value.setTextColor(Utils.RED)

                        if (it.change_per < -1) {
                            emoji.text = if (invertedEmoji) "🤑" else "😱"
                        } else {
                            emoji.text = if (invertedEmoji) "😍" else "😰"
                        }
                    } else {
                        change.text = "+${it.change_per}%"
                        change.setTextColor(Utils.GREEN)
                        value.setTextColor(Utils.GREEN)

                        if (it.change_per > 1) {
                            emoji.text = if (invertedEmoji) "😱" else "🤑"
                        } else {
                            emoji.text = if (invertedEmoji) "😰" else "😍"
                        }
                    }

                    if (abs(it.change_per) <= 0.15) {
                        emoji.text = "😐"
                    }
                }
            }
        }
        drawerLayout.addDrawerListener(actionBarDrawerToggle)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_settings,
                R.id.nav_portfolio,
                R.id.nav_premarket,
                R.id.nav_postmarket,
                R.id.nav_1000_sell_start,
                R.id.nav_1000_buy_start,
                R.id.nav_2358_start,
                R.id.nav_1728_start,
                R.id.nav_1830_start,
                R.id.nav_rocket_start,
                R.id.nav_hour_start,
                R.id.nav_reports,
                R.id.nav_diagnostics,
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        workflowManager.startApp()
        this.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    override fun onResume() {
        super.onResume()
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.requestFocus()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.main, menu)
        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}