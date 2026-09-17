package com.shnapps.couple

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.navigation.CplNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's single Activity. Every screen is a Compose destination (BUILD_PROMPT.md §1.2).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AfterhoursTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CplNavHost(navController = rememberNavController())
                }
            }
        }
    }
}
