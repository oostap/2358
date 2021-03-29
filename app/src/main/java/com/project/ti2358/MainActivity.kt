package com.project.ti2358

import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
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
import com.project.ti2358.service.log
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        // add back arrow to toolbar
        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true);
            supportActionBar?.setDisplayShowHomeEnabled(true);
        }
        // угловая круглая кнопка
//        val fab: FloatingActionButton = findViewById(R.id.fab)
//        fab.setOnClickListener { view ->
//            Snackbar.make(view, "ухх! кнопка!", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show()
//        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        toolbar.setNavigationOnClickListener { onSupportNavigateUp() }
//        navController.set
//        toolbar.setOnMenuItemClickListener {
//            navController.navigateUp()
//            val navController = findNavController(R.id.nav_host_fragment)
//            when (it.itemId) {
//                android.R.id.home -> {
//                    navController.popBackStack()
//                }
//            }
//            navController.popBackStack()
//        }

        val header = navView.getHeaderView(0)
        val cashUSDView: TextView = header.findViewById(R.id.free_cash_usd)
        val cashRUBView: TextView = header.findViewById(R.id.free_cash_rub)

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
                if (newState == DrawerLayout.STATE_DRAGGING || newState == DrawerLayout.STATE_IDLE)
                    updateInfo()
                super.onDrawerStateChanged(newState)
            }

            fun updateInfo() {
                cashUSDView.text = depositManager.getFreeCashUSD()
                cashRUBView.text = depositManager.getFreeCashRUB()

                val indices = stockManager.indices

                if (indices.size >= 5) {
                    processIndex(indices[0], index2NameView, index2ValueView, index2ChangeView, index2EmojiView)
                    processIndex(indices[1], index3NameView, index3ValueView, index3ChangeView, index3EmojiView)
                    processIndex(indices[2], index4NameView, index4ValueView, index4ChangeView, index4EmojiView)
                    processIndex(indices[3], index5NameView, index5ValueView, index5ChangeView, index5EmojiView)
                    processIndex(indices[4], index6NameView, index6ValueView, index6ChangeView, index6EmojiView, true)

                    index1NameView.text = "SUPER"
                    val superChange = indices[0].change_per + indices[1].change_per + indices[2].change_per + indices[3].change_per
                    index1ChangeView.text = "%.2f%%".format(superChange)

                    index1EmojiView.text = when {
                        superChange >= 2.0 -> "😍🤑😇"
                        superChange >= 1.0 -> "😍🤑"
                        superChange >= 0.2 -> "🥰"
                        abs(superChange) < 0.2 -> "😐"
                        superChange <= -5 -> "☠️☠️☠️"
                        superChange <= -4 -> "🥵🤬😡️"
                        superChange <= -3 -> "👿🤢😤️"
                        superChange <= -2 -> "😦😨😣"
                        superChange <= -1 -> "😰😭"
                        superChange <= -0.2 -> "😧"
                        else -> ""
                    }
                    index1ChangeView.setTextColor(Utils.getColorForValue(superChange))
                }
            }

            fun processIndex(index: Index, name: TextView, value: TextView, change: TextView, emoji: TextView, invertedEmoji: Boolean = false) {
                index.let {
                    name.text = it.short
                    value.text = "${it.value}"
                    if (it.change_per < 0) {
                        change.text = "${it.change_per}%"
                        if (it.change_per < -1) {
                            emoji.text = if (invertedEmoji) "🤑" else "😱"
                        } else {
                            emoji.text = if (invertedEmoji) "😍" else "😰"
                        }
                    } else {
                        change.text = "+${it.change_per}%"
                        if (it.change_per > 1) {
                            emoji.text = if (invertedEmoji) "😱" else "🤑"
                        } else {
                            emoji.text = if (invertedEmoji) "😰" else "😍"
                        }
                    }
                    change.setTextColor(Utils.getColorForValue(it.change_per))
                    value.setTextColor(Utils.getColorForValue(it.change_per))

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
                R.id.nav_1728_up,
                R.id.nav_1728_down,
                R.id.nav_rockets,
                R.id.nav_fixprice,
                R.id.nav_reports,
                R.id.nav_shorts,
                R.id.nav_diagnostics,
                R.id.nav_favorites
            ),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        workflowManager.startApp()
    }

    override fun onResume() {
        super.onResume()
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.requestFocus()
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