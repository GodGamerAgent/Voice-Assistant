package com.example.data.model

import java.util.UUID

/**
 * A fallback API endpoint for an AI model category.
 */
data class FallbackEndpoint(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Fallback API",
    val baseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val enabled: Boolean = true
)

/**
 * Quick preset template to pre-fill main or fallback endpoints with
 * recommended models, documentation links, and generation defaults.
 */
data class ProviderPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val apiKey: String = "",
    val category: String = "all", // "all", "extractor", "summarizer", "reply", "stt"
    val tag: String = "",
    val isCustom: Boolean = false,
    val docUrl: String = "",
    val suggestedTemperature: Double = 0.7,
    val suggestedMaxTokens: Int = 1000
)

/**
 * Category-specific AI model configuration.
 * Supports a primary endpoint + N fallback endpoints.
 */
data class ModelCategoryConfig(
    val categoryId: String, // "extractor", "summarizer", "reply"
    val displayName: String,
    val description: String,
    val enabled: Boolean = true,
    val primaryBaseUrl: String = "https://api.openai.com",
    val primaryApiKey: String = "",
    val primaryModel: String = "gpt-4o-mini",
    val fallbacks: List<FallbackEndpoint> = emptyList(),
    val maxSummaryWords: Int = 80 // Used primarily by Summarizer
) {
    companion object {
        const val CATEGORY_EXTRACTOR = "extractor"
        const val CATEGORY_SUMMARIZER = "summarizer"
        const val CATEGORY_REPLY = "reply"

        fun defaultExtractor(): ModelCategoryConfig = ModelCategoryConfig(
            categoryId = CATEGORY_EXTRACTOR,
            displayName = "Extractor",
            description = "Extracts chat messages from uploaded screenshots in '<Name>: <Chat>' format",
            enabled = true,
            primaryBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            primaryApiKey = "",
            primaryModel = "gemini-2.5-flash",
            fallbacks = emptyList()
        )

        fun defaultSummarizer(): ModelCategoryConfig = ModelCategoryConfig(
            categoryId = CATEGORY_SUMMARIZER,
            displayName = "Summarizer",
            description = "Compresses 8 chat messages into less than n words for the Relationship Summary",
            enabled = true,
            primaryBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            primaryApiKey = "",
            primaryModel = "gemini-2.5-flash",
            fallbacks = emptyList(),
            maxSummaryWords = 80
        )

        fun defaultReplyGenerator(): ModelCategoryConfig = ModelCategoryConfig(
            categoryId = CATEGORY_REPLY,
            displayName = "Realtime Reply Generator",
            description = "Generates personalized replies using Latest Chat Memory, Relationship Summary & Person Memory",
            enabled = true,
            primaryBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            primaryApiKey = "",
            primaryModel = "gemini-2.5-flash",
            fallbacks = emptyList()
        )

        /**
         * Returns curated provider presets for each category.
         * Fulfills catalog requirements:
         * - OpenAI: https://api.openai.com/v1 (gpt-4o, gpt-4o-mini, whisper-1)
         * - Google Gemini: https://generativelanguage.googleapis.com/v1beta/openai (gemini-2.5-flash, gemini-2.0-flash, gemini-1.5-pro)
         * - Anthropic Claude: Claude 3.5 Sonnet, Claude 3.5 Haiku (via OpenRouter / Proxy)
         * - Groq: https://api.groq.com/openai/v1 (llama-3.3-70b-versatile, llama-3.1-8b-instant, whisper-large-v3)
         * - DeepSeek: https://api.deepseek.com/v1 (deepseek-chat, deepseek-reasoner)
         * - OpenRouter: https://openrouter.ai/api/v1
         * - Custom / Local: Ollama, LM Studio, vLLM
         */
        fun getPresetsForCategory(categoryId: String): List<ProviderPreset> {
            return when (categoryId) {
                CATEGORY_EXTRACTOR -> listOf(
                    ProviderPreset(
                        id = "gemini_25_flash_vision",
                        name = "Google Gemini 2.5 Flash",
                        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                        defaultModel = "gemini-2.5-flash",
                        category = CATEGORY_EXTRACTOR,
                        tag = "Recommended Vision",
                        docUrl = "https://ai.google.dev/gemini-api/docs",
                        suggestedTemperature = 0.2,
                        suggestedMaxTokens = 1500
                    ),
                    ProviderPreset(
                        id = "openrouter_gemini_vision",
                        name = "OpenRouter (Gemini 2.0 Flash)",
                        baseUrl = "https://openrouter.ai/api/v1",
                        defaultModel = "google/gemini-2.0-flash-001",
                        category = CATEGORY_EXTRACTOR,
                        tag = "Fast Multimodal",
                        docUrl = "https://openrouter.ai/models/google/gemini-2.0-flash-001",
                        suggestedTemperature = 0.2,
                        suggestedMaxTokens = 1500
                    ),
                    ProviderPreset(
                        id = "groq_vision_llama",
                        name = "Groq Vision (Llama 3.2 11B)",
                        baseUrl = "https://api.groq.com/openai/v1",
                        defaultModel = "llama-3.2-11b-vision-preview",
                        category = CATEGORY_EXTRACTOR,
                        tag = "Ultra-Fast Vision",
                        docUrl = "https://console.groq.com/docs/models",
                        suggestedTemperature = 0.2,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "openai_gpt4o_mini_vision",
                        name = "OpenAI GPT-4o Mini",
                        baseUrl = "https://api.openai.com/v1",
                        defaultModel = "gpt-4o-mini",
                        category = CATEGORY_EXTRACTOR,
                        tag = "Accurate Vision",
                        docUrl = "https://platform.openai.com/docs/models",
                        suggestedTemperature = 0.2,
                        suggestedMaxTokens = 1200
                    ),
                    ProviderPreset(
                        id = "openai_gpt4o_vision",
                        name = "OpenAI GPT-4o",
                        baseUrl = "https://api.openai.com/v1",
                        defaultModel = "gpt-4o",
                        category = CATEGORY_EXTRACTOR,
                        tag = "Flagship Vision",
                        docUrl = "https://platform.openai.com/docs/models",
                        suggestedTemperature = 0.2,
                        suggestedMaxTokens = 2000
                    ),
                    ProviderPreset(
                        id = "claude_35_sonnet_vision",
                        name = "Claude 3.5 Sonnet (OpenRouter)",
                        baseUrl = "https://openrouter.ai/api/v1",
                        defaultModel = "anthropic/claude-3.5-sonnet",
                        category = CATEGORY_EXTRACTOR,
                        tag = "High Detail OCR",
                        docUrl = "https://openrouter.ai/models/anthropic/claude-3.5-sonnet",
                        suggestedTemperature = 0.2,
                        suggestedMaxTokens = 2000
                    )
                )
                "stt" -> listOf(
                    ProviderPreset(
                        id = "openai_whisper",
                        name = "OpenAI Whisper-1",
                        baseUrl = "https://api.openai.com/v1",
                        defaultModel = "whisper-1",
                        category = "stt",
                        tag = "Standard STT",
                        docUrl = "https://platform.openai.com/docs/guides/speech-to-text",
                        suggestedTemperature = 0.0,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "groq_whisper_large",
                        name = "Groq Whisper Large v3",
                        baseUrl = "https://api.groq.com/openai/v1",
                        defaultModel = "whisper-large-v3",
                        category = "stt",
                        tag = "Instant Speed",
                        docUrl = "https://console.groq.com/docs/speech-text",
                        suggestedTemperature = 0.0,
                        suggestedMaxTokens = 1000
                    )
                )
                else -> listOf(
                    ProviderPreset(
                        id = "gemini_25_flash",
                        name = "Google Gemini 2.5 Flash",
                        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                        defaultModel = "gemini-2.5-flash",
                        category = "all",
                        tag = "Primary • High Quota",
                        docUrl = "https://ai.google.dev/gemini-api/docs",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "claude_35_haiku",
                        name = "Claude 3.5 Haiku (Anthropic/OpenRouter)",
                        baseUrl = "https://openrouter.ai/api/v1",
                        defaultModel = "anthropic/claude-3.5-haiku",
                        category = "all",
                        tag = "Conversational",
                        docUrl = "https://openrouter.ai/models/anthropic/claude-3.5-haiku",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "groq_llama_33",
                        name = "Groq (Llama 3.3 70B)",
                        baseUrl = "https://api.groq.com/openai/v1",
                        defaultModel = "llama-3.3-70b-versatile",
                        category = "all",
                        tag = "Ultra-Fast (300+ tps)",
                        docUrl = "https://console.groq.com/docs/models",
                        suggestedTemperature = 0.6,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "deepseek_chat",
                        name = "DeepSeek Chat",
                        baseUrl = "https://api.deepseek.com/v1",
                        defaultModel = "deepseek-chat",
                        category = "all",
                        tag = "Ultra-Economical",
                        docUrl = "https://platform.deepseek.com/api-docs",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "openai_gpt4o_mini",
                        name = "OpenAI GPT-4o Mini",
                        baseUrl = "https://api.openai.com/v1",
                        defaultModel = "gpt-4o-mini",
                        category = "all",
                        tag = "Reliable Standard",
                        docUrl = "https://platform.openai.com/docs/models",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "openai_gpt4o",
                        name = "OpenAI GPT-4o",
                        baseUrl = "https://api.openai.com/v1",
                        defaultModel = "gpt-4o",
                        category = "all",
                        tag = "Smartest",
                        docUrl = "https://platform.openai.com/docs/models",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1500
                    ),
                    ProviderPreset(
                        id = "claude_35_sonnet",
                        name = "Claude 3.5 Sonnet (OpenRouter)",
                        baseUrl = "https://openrouter.ai/api/v1",
                        defaultModel = "anthropic/claude-3.5-sonnet",
                        category = "all",
                        tag = "Nuanced Empathy",
                        docUrl = "https://openrouter.ai/models/anthropic/claude-3.5-sonnet",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1500
                    ),
                    ProviderPreset(
                        id = "groq_llama_31_instant",
                        name = "Groq Llama 3.1 8B Instant",
                        baseUrl = "https://api.groq.com/openai/v1",
                        defaultModel = "llama-3.1-8b-instant",
                        category = "all",
                        tag = "Instant Micro",
                        docUrl = "https://console.groq.com/docs/models",
                        suggestedTemperature = 0.6,
                        suggestedMaxTokens = 800
                    ),
                    ProviderPreset(
                        id = "deepseek_reasoner",
                        name = "DeepSeek Reasoner (R1)",
                        baseUrl = "https://api.deepseek.com/v1",
                        defaultModel = "deepseek-reasoner",
                        category = "all",
                        tag = "Reasoning R1",
                        docUrl = "https://platform.deepseek.com/api-docs",
                        suggestedTemperature = 0.6,
                        suggestedMaxTokens = 2000
                    ),
                    ProviderPreset(
                        id = "openrouter_llama_33",
                        name = "OpenRouter (Llama 3.3)",
                        baseUrl = "https://openrouter.ai/api/v1",
                        defaultModel = "meta-llama/llama-3.3-70b-instruct",
                        category = "all",
                        tag = "Flexible",
                        docUrl = "https://openrouter.ai/models/meta-llama/llama-3.3-70b-instruct",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "gemini_20_flash",
                        name = "Google Gemini 2.0 Flash",
                        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                        defaultModel = "gemini-2.0-flash",
                        category = "all",
                        tag = "Low Latency",
                        docUrl = "https://ai.google.dev/gemini-api/docs",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "gemini_15_pro",
                        name = "Google Gemini 1.5 Pro",
                        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                        defaultModel = "gemini-1.5-pro",
                        category = "all",
                        tag = "Deep Context",
                        docUrl = "https://ai.google.dev/gemini-api/docs",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 2000
                    ),
                    ProviderPreset(
                        id = "ollama_local",
                        name = "Ollama (Local LLM)",
                        baseUrl = "http://10.0.2.2:11434",
                        defaultModel = "llama3.2",
                        category = "all",
                        tag = "100% Offline / Private",
                        docUrl = "https://ollama.com",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1000
                    ),
                    ProviderPreset(
                        id = "lmstudio_local",
                        name = "LM Studio / vLLM (Local)",
                        baseUrl = "http://10.0.2.2:1234/v1",
                        defaultModel = "local-model",
                        category = "all",
                        tag = "Local OpenAI API",
                        docUrl = "https://lmstudio.ai/docs",
                        suggestedTemperature = 0.7,
                        suggestedMaxTokens = 1000
                    )
                )
            }
        }

        /**
         * Returns all unique built-in provider presets across all categories.
         */
        fun getAllBuiltInPresets(): List<ProviderPreset> {
            val extractorList = getPresetsForCategory(CATEGORY_EXTRACTOR)
            val sttList = getPresetsForCategory("stt")
            val generalList = getPresetsForCategory(CATEGORY_REPLY)
            return (extractorList + sttList + generalList).distinctBy { it.name + it.baseUrl + it.defaultModel }
        }

        /**
         * Creates a standard pool of fallback endpoints for one-tap batch creation.
         */
        fun getStandardFallbackPool(categoryId: String): List<FallbackEndpoint> {
            return getBalancedFallbackChain(categoryId)
        }

        /**
         * Returns the balanced, multi-provider fallback pool explicitly specified in requirement 1.C.3:
         * (e.g., Primary: Gemini 2.5 Flash, Fallback 1: Claude 3.5 Haiku, Fallback 2: Groq Llama 3.3)
         */
        fun getBalancedFallbackChain(categoryId: String): List<FallbackEndpoint> {
            return when (categoryId) {
                CATEGORY_EXTRACTOR -> listOf(
                    FallbackEndpoint(
                        id = UUID.randomUUID().toString(),
                        name = "OpenRouter (Gemini Flash Vision)",
                        baseUrl = "https://openrouter.ai/api/v1",
                        apiKey = "",
                        model = "google/gemini-2.0-flash-001",
                        enabled = true
                    ),
                    FallbackEndpoint(
                        id = UUID.randomUUID().toString(),
                        name = "Groq Vision (Llama 3.2 11B)",
                        baseUrl = "https://api.groq.com/openai/v1",
                        apiKey = "",
                        model = "llama-3.2-11b-vision-preview",
                        enabled = true
                    ),
                    FallbackEndpoint(
                        id = UUID.randomUUID().toString(),
                        name = "OpenAI GPT-4o Mini Vision",
                        baseUrl = "https://api.openai.com/v1",
                        apiKey = "",
                        model = "gpt-4o-mini",
                        enabled = true
                    )
                )
                else -> listOf(
                    FallbackEndpoint(
                        id = UUID.randomUUID().toString(),
                        name = "Claude 3.5 Haiku (Anthropic/OpenRouter)",
                        baseUrl = "https://openrouter.ai/api/v1",
                        apiKey = "",
                        model = "anthropic/claude-3.5-haiku",
                        enabled = true
                    ),
                    FallbackEndpoint(
                        id = UUID.randomUUID().toString(),
                        name = "Groq Llama 3.3 70B",
                        baseUrl = "https://api.groq.com/openai/v1",
                        apiKey = "",
                        model = "llama-3.3-70b-versatile",
                        enabled = true
                    ),
                    FallbackEndpoint(
                        id = UUID.randomUUID().toString(),
                        name = "DeepSeek Chat",
                        baseUrl = "https://api.deepseek.com/v1",
                        apiKey = "",
                        model = "deepseek-chat",
                        enabled = true
                    )
                )
            }
        }

        /**
         * Recommended primary endpoint preset for each category in the balanced pool setup.
         */
        fun getBalancedPrimaryPreset(categoryId: String): ProviderPreset {
            return when (categoryId) {
                CATEGORY_EXTRACTOR -> ProviderPreset(
                    name = "Google Gemini 2.5 Flash",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                    defaultModel = "gemini-2.5-flash",
                    category = CATEGORY_EXTRACTOR,
                    tag = "Primary Vision"
                )
                "stt" -> ProviderPreset(
                    name = "OpenAI Whisper-1",
                    baseUrl = "https://api.openai.com/v1",
                    defaultModel = "whisper-1",
                    category = "stt",
                    tag = "Primary STT"
                )
                else -> ProviderPreset(
                    name = "Google Gemini 2.5 Flash",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                    defaultModel = "gemini-2.5-flash",
                    category = "all",
                    tag = "Primary"
                )
            }
        }
    }
}
