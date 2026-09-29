package com.example.halakou.domain.model

enum class LlmProvider(
    val id: String,
    val displayName: String,
    val tagline: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val defaultBaseUrl: String,
    val placeholderKey: String,
    val keyDocsUrl: String,
    val isLocal: Boolean = false
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        tagline = "Ultra-fast multimodal reasoning & Vision (Free Tier)",
        defaultModel = "gemini-2.5-flash",
        availableModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        ),
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        placeholderKey = "AIzaSy...",
        keyDocsUrl = "https://aistudio.google.com/app/apikey"
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter (Free Catalog)",
        tagline = "Auto-aggregates 100% free Vision, Reasoning & Coding models",
        defaultModel = "google/gemini-2.0-flash-exp:free",
        availableModels = listOf(
            "google/gemini-2.0-flash-exp:free",
            "meta-llama/llama-3.2-11b-vision-instruct:free",
            "deepseek/deepseek-r1:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "qwen/qwen-2.5-coder-32b-instruct:free",
            "mistralai/mistral-7b-instruct:free"
        ),
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        placeholderKey = "sk-or-v1-...",
        keyDocsUrl = "https://openrouter.ai/keys"
    ),
    ATRIA_ASI(
        id = "atria",
        displayName = "Atria ASI (Dawn)",
        tagline = "744B agentic MoE model with deep reasoning & tool mastery",
        defaultModel = "Atria-Dawn-Preview",
        availableModels = listOf(
            "Atria-Dawn-Preview",
            "Atria-Dawn"
        ),
        defaultBaseUrl = "https://api.atria-asi.ai/v1",
        placeholderKey = "atria-...",
        keyDocsUrl = "https://api.atria-asi.ai/"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        tagline = "Flagship reasoning & omni intelligence",
        defaultModel = "gpt-4o",
        availableModels = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "o1-preview",
            "o1-mini"
        ),
        defaultBaseUrl = "https://api.openai.com/v1",
        placeholderKey = "sk-proj-...",
        keyDocsUrl = "https://platform.openai.com/api-keys"
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic Claude",
        tagline = "Nuanced writing & exceptional coding capabilities",
        defaultModel = "claude-3-5-sonnet-20241022",
        availableModels = listOf(
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229"
        ),
        defaultBaseUrl = "https://api.anthropic.com/v1",
        placeholderKey = "sk-ant-api03-...",
        keyDocsUrl = "https://console.anthropic.com/settings/keys"
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        tagline = "High performance open-weights reasoning & coding",
        defaultModel = "deepseek-chat",
        availableModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        ),
        defaultBaseUrl = "https://api.deepseek.com/v1",
        placeholderKey = "sk-...",
        keyDocsUrl = "https://platform.deepseek.com/api_keys"
    ),
    OLLAMA(
        id = "ollama",
        displayName = "Ollama (Local Network)",
        tagline = "100% private, self-hosted on your home network or rig",
        defaultModel = "llama3:latest",
        availableModels = listOf(
            "llama3:latest",
            "llava:latest",
            "mistral:latest",
            "qwen2.5-coder:latest",
            "deepseek-r1:latest",
            "phi3:latest"
        ),
        defaultBaseUrl = "http://10.0.2.2:11434",
        placeholderKey = "no-key-required",
        keyDocsUrl = "https://ollama.com",
        isLocal = true
    );

    companion object {
        fun fromId(id: String): LlmProvider = entries.find { it.id.equals(id, ignoreCase = true) } ?: GEMINI
    }
}
