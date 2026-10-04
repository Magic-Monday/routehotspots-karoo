package com.routehotspots.karoo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.routehotspots.karoo.screens.MainScreen
import com.routehotspots.karoo.theme.AppTheme
import io.hammerhead.karooext.KarooSystemService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}
