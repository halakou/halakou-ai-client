package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.halakou.presentation.mvi.ChatIntent
import com.example.halakou.presentation.mvi.ChatViewModel
import com.example.halakou.presentation.ui.MainChatScreen
import com.example.halakou.presentation.ui.SettingsVaultScreen
import com.example.ui.theme.HalakouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HalakouTheme {
                HalakouApp()
            }
        }
    }
}

@Composable
fun HalakouApp(
    viewModel: ChatViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.isSettingsOpen || state.isDrawerOpen) {
        if (state.isSettingsOpen) {
            viewModel.onIntent(ChatIntent.SetSettingsOpen(false))
        } else if (state.isDrawerOpen) {
            viewModel.onIntent(ChatIntent.SetDrawerOpen(false))
        }
    }

    if (state.isSettingsOpen) {
        SettingsVaultScreen(
            state = state,
            viewModel = viewModel,
            onBack = { viewModel.onIntent(ChatIntent.SetSettingsOpen(false)) },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        MainChatScreen(
            state = state,
            viewModel = viewModel,
            onOpenSettings = { viewModel.onIntent(ChatIntent.SetSettingsOpen(true)) },
            modifier = Modifier.fillMaxSize()
        )
    }
}
