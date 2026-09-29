package com.example.halakou.presentation.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.halakou.domain.model.ChatMessage
import com.example.halakou.domain.model.LlmProvider
import com.example.halakou.domain.model.MessageRole
import com.example.halakou.domain.orchestrator.FallbackEvent
import com.example.halakou.presentation.mvi.ChatIntent
import com.example.halakou.presentation.mvi.ChatSideEffect
import com.example.halakou.presentation.mvi.ChatUiState
import com.example.halakou.presentation.mvi.ChatViewModel
import com.example.halakou.presentation.util.HapticFeedbackHelper
import com.example.halakou.presentation.util.HapticInteraction
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BubbleUser
import com.example.ui.theme.BubbleUserBorder
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    state: ChatUiState,
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collectLatest { effect ->
            when (effect) {
                is ChatSideEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is ChatSideEffect.ScrollToBottom -> {
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(state.messages.size - 1)
                    }
                }
                is ChatSideEffect.TriggerHaptic -> {
                    HapticFeedbackHelper.performHaptic(view, haptic, effect.interaction)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = androidx.compose.material3.rememberDrawerState(
            initialValue = if (state.isDrawerOpen) androidx.compose.material3.DrawerValue.Open else androidx.compose.material3.DrawerValue.Closed
        ),
        gesturesEnabled = true,
        drawerContent = {
            SessionDrawerContent(
                state = state,
                viewModel = viewModel,
                onClose = { viewModel.onIntent(ChatIntent.SetDrawerOpen(false)) }
            )
        }
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    state = state,
                    viewModel = viewModel,
                    onOpenDrawer = { viewModel.onIntent(ChatIntent.SetDrawerOpen(true)) },
                    onOpenSettings = onOpenSettings
                )
            },
            bottomBar = {
                ChatComposerBar(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                )
            },
            containerColor = DarkCanvas,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = modifier
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (state.messages.isEmpty()) {
                    EmptyChatGreeting(
                        selectedProvider = state.selectedProvider,
                        selectedModel = state.selectedModel,
                        onOpenRadar = { viewModel.onIntent(ChatIntent.SetFreeModelsRadarOpen(true)) },
                        onPromptClick = { prompt ->
                            HapticFeedbackHelper.performHaptic(view, haptic, HapticInteraction.SEND_MESSAGE)
                            viewModel.onIntent(ChatIntent.UpdateInputText(prompt))
                            viewModel.onIntent(ChatIntent.SendMessage)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(10.dp)) }

                        items(state.messages, key = { it.id }) { message ->
                            AnimatedMessageItem(message = message, currentModel = state.selectedModel)
                        }

                        // Live tool execution pill when generating
                        if (state.activeToolName != null) {
                            item {
                                ActiveToolExecutingCard(
                                    toolName = state.activeToolName,
                                    args = state.activeToolArgs ?: ""
                                )
                            }
                        }

                        // Generating pulse indicator
                        if (state.isGenerating && state.activeToolName == null) {
                            item {
                                GeneratingPulseIndicator(
                                    modelName = if (state.isVisionBypassActive) "Vision Engine" else state.selectedModel
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                // Smooth animated Circuit Breaker Fallback Notification banner
                AnimatedVisibility(
                    visible = state.activeFallbackEvent != null,
                    enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    state.activeFallbackEvent?.let { event ->
                        CircuitBreakerFallbackBanner(
                            event = event,
                            onDismiss = { viewModel.onIntent(ChatIntent.DismissFallbackBanner) },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Error notification banner if everything in pool fails
                if (state.errorMessage != null && state.activeFallbackEvent == null) {
                    ErrorNotificationBanner(
                        message = state.errorMessage,
                        isKeyMissing = state.isKeyMissing,
                        onDismiss = { viewModel.onIntent(ChatIntent.ClearError) },
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    )
                }

                // Free Models Radar Sheet
                if (state.isFreeModelsRadarOpen) {
                    FreeModelsRadarSheet(
                        state = state,
                        viewModel = viewModel,
                        onDismiss = { viewModel.onIntent(ChatIntent.SetFreeModelsRadarOpen(false)) }
                    )
                }

                // Gateway Latency Radar Sheet
                if (state.isLatencyMonitorOpen) {
                    GatewayLatencySheet(
                        state = state,
                        viewModel = viewModel,
                        onDismiss = { viewModel.onIntent(ChatIntent.SetLatencyMonitorOpen(false)) }
                    )
                }

                // Paywall Bottom Sheet ($1/month micro-subscription)
                if (state.isPaywallOpen) {
                    PaywallBottomSheet(
                        billingManager = viewModel.billingManager,
                        onDismiss = { viewModel.onIntent(ChatIntent.SetPaywallOpen(false)) },
                        onOpenAdminTrigger = { viewModel.onIntent(ChatIntent.SetAdminPanelOpen(true)) }
                    )
                }

                // Hidden Cryptographic Admin Panel ("God Mode")
                if (state.isAdminPanelOpen) {
                    AdminPanelDialog(
                        adminManager = viewModel.adminManager,
                        billingManager = viewModel.billingManager,
                        aggregator = viewModel.autonomousAggregator,
                        onDismiss = { viewModel.onIntent(ChatIntent.SetAdminPanelOpen(false)) }
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedMessageItem(message: ChatMessage, currentModel: String) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(250)) + slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) { it / 4 }
    ) {
        MessageRow(message = message, currentModel = currentModel)
    }
}

/**
 * Top App Bar featuring Glassmorphic backdrop and the Dynamic Orchestration Pill ("Magic UI").
 */
@Composable
fun ChatTopBar(
    state: ChatUiState,
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Glassmorphism translucent backdrop
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(DarkCanvas.copy(alpha = 0.90f))
            .border(width = 0.5.dp, color = DarkBorder.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sidebar Drawer Toggle
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "History Drawer",
                    tint = TextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Dynamic Orchestration Indicator Pill ("Magic UI")
            DynamicOrchestrationHeader(
                state = state,
                onClick = { viewModel.onIntent(ChatIntent.SetModelSwitcherOpen(true)) }
            )

            // Dropdown menu to switch providers and models
            DropdownMenu(
                expanded = state.isModelSwitcherOpen,
                onDismissRequest = { viewModel.onIntent(ChatIntent.SetModelSwitcherOpen(false)) },
                modifier = Modifier
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            ) {
                LlmProvider.entries.forEach { provider ->
                    Text(
                        text = provider.displayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (provider == LlmProvider.ATRIA_ASI) AccentIndigo else AccentCyan,
                            letterSpacing = 0.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    provider.availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model,
                                        color = if (model == state.selectedModel) AccentCyan else TextPrimary,
                                        fontWeight = if (model == state.selectedModel) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (model == "Atria-Dawn-Preview") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "PRIMARY",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = AccentIndigo,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.onIntent(ChatIntent.SwitchProvider(provider))
                                viewModel.onIntent(ChatIntent.SwitchModel(model))
                            }
                        )
                    }
                }
            }

            // Action Buttons (Latency Radar + Free Models Radar + Key Vault)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = { viewModel.onIntent(ChatIntent.SetLatencyMonitorOpen(true)) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Gateway Latency Radar",
                        tint = AccentAmber,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.onIntent(ChatIntent.SetFreeModelsRadarOpen(true)) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Free Models Radar",
                        tint = AccentEmerald,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Key Vault",
                        tint = if (state.isKeyMissing) AccentRose else AccentCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dynamic Orchestration Pill showing real-time routing status:
 * Text (Atria ASI) vs Vision Bypass (Gemini) vs Circuit Breaker Failover.
 */
@Composable
fun DynamicOrchestrationHeader(
    state: ChatUiState,
    onClick: () -> Unit
) {
    val isVisionBypass = state.attachedImageUri != null || state.isVisionBypassActive
    val hasFallback = state.activeFallbackEvent != null

    val pillBorderColor by animateColorAsState(
        targetValue = when {
            hasFallback -> AccentAmber
            isVisionBypass -> AccentEmerald
            state.selectedProvider == LlmProvider.ATRIA_ASI -> AccentIndigo.copy(alpha = 0.6f)
            else -> DarkBorder
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    )

    val beaconColor = when {
        hasFallback -> AccentAmber
        isVisionBypass -> AccentEmerald
        state.selectedProvider == LlmProvider.ATRIA_ASI -> AccentIndigo
        else -> AccentCyan
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val beaconScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, pillBorderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing status beacon dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(if (isVisionBypass || hasFallback) beaconScale else 1f)
                .clip(CircleShape)
                .background(beaconColor)
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Dynamic Label text with smooth fading
        Text(
            text = when {
                hasFallback -> "Failover: ${state.activeFallbackEvent?.toModel?.take(18)}"
                isVisionBypass -> "Vision: Gemini Flash Free"
                state.selectedProvider == LlmProvider.ATRIA_ASI -> "Atria ASI (Dawn 744B)"
                else -> state.selectedModel
            },
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                fontSize = 12.5.sp
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = "Switch",
            tint = TextSecondary,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
fun CircuitBreakerFallbackBanner(
    event: FallbackEvent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(event) {
        HapticFeedbackHelper.performHaptic(view, haptic, HapticInteraction.CIRCUIT_BREAKER_FALLBACK)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated.copy(alpha = 0.95f))
            .border(1.dp, AccentAmber.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentAmber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Circuit Breaker Activated (Zero Interruption)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentAmber
                        )
                    )
                    Text(
                        text = "Rate limit on ${event.fromModel} → Auto-switched to ${event.toModel}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                    )
                }
            }

            IconButton(
                onClick = {
                    HapticFeedbackHelper.performHaptic(view, haptic, HapticInteraction.STANDARD_CLICK)
                    onDismiss()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
            }
        }
    }
}

@Composable
fun MessageRow(message: ChatMessage, currentModel: String) {
    val isUser = message.role == MessageRole.USER
    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentModel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
            )
        }

        if (message.toolExecution != null) {
            ToolExecutionPill(
                execution = message.toolExecution,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth(0.95f)
            )
        }

        if (message.content.isNotBlank()) {
            if (isUser) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                        .background(BubbleUser)
                        .border(1.dp, BubbleUserBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    HalakouMarkdown(
                        content = message.content,
                        textColor = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveToolExecutingCard(toolName: String, args: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, AccentAmber.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = AccentAmber
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Agent triggering '$toolName'...",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber
                    )
                )
                if (args.isNotBlank()) {
                    Text(
                        text = args,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun GeneratingPulseIndicator(modelName: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 2.dp,
            color = AccentCyan
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$modelName thinking...",
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
        )
    }
}

@Composable
fun EmptyChatGreeting(
    selectedProvider: LlmProvider,
    selectedModel: String,
    onOpenRadar: () -> Unit,
    onPromptClick: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(AccentCyan.copy(alpha = 0.25f), AccentPurple.copy(alpha = 0.1f), Color.Transparent)
                        )
                    )
                    .border(1.5.dp, AccentCyan, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "halakou",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Text(
                text = "Autonomous AI Orchestrator • Zero-Friction Circuit Breaker",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Primary: Atria ASI (Dawn)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentIndigo,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentEmerald.copy(alpha = 0.12f))
                        .clickable { onOpenRadar() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "📡 Free Models Radar",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val prompts = listOf(
                    "👁️ Attach photo to auto-route to Free Vision Model",
                    "⚡ Ask Atria-Dawn-Preview (744B MoE) to architect an API",
                    "🔍 Search web for latest Kotlin news & releases",
                    "🧮 Calculate (128 * 4.5) + sqrt(144) / 2"
                )
                prompts.forEach { p ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                if (p.contains("Vision Model")) {
                                    onOpenRadar()
                                } else {
                                    onPromptClick(p)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = p,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating glassmorphic composer bar with photo picker and haptic send feedback.
 */
@Composable
fun ChatComposerBar(
    state: ChatUiState,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            HapticFeedbackHelper.performHaptic(view, haptic, HapticInteraction.STANDARD_CLICK)
            viewModel.onIntent(ChatIntent.AttachImage(uri.toString()))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkCanvas.copy(alpha = 0.92f))
            .border(width = 0.5.dp, color = DarkBorder.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Image attachment preview badge if present
        if (state.attachedImageUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentEmerald.copy(alpha = 0.15f))
                        .border(1.dp, AccentEmerald.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RemoveRedEye,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Vision Mode: Auto-Routing image to Free Vision Model",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint = AccentEmerald,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable {
                                    HapticFeedbackHelper.performHaptic(view, haptic, HapticInteraction.STANDARD_CLICK)
                                    viewModel.onIntent(ChatIntent.RemoveAttachedImage)
                                }
                        )
                    }
                }
            }
        }

        // Quick status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.isToolsEnabled) AccentCyan.copy(alpha = 0.15f) else DarkSurfaceElevated)
                        .clickable {
                            HapticFeedbackHelper.performHaptic(view, haptic, HapticInteraction.STANDARD_CLICK)
                            viewModel.onIntent(ChatIntent.ToggleToolsGlobal(!state.isToolsEnabled))
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Agentic Tools",
                            tint = if (state.isToolsEnabled) AccentCyan else TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.isToolsEnabled) "Tools ON" else "Tools OFF",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (state.isToolsEnabled) AccentCyan else TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Text(
                text = "Circuit Breaker Active",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = AccentEmerald.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Input row with Photo Attachment Picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo Picker Button
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Attach image for vision model",
                    tint = if (state.attachedImageUri != null) AccentEmerald else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.onIntent(ChatIntent.UpdateInputText(it)) },
                placeholder = {
                    Text(
                        if (state.attachedImageUri != null) "Ask anything about attached image..."
                        else "Message halakou (Default: Atria ASI)...",
                        color = TextTertiary,
                        fontSize = 14.sp
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    HapticFeedbackHelper.performHaptic(view, haptic, HapticInteraction.SEND_MESSAGE)
                    viewModel.onIntent(ChatIntent.SendMessage)
                },
                enabled = (state.inputText.isNotBlank() || state.attachedImageUri != null) && !state.isGenerating,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if ((state.inputText.isNotBlank() || state.attachedImageUri != null) && !state.isGenerating) AccentCyan
                        else DarkSurfaceElevated
                    )
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (state.inputText.isNotBlank() || state.attachedImageUri != null) Color.Black else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorNotificationBanner(
    message: String,
    isKeyMissing: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .border(1.dp, AccentRose.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isKeyMissing) "API Key Missing" else "Request Error",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = AccentRose
                    )
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isKeyMissing) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentCyan)
                            .clickable { onOpenSettings() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Key Vault",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                }
            }
        }
    }
}
