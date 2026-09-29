package com.example.halakou.presentation.mvi

import com.example.halakou.domain.billing.SubscriptionStatus
import com.example.halakou.domain.model.ChatMessage
import com.example.halakou.domain.model.ChatSession
import com.example.halakou.domain.model.FreeModelCategory
import com.example.halakou.domain.model.FreeModelInfo
import com.example.halakou.domain.model.LlmProvider
import com.example.halakou.domain.model.ModelSettings
import com.example.halakou.domain.orchestrator.FallbackEvent
import com.example.halakou.domain.tools.AgentTool

data class ChatUiState(
    val currentSessionId: String? = null,
    val sessions: List<ChatSession> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val selectedProvider: LlmProvider = LlmProvider.ATRIA_ASI, // Primary default text engine
    val selectedModel: String = LlmProvider.ATRIA_ASI.defaultModel,
    val modelSettings: ModelSettings = ModelSettings(),
    val inputText: String = "",
    val attachedImageUri: String? = null,
    val isVisionBypassActive: Boolean = false,
    val isGenerating: Boolean = false,
    val isToolsEnabled: Boolean = true,
    val activeToolName: String? = null,
    val activeToolArgs: String? = null,
    val activeFallbackEvent: FallbackEvent? = null,
    val errorMessage: String? = null,
    val isKeyMissing: Boolean = false,
    val isHardwareBackedKeyStore: Boolean = true,
    val availableTools: List<AgentTool> = emptyList(),
    val enabledToolNames: Set<String> = emptySet(),
    val isDrawerOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isModelSwitcherOpen: Boolean = false,
    val isFreeModelsRadarOpen: Boolean = false,
    val isAdminPanelOpen: Boolean = false,
    val isPaywallOpen: Boolean = false,
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE_TIER,
    val dailyRequestsUsed: Int = 0,
    val freeModels: List<FreeModelInfo> = emptyList(),
    val isScanningFreeModels: Boolean = false,
    val exportedMarkdown: String? = null
)

sealed interface ChatIntent {
    data class UpdateInputText(val text: String) : ChatIntent
    data class AttachImage(val uri: String) : ChatIntent
    data object RemoveAttachedImage : ChatIntent
    data object SendMessage : ChatIntent
    data class SelectSession(val sessionId: String) : ChatIntent
    data object CreateNewSession : ChatIntent
    data class DeleteSession(val sessionId: String) : ChatIntent
    data class RenameSession(val sessionId: String, val newTitle: String) : ChatIntent
    data class SwitchProvider(val provider: LlmProvider) : ChatIntent
    data class SwitchModel(val modelId: String) : ChatIntent
    data class SaveApiKey(val provider: LlmProvider, val key: String) : ChatIntent
    data class SaveBaseUrl(val provider: LlmProvider, val url: String) : ChatIntent
    data class UpdateModelSettings(val settings: ModelSettings) : ChatIntent
    data class ToggleTool(val toolName: String) : ChatIntent
    data class ToggleToolsGlobal(val enabled: Boolean) : ChatIntent
    data class ExportMarkdown(val sessionId: String) : ChatIntent
    data object ClearExportedMarkdown : ChatIntent
    data object ClearError : ChatIntent
    data object DismissFallbackBanner : ChatIntent
    data class SetDrawerOpen(val isOpen: Boolean) : ChatIntent
    data class SetSettingsOpen(val isOpen: Boolean) : ChatIntent
    data class SetModelSwitcherOpen(val isOpen: Boolean) : ChatIntent
    data class SetFreeModelsRadarOpen(val isOpen: Boolean) : ChatIntent
    data class SetAdminPanelOpen(val isOpen: Boolean) : ChatIntent
    data class SetPaywallOpen(val isOpen: Boolean) : ChatIntent
    data object ScanFreeModels : ChatIntent
    data class AutoSetFreeModel(val category: FreeModelCategory) : ChatIntent
    data class SelectFreeModel(val model: FreeModelInfo) : ChatIntent
}

sealed interface ChatSideEffect {
    data class ShowToast(val message: String) : ChatSideEffect
    data object ScrollToBottom : ChatSideEffect
    data object TriggerHaptic : ChatSideEffect
}
