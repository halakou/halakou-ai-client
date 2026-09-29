package com.example.halakou.presentation.mvi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.halakou.data.local.ChatHistoryRepository
import com.example.halakou.data.local.HalakouDatabase
import com.example.halakou.data.remote.ConfigRepository
import com.example.halakou.data.remote.FreeModelDiscoveryService
import com.example.halakou.data.remote.LlmGateway
import com.example.halakou.data.security.AdminAuthenticator
import com.example.halakou.data.security.AdminManager
import com.example.halakou.data.security.SecureVaultRepository
import com.example.halakou.domain.billing.BillingManager
import com.example.halakou.domain.billing.SubscriptionStatus
import com.example.halakou.domain.model.ChatMessage
import com.example.halakou.domain.model.FreeModelCategory
import com.example.halakou.domain.model.FreeModelInfo
import com.example.halakou.domain.model.LlmProvider
import com.example.halakou.domain.model.MessageRole
import com.example.halakou.domain.model.ModelSettings
import com.example.halakou.domain.model.ToolExecution
import com.example.halakou.domain.orchestrator.AutonomousAggregator
import com.example.halakou.domain.orchestrator.CircuitBreaker
import com.example.halakou.domain.orchestrator.LatencyMonitor
import com.example.halakou.domain.orchestrator.ModelOrchestrator
import com.example.halakou.domain.orchestrator.OrchestratedEndpoint
import com.example.halakou.domain.orchestrator.TaskRouter
import com.example.halakou.domain.tools.ToolRegistry
import com.example.halakou.presentation.util.HapticInteraction
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val vaultRepo = SecureVaultRepository(application)
    private val database = HalakouDatabase.getInstance(application)
    private val chatRepo = ChatHistoryRepository(database.chatDao())
    private val llmGateway = LlmGateway()
    private val toolRegistry = ToolRegistry()
    private val freeDiscoveryService = FreeModelDiscoveryService()

    // Autonomous AI Orchestration Engines
    val circuitBreaker = CircuitBreaker()
    val autonomousAggregator = AutonomousAggregator()
    val latencyMonitor = LatencyMonitor()
    val taskRouter = TaskRouter(autonomousAggregator, circuitBreaker)
    val orchestrator = ModelOrchestrator(
        autonomousAggregator,
        circuitBreaker,
        taskRouter,
        llmGateway,
        vaultRepo,
        latencyMonitor
    )

    // Security & Commercial Monetization
    val adminAuthenticator = AdminAuthenticator(application)
    val adminManager = AdminManager(application)
    val billingManager = BillingManager(application, adminAuthenticator)
    val configRepo = ConfigRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<ChatSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    private var messagesJob: Job? = null

    init {
        // Synchronize initial cached/bundled config.json
        autonomousAggregator.syncFromRemoteConfig(configRepo.configState.value)

        // Strictly set primary text engine to Atria ASI (Dawn)
        val initialProvider = LlmProvider.ATRIA_ASI
        val enabledTools = toolRegistry.registeredTools
            .filter { vaultRepo.isToolEnabled(it.name) }
            .map { it.name }
            .toSet()

        _uiState.update {
            it.copy(
                selectedProvider = initialProvider,
                selectedModel = initialProvider.defaultModel,
                modelSettings = vaultRepo.getModelSettings(initialProvider),
                availableTools = toolRegistry.registeredTools,
                enabledToolNames = enabledTools,
                isHardwareBackedKeyStore = vaultRepo.isHardwareBacked(),
                freeModels = freeDiscoveryService.curatedFreeModels
            )
        }

        // Initialize Latency Monitor with endpoints
        latencyMonitor.initializeEndpoints(
            endpoints = autonomousAggregator.getAllEndpoints(),
            activeEndpointKey = "atria-dawn-primary"
        )

        observeSessions()
        scanFreeModelsInternal()
        observeFallbackEvents()
        observeBillingState()
        observeLatencyMonitor()
        fetchRemoteEndpointsInBackground()
    }

    private fun observeLatencyMonitor() {
        viewModelScope.launch {
            latencyMonitor.rankedGateways.collect { ranked ->
                _uiState.update { it.copy(rankedGateways = ranked) }
            }
        }
        viewModelScope.launch {
            latencyMonitor.isBenchmarking.collect { benchmarking ->
                _uiState.update { it.copy(isBenchmarkingLatency = benchmarking) }
            }
        }
    }

    private fun fetchRemoteEndpointsInBackground() {
        viewModelScope.launch {
            val customUrl = adminAuthenticator.customConfigUrl.value.ifBlank { null }
            val refreshed = configRepo.refreshRemoteConfig(customUrl)
            if (refreshed) {
                autonomousAggregator.syncFromRemoteConfig(configRepo.configState.value)
                latencyMonitor.initializeEndpoints(
                    autonomousAggregator.getAllEndpoints(),
                    "${_uiState.value.selectedProvider.id}-${_uiState.value.selectedModel}"
                )
            }
        }
    }

    private fun observeBillingState() {
        viewModelScope.launch {
            billingManager.billingState.collect { bill ->
                _uiState.update {
                    it.copy(
                        subscriptionStatus = bill.status,
                        dailyRequestsUsed = bill.dailyRequestsUsed
                    )
                }
            }
        }
    }

    private fun observeFallbackEvents() {
        viewModelScope.launch {
            orchestrator.fallbackEvents.collect { fallback ->
                _uiState.update { it.copy(activeFallbackEvent = fallback) }
                _sideEffect.send(
                    ChatSideEffect.ShowToast(
                        "Circuit Breaker: Auto-routed from ${fallback.fromModel} to ${fallback.toModel}"
                    )
                )
                _sideEffect.send(ChatSideEffect.TriggerHaptic(HapticInteraction.CIRCUIT_BREAKER_FALLBACK))
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            chatRepo.allSessions.collect { sessionsList ->
                _uiState.update { state ->
                    val activeId = if (state.currentSessionId == null && sessionsList.isNotEmpty()) {
                        sessionsList.first().id
                    } else state.currentSessionId
                    state.copy(sessions = sessionsList, currentSessionId = activeId)
                }

                val currentId = _uiState.value.currentSessionId
                if (currentId != null) {
                    subscribeToMessages(currentId)
                } else if (sessionsList.isEmpty()) {
                    createNewSessionInternal("New Chat")
                }
            }
        }
    }

    private fun subscribeToMessages(sessionId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepo.getMessagesForSession(sessionId).collect { msgList ->
                _uiState.update { it.copy(messages = msgList) }
                _sideEffect.send(ChatSideEffect.ScrollToBottom)
            }
        }
    }

    private fun scanFreeModelsInternal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningFreeModels = true) }
            autonomousAggregator.probeEndpointsHealth()
            val ollamaUrl = vaultRepo.getBaseUrl(LlmProvider.OLLAMA)
            val discovered = freeDiscoveryService.discoverAllFreeModels(ollamaUrl)
            _uiState.update {
                it.copy(
                    freeModels = discovered,
                    isScanningFreeModels = false
                )
            }
        }
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.UpdateInputText -> {
                _uiState.update { it.copy(inputText = intent.text) }
            }
            is ChatIntent.AttachImage -> {
                _uiState.update { it.copy(attachedImageUri = intent.uri, isVisionBypassActive = true) }
            }
            is ChatIntent.RemoveAttachedImage -> {
                _uiState.update { it.copy(attachedImageUri = null, isVisionBypassActive = false) }
            }
            is ChatIntent.DismissFallbackBanner -> {
                _uiState.update { it.copy(activeFallbackEvent = null) }
            }
            is ChatIntent.SendMessage -> sendMessage()
            is ChatIntent.SelectSession -> {
                _uiState.update { it.copy(currentSessionId = intent.sessionId, isDrawerOpen = false) }
                subscribeToMessages(intent.sessionId)
            }
            is ChatIntent.CreateNewSession -> {
                createNewSessionInternal("New Chat")
                _uiState.update { it.copy(isDrawerOpen = false) }
            }
            is ChatIntent.DeleteSession -> {
                viewModelScope.launch {
                    chatRepo.deleteSession(intent.sessionId)
                    if (_uiState.value.currentSessionId == intent.sessionId) {
                        _uiState.update { it.copy(currentSessionId = null, messages = emptyList()) }
                    }
                }
            }
            is ChatIntent.RenameSession -> {
                viewModelScope.launch {
                    chatRepo.updateSessionTitle(intent.sessionId, intent.newTitle)
                }
            }
            is ChatIntent.SwitchProvider -> {
                val newProvider = intent.provider
                val settings = vaultRepo.getModelSettings(newProvider)
                _uiState.update {
                    it.copy(
                        selectedProvider = newProvider,
                        selectedModel = newProvider.defaultModel,
                        modelSettings = settings,
                        isKeyMissing = !vaultRepo.hasApiKey(newProvider)
                    )
                }
            }
            is ChatIntent.SwitchModel -> {
                _uiState.update { it.copy(selectedModel = intent.modelId, isModelSwitcherOpen = false) }
            }
            is ChatIntent.SaveApiKey -> {
                vaultRepo.saveApiKey(intent.provider, intent.key)
                _uiState.update { it.copy(isKeyMissing = !vaultRepo.hasApiKey(it.selectedProvider)) }
                viewModelScope.launch {
                    _sideEffect.send(ChatSideEffect.ShowToast("${intent.provider.displayName} API Key securely stored"))
                }
            }
            is ChatIntent.SaveBaseUrl -> {
                vaultRepo.setBaseUrl(intent.provider, intent.url)
                viewModelScope.launch {
                    _sideEffect.send(ChatSideEffect.ShowToast("Base URL updated for ${intent.provider.displayName}"))
                }
            }
            is ChatIntent.UpdateModelSettings -> {
                vaultRepo.saveModelSettings(_uiState.value.selectedProvider, intent.settings)
                _uiState.update { it.copy(modelSettings = intent.settings) }
            }
            is ChatIntent.ToggleTool -> {
                val current = _uiState.value.enabledToolNames.toMutableSet()
                val isNowEnabled = if (current.contains(intent.toolName)) {
                    current.remove(intent.toolName)
                    false
                } else {
                    current.add(intent.toolName)
                    true
                }
                vaultRepo.setToolEnabled(intent.toolName, isNowEnabled)
                _uiState.update { it.copy(enabledToolNames = current) }
            }
            is ChatIntent.ToggleToolsGlobal -> {
                _uiState.update { it.copy(isToolsEnabled = intent.enabled) }
            }
            is ChatIntent.ExportMarkdown -> {
                viewModelScope.launch {
                    val md = chatRepo.exportSessionAsMarkdown(intent.sessionId)
                    _uiState.update { it.copy(exportedMarkdown = md) }
                }
            }
            is ChatIntent.ClearExportedMarkdown -> {
                _uiState.update { it.copy(exportedMarkdown = null) }
            }
            is ChatIntent.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is ChatIntent.SetDrawerOpen -> {
                _uiState.update { it.copy(isDrawerOpen = intent.isOpen) }
            }
            is ChatIntent.SetSettingsOpen -> {
                _uiState.update { it.copy(isSettingsOpen = intent.isOpen) }
            }
            is ChatIntent.SetModelSwitcherOpen -> {
                _uiState.update { it.copy(isModelSwitcherOpen = intent.isOpen) }
            }
            is ChatIntent.SetFreeModelsRadarOpen -> {
                _uiState.update { it.copy(isFreeModelsRadarOpen = intent.isOpen) }
            }
            is ChatIntent.SetLatencyMonitorOpen -> {
                _uiState.update { it.copy(isLatencyMonitorOpen = intent.isOpen) }
            }
            is ChatIntent.SetAdminPanelOpen -> {
                _uiState.update { it.copy(isAdminPanelOpen = intent.isOpen) }
            }
            is ChatIntent.SetPaywallOpen -> {
                _uiState.update { it.copy(isPaywallOpen = intent.isOpen) }
            }
            is ChatIntent.ScanFreeModels -> {
                scanFreeModelsInternal()
            }
            is ChatIntent.BenchmarkGateways -> {
                viewModelScope.launch {
                    val activeKey = "${_uiState.value.selectedProvider.id}-${_uiState.value.selectedModel}"
                    latencyMonitor.benchmarkAll(autonomousAggregator.getAllEndpoints(), activeKey)
                    _sideEffect.send(ChatSideEffect.ShowToast("⚡ Gateway Benchmark Complete"))
                }
            }
            is ChatIntent.AutoSetFreeModel -> {
                autoConfigureFreePreset(intent.category)
            }
            is ChatIntent.SelectFreeModel -> {
                val model = intent.model
                _uiState.update {
                    it.copy(
                        selectedProvider = model.provider,
                        selectedModel = model.id,
                        isFreeModelsRadarOpen = false,
                        isKeyMissing = !vaultRepo.hasApiKey(model.provider)
                    )
                }
                viewModelScope.launch {
                    _sideEffect.send(ChatSideEffect.ShowToast("Selected Free Model: ${model.name}"))
                }
            }
            is ChatIntent.SelectRankedGateway -> {
                val stats = intent.gateway.stats
                val newProvider = stats.provider
                val settings = vaultRepo.getModelSettings(newProvider)
                _uiState.update {
                    it.copy(
                        selectedProvider = newProvider,
                        selectedModel = stats.modelId,
                        modelSettings = settings,
                        isLatencyMonitorOpen = false,
                        isKeyMissing = !vaultRepo.hasApiKey(newProvider)
                    )
                }
                latencyMonitor.recalculateRankings(stats.endpointKey)
                viewModelScope.launch {
                    _sideEffect.send(
                        ChatSideEffect.ShowToast("⚡ Switched to ${stats.modelId} (${stats.averageLatencyMs}ms)")
                    )
                }
            }
        }
    }

    private fun autoConfigureFreePreset(category: FreeModelCategory) {
        val models = _uiState.value.freeModels
        val target = when (category) {
            FreeModelCategory.VISION -> freeDiscoveryService.findBestVisionModel(models)
            FreeModelCategory.REASONING -> freeDiscoveryService.findBestReasoningModel(models)
            FreeModelCategory.CODING -> freeDiscoveryService.findBestCodingModel(models)
            FreeModelCategory.GENERAL -> models.find { it.category == FreeModelCategory.GENERAL } ?: models.firstOrNull()
        }

        if (target != null) {
            _uiState.update {
                it.copy(
                    selectedProvider = target.provider,
                    selectedModel = target.id,
                    isFreeModelsRadarOpen = false,
                    isKeyMissing = !vaultRepo.hasApiKey(target.provider)
                )
            }
            viewModelScope.launch {
                _sideEffect.send(ChatSideEffect.ShowToast("Auto-configured ${category.displayName}: ${target.name}"))
            }
        }
    }

    private fun createNewSessionInternal(title: String) {
        viewModelScope.launch {
            val provider = _uiState.value.selectedProvider
            val modelId = _uiState.value.selectedModel
            val session = chatRepo.createSession(title, provider, modelId)
            _uiState.update { it.copy(currentSessionId = session.id) }
            subscribeToMessages(session.id)
        }
    }

    private fun sendMessage() {
        val currentState = _uiState.value
        val text = currentState.inputText.trim()
        val attachedImage = currentState.attachedImageUri
        if (text.isBlank() || currentState.isGenerating) return

        // Commercial Quota Check: If limit reached and not subscribed / not admin, present Paywall
        if (!billingManager.canExecuteRequest()) {
            _uiState.update { it.copy(isPaywallOpen = true) }
            viewModelScope.launch {
                _sideEffect.send(
                    ChatSideEffect.ShowToast("Free daily limit (30/30) reached. Upgrade for $1/mo or enter Admin key!")
                )
            }
            return
        }

        val sessionId = currentState.currentSessionId ?: run {
            createNewSessionInternal("New Chat")
            return
        }

        val activeSession = currentState.sessions.find { it.id == sessionId }
        if (activeSession?.title == "New Chat" || currentState.messages.isEmpty()) {
            val autoTitle = if (text.length > 30) text.take(30) + "..." else text
            viewModelScope.launch {
                chatRepo.updateSessionTitle(sessionId, autoTitle)
            }
        }

        val userMessageContent = if (attachedImage != null) {
            "[Attached Image: $attachedImage]\n$text"
        } else text

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = MessageRole.USER,
            content = userMessageContent,
            timestamp = System.currentTimeMillis()
        )

        billingManager.recordRequestUsage()

        _uiState.update {
            it.copy(
                inputText = "",
                attachedImageUri = null,
                isVisionBypassActive = false,
                isGenerating = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            _sideEffect.send(ChatSideEffect.TriggerHaptic(HapticInteraction.SEND_MESSAGE))
            chatRepo.saveMessage(userMessage)

            val pastMessages = chatRepo.getMessagesForSessionSync(sessionId)
            val windowSize = currentState.modelSettings.contextWindowMessages
            val contextMessages = if (pastMessages.size > windowSize) {
                pastMessages.takeLast(windowSize)
            } else pastMessages

            val toolsPrompt = if (currentState.isToolsEnabled) {
                toolRegistry.buildToolSystemPrompt(currentState.enabledToolNames)
            } else ""

            val fullSystemPrompt = currentState.modelSettings.systemPrompt + toolsPrompt

            // Run through the Autonomous ModelOrchestrator with TaskRouter + Circuit Breaker failover
            val orchestratorResult = orchestrator.execute(
                userPrompt = text,
                hasImageAttachments = attachedImage != null,
                messages = contextMessages,
                settings = currentState.modelSettings,
                systemPromptWithTools = fullSystemPrompt
            )

            orchestratorResult.fold(
                onSuccess = { response ->
                    if (response.wasFallback && response.fallbackEvent != null) {
                        _uiState.update { it.copy(activeFallbackEvent = response.fallbackEvent) }
                    }
                    handleLlmResponse(
                        sessionId = sessionId,
                        responseText = response.content,
                        priorContext = contextMessages,
                        usedEndpoint = response.endpointUsed,
                        systemPrompt = fullSystemPrompt
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            errorMessage = error.localizedMessage ?: "Failed to generate response."
                        )
                    }
                }
            )
        }
    }

    private suspend fun handleLlmResponse(
        sessionId: String,
        responseText: String,
        priorContext: List<ChatMessage>,
        usedEndpoint: OrchestratedEndpoint,
        systemPrompt: String
    ) {
        val toolInvocation = if (_uiState.value.isToolsEnabled) {
            toolRegistry.parseToolCall(responseText)
        } else null

        if (toolInvocation != null) {
            _uiState.update {
                it.copy(
                    activeToolName = toolInvocation.toolName,
                    activeToolArgs = toolInvocation.arguments.toString()
                )
            }

            val toolInstance = toolRegistry.getTool(toolInvocation.toolName)
            val toolResult = if (toolInstance != null) {
                toolInstance.execute(toolInvocation.arguments)
            } else {
                com.example.halakou.domain.tools.ToolResult(
                    output = "Tool '${toolInvocation.toolName}' is not registered.",
                    isSuccess = false
                )
            }

            if (toolResult.isSuccess) {
                _sideEffect.send(ChatSideEffect.TriggerHaptic(HapticInteraction.TOOL_SUCCESS))
            } else {
                _sideEffect.send(ChatSideEffect.TriggerHaptic(HapticInteraction.ERROR_ALERT))
            }

            val toolMessageId = UUID.randomUUID().toString()
            val intermediateMsg = ChatMessage(
                id = toolMessageId,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = responseText.replace(toolInvocation.rawBlock, "").trim(),
                toolExecution = ToolExecution(
                    toolName = toolInvocation.toolName,
                    arguments = toolInvocation.arguments.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                    result = toolResult.output,
                    isRunning = false,
                    isError = !toolResult.isSuccess
                ),
                timestamp = System.currentTimeMillis()
            )
            chatRepo.saveMessage(intermediateMsg)

            val toolResultMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = MessageRole.USER,
                content = "[TOOL EXECUTION RESULT FOR '${toolInvocation.toolName}']:\n${toolResult.output}\n\nPlease synthesize the answer cleanly for the user based on this tool result.",
                timestamp = System.currentTimeMillis()
            )

            val updatedContext = priorContext + intermediateMsg + toolResultMessage

            val finalCall = llmGateway.generateResponse(
                provider = usedEndpoint.provider,
                modelId = usedEndpoint.modelId,
                apiKey = vaultRepo.getApiKey(usedEndpoint.provider),
                baseUrl = usedEndpoint.baseUrl,
                messages = updatedContext,
                settings = _uiState.value.modelSettings,
                systemPromptWithTools = systemPrompt
            )

            finalCall.fold(
                onSuccess = { finalAnswer ->
                    val finalMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = finalAnswer,
                        timestamp = System.currentTimeMillis()
                    )
                    chatRepo.saveMessage(finalMsg)
                    _uiState.update { it.copy(isGenerating = false, activeToolName = null, activeToolArgs = null) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            activeToolName = null,
                            activeToolArgs = null,
                            errorMessage = "Synthesis error after tool run: ${err.localizedMessage}"
                        )
                    }
                }
            )
        } else {
            val assistantMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = responseText,
                timestamp = System.currentTimeMillis()
            )
            chatRepo.saveMessage(assistantMsg)
            _uiState.update { it.copy(isGenerating = false, activeToolName = null, activeToolArgs = null) }
        }
    }

    fun getStoredApiKey(provider: LlmProvider): String = vaultRepo.getApiKey(provider)
    fun getStoredBaseUrl(provider: LlmProvider): String = vaultRepo.getBaseUrl(provider)
}
