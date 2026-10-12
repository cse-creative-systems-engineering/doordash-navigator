package com.cse.dashnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cse.dashnav.ui.DashNavTheme
import com.cse.dashnav.ui.day.DayScreen
import com.cse.dashnav.ui.home.HomeScreen
import com.cse.dashnav.ui.replay.ReplayScreen
import com.cse.dashnav.ui.settings.SettingsScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DashNavTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onOpenDay = { nav.navigate("day/$it") },
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("day/{date}") { entry ->
                        val date = LocalDate.parse(entry.arguments?.getString("date"))
                        DayScreen(date, onBack = { nav.popBackStack() }, onReplay = { nav.navigate("replay/$date") })
                    }
                    composable("replay/{date}") { entry ->
                        val date = LocalDate.parse(entry.arguments?.getString("date"))
                        ReplayScreen(date, onBack = { nav.popBackStack() })
                    }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}
