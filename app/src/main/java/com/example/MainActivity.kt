package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.BookDetailScreen
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DigiBookViewModel
import com.example.ui.viewmodel.Screen
import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    private val viewModel: DigiBookViewModel by viewModels()

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure Coil to support Cloudflare Access Zero Trust authorization headers
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val token = viewModel.repository.preferenceManager.cfAccessToken
                        val requestBuilder = original.newBuilder()
                        if (token.isNotEmpty()) {
                            requestBuilder.addHeader("Cf-Access-Jwt-Assertion", token)
                            requestBuilder.addHeader("Cookie", "CF_Authorization=$token")
                        }
                        chain.proceed(requestBuilder.build())
                    }
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)
        
        // Supports full edge-to-edge drawing under status bars
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()

                // Intercept Android hardware back taps
                BackHandler(enabled = currentScreen != Screen.Connect && currentScreen != Screen.Dashboard) {
                    viewModel.handleBackPress()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    // Modern horizontal slide transition between audiobook decks
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { -it })
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            is Screen.Connect -> ConnectScreen(viewModel = viewModel)
                            is Screen.Dashboard -> DashboardScreen(viewModel = viewModel)
                            is Screen.BookDetail -> BookDetailScreen(
                                bookId = screen.bookId,
                                viewModel = viewModel
                            )
                            is Screen.Player -> PlayerScreen(
                                bookId = screen.bookId,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
