package com.example.network

import android.util.Log
import com.example.data.model.FallbackEndpoint
import com.example.data.model.ModelCategoryConfig
import com.example.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object OpenAiClient {
    private const val TAG = "OpenAiClient"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun sanitizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length - 3)
        }
        return url
    }

    fun buildChatEndpoint(rawBaseUrl: String): String {
        val cleanUrl = sanitizeBaseUrl(rawBaseUrl)
        return if (cleanUrl.contains("generativelanguage.googleapis.com")) {
            if (cleanUrl.endsWith("/chat/completions")) {
                cleanUrl
            } else {
                "${cleanUrl.removeSuffix("/")}/chat/completions"
            }
        } else {
            "$cleanUrl/v1/chat/completions"
        }
    }

    suspend fun transcribeAudio(
        config: ProviderConfig,
        audioFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!config.enabled) {
                return@withContext Result.failure(IllegalStateException("Speech-to-Text provider is disabled."))
            }
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API Key is missing for Speech-to-Text."))
            }

            val baseUrl = sanitizeBaseUrl(config.baseUrl)
            val endpoint = "$baseUrl/v1/audio/transcriptions"
            val modelName = if (config.model.isNotBlank()) config.model else "whisper-1"

            val audioMediaType = "audio/m4a".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody(audioMediaType)
                )
                .addFormDataPart("model", modelName)
                .build()

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "STT error: ${response.code} $responseBody")
                val errorMsg = parseErrorMessage(responseBody, "STT API returned status ${response.code}")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseBody)
            val transcript = json.optString("text", "").trim()
            Result.success(transcript)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during STT", e)
            Result.failure(e)
        }
    }

    suspend fun completeChat(
        config: ProviderConfig,
        systemPrompt: String,
        userContent: String
    ): Result<String> {
        return completeChatDirect(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            systemPrompt = systemPrompt,
            userContent = userContent
        )
    }

    /**
     * Executes chat completion on a single endpoint.
     */
    private suspend fun completeChatDirect(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userContent: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API Key is missing."))
            }

            val endpoint = buildChatEndpoint(baseUrl)
            val modelName = if (model.isNotBlank()) model else "gpt-4o-mini"

            val messages = JSONArray().apply {
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }

            val payload = JSONObject().apply {
                put("model", modelName)
                put("messages", messages)
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Chat API error: ${response.code} $responseBody")
                val errorMsg = parseErrorMessage(responseBody, "Chat API returned status ${response.code}")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                val content = message?.optString("content", "")?.trim() ?: ""
                Result.success(content)
            } else {
                Result.failure(Exception("No choices returned from AI model."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Chat Completion", e)
            Result.failure(e)
        }
    }

    /**
     * Executes a chat completion across a ModelCategoryConfig:
     * First tries the Primary endpoint. If it fails, loops through Fallback 1..N in order.
     */
    suspend fun completeChatWithCategoryFallback(
        config: ModelCategoryConfig,
        systemPrompt: String,
        userContent: String
    ): Result<String> {
        if (!config.enabled) {
            return Result.failure(IllegalStateException("${config.displayName} is disabled in settings."))
        }

        val errors = mutableListOf<String>()

        // 1. Try Primary endpoint if key is present
        if (config.primaryApiKey.isNotBlank()) {
            val primaryResult = completeChatDirect(
                baseUrl = config.primaryBaseUrl,
                apiKey = config.primaryApiKey,
                model = config.primaryModel,
                systemPrompt = systemPrompt,
                userContent = userContent
            )
            if (primaryResult.isSuccess) {
                return primaryResult
            } else {
                val err = primaryResult.exceptionOrNull()?.message ?: "Primary endpoint error"
                Log.w(TAG, "Primary endpoint for ${config.displayName} failed: $err. Trying fallbacks...")
                errors.add("Primary: $err")
            }
        } else {
            errors.add("Primary: No API key configured")
        }

        // 2. Iterate through configured fallbacks
        val activeFallbacks = config.fallbacks.filter { it.enabled && it.apiKey.isNotBlank() }
        for ((index, fb) in activeFallbacks.withIndex()) {
            val fbResult = completeChatDirect(
                baseUrl = fb.baseUrl,
                apiKey = fb.apiKey,
                model = fb.model,
                systemPrompt = systemPrompt,
                userContent = userContent
            )
            if (fbResult.isSuccess) {
                Log.i(TAG, "Fallback #${index + 1} (${fb.name}) succeeded for ${config.displayName}!")
                return fbResult
            } else {
                val err = fbResult.exceptionOrNull()?.message ?: "Failed"
                Log.w(TAG, "Fallback #${index + 1} (${fb.name}) failed: $err")
                errors.add("${fb.name}: $err")
            }
        }

        val combinedError = if (errors.isEmpty()) {
            "No active API keys found for ${config.displayName}. Please configure Primary or Fallback API in Providers tab."
        } else {
            "All endpoints failed for ${config.displayName}:\n" + errors.joinToString("\n")
        }
        return Result.failure(Exception(combinedError))
    }

    /**
     * Executes Vision API completion on a single endpoint.
     */
    private suspend fun completeVisionDirect(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API Key is missing for Vision."))
            }

            val endpoint = buildChatEndpoint(baseUrl)
            val modelName = if (model.isNotBlank()) model else "gpt-4o-mini"

            // Construct OpenAI vision user message with text + image_url
            val contentArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", userPrompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:$mimeType;base64,$imageBase64")
                    })
                })
            }

            val messages = JSONArray().apply {
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })
            }

            val payload = JSONObject().apply {
                put("model", modelName)
                put("messages", messages)
                put("temperature", 0.2)
                put("max_tokens", 1000)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Vision API error: ${response.code} $responseBody")
                val errorMsg = parseErrorMessage(responseBody, "Vision API error: ${response.code}")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                val content = message?.optString("content", "")?.trim() ?: ""
                Result.success(content)
            } else {
                Result.failure(Exception("No output returned from Vision model."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Vision extraction", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts chat messages from an uploaded screenshot using Extractor model with fallbacks:
     * Output format requirement:
     * <Person Name>: <Their Chat>
     * <My Name>: <My Chat>
     */
    suspend fun extractChatFromImage(
        config: ModelCategoryConfig,
        personName: String,
        myName: String,
        imageBase64: String,
        mimeType: String = "image/jpeg"
    ): Result<List<String>> {
        if (!config.enabled) {
            return Result.failure(IllegalStateException("Extractor model is disabled in settings."))
        }

        val systemPrompt = """
You are a precise mobile chat transcript extractor.
Analyze the provided messaging screenshot between '$personName' and '$myName'.

CRITICAL VISUAL LAYOUT RULES:
1. Bubble in the RIGHT is my message (sent by user):
   Format: <$myName>: <message content>
2. Bubble in the LEFT is the other person's message (received from '$personName'):
   Format: <$personName>: <message content>
3. Both parties' messages MUST be extracted in exact chronological top-to-bottom order.
4. Extract ALL visible messages from both the left and right sides. Do NOT extract only one person's messages!
5. Every single output line MUST strictly follow this exact format:
<$personName>: <message>
<$myName>: <message>

OUTPUT CONSTRAINTS:
- Output ONLY the formatted lines.
- NO markdown code fences (no ```).
- NO greetings, intro, or commentary.
""".trimIndent()

        val userPrompt = """
Extract all chat bubbles from this screenshot in chronological order:
- Left bubble (the other person): <$personName>: <message>
- Right bubble (my message): <$myName>: <message>
Output ONLY lines adhering to this format.
""".trimIndent()

        val errors = mutableListOf<String>()

        // 1. Try Primary
        if (config.primaryApiKey.isNotBlank()) {
            val primaryRes = completeVisionDirect(
                baseUrl = config.primaryBaseUrl,
                apiKey = config.primaryApiKey,
                model = config.primaryModel,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = imageBase64,
                mimeType = mimeType
            )
            if (primaryRes.isSuccess) {
                val parsed = parseExtractedChatLines(primaryRes.getOrNull() ?: "", personName, myName)
                return Result.success(parsed)
            } else {
                val err = primaryRes.exceptionOrNull()?.message ?: "Primary error"
                errors.add("Primary: $err")
            }
        } else {
            errors.add("Primary: No API key")
        }

        // 2. Try Fallbacks
        val activeFallbacks = config.fallbacks.filter { it.enabled && it.apiKey.isNotBlank() }
        for (fb in activeFallbacks) {
            val fbRes = completeVisionDirect(
                baseUrl = fb.baseUrl,
                apiKey = fb.apiKey,
                model = fb.model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageBase64 = imageBase64,
                mimeType = mimeType
            )
            if (fbRes.isSuccess) {
                val parsed = parseExtractedChatLines(fbRes.getOrNull() ?: "", personName, myName)
                return Result.success(parsed)
            } else {
                errors.add("${fb.name}: ${fbRes.exceptionOrNull()?.message}")
            }
        }

        val msg = if (errors.isEmpty()) "No active Extractor API keys configured." else "Extractor failed:\n" + errors.joinToString("\n")
        return Result.failure(Exception(msg))
    }

    private fun parseExtractedChatLines(raw: String, personName: String, myName: String): List<String> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<String>()

        for (line in lines) {
            // Strip any accidental markdown bullets or numbers
            val clean = line.replace(Regex("^[-*•\\d.]+\\s*"), "").trim()
            if (clean.startsWith("<") && clean.contains(">:")) {
                result.add(clean)
            } else if (clean.contains(":")) {
                // If AI omitted angle brackets, format properly
                val parts = clean.split(":", limit = 2)
                val sender = parts[0].trim().replace("<", "").replace(">", "")
                val text = parts[1].trim()
                if (sender.equals(myName, ignoreCase = true) || sender.equals("Me", ignoreCase = true) || sender.equals("You", ignoreCase = true)) {
                    result.add("<$myName>: $text")
                } else {
                    result.add("<$personName>: $text")
                }
            } else if (clean.isNotBlank()) {
                // Default to person's chat if no colon
                result.add("<$personName>: $clean")
            }
        }
        return result
    }

    /**
     * Summarizer AI Model:
     * Compresses the conversation history to LESS THAN n words and returns the new Relationship Summary.
     */
    suspend fun summarizeChatHistory(
        config: ModelCategoryConfig,
        personName: String,
        myName: String,
        existingSummary: String,
        chatMessages: List<String>,
        maxWords: Int
    ): Result<String> {
        val targetWords = if (maxWords > 10) maxWords else 80
        val systemPrompt = """
You are a concise Relationship Context Summarizer.
Your goal is to compress conversation messages between '$personName' and '$myName' into an updated, continuous relationship summary.

CRITICAL DIRECTIVES:
1. The total length MUST be LESS THAN $targetWords words.
2. Focus on essential background facts, shared context, user goals, commitments, tone preferences, and discussion topics.
3. Merge and integrate the existing summary with the new conversation history seamlessly.
4. Output ONLY the compressed summary text in less than $targetWords words.
5. Do NOT include preamble, headings, word counts, or meta-comments.
""".trimIndent()

        val historyText = chatMessages.joinToString("\n")
        val userContent = """
Existing Relationship Summary:
${if (existingSummary.isNotBlank()) existingSummary else "[None yet]"}

Recent Conversation to Compress:
$historyText

Compress the above into a relationship summary of LESS THAN $targetWords words.
""".trimIndent()

        return completeChatWithCategoryFallback(config, systemPrompt, userContent)
    }

    /**
     * Refine Feature:
     * Refines the last copied message based on new custom refining instructions and active tone.
     */
    suspend fun refineMessage(
        config: ModelCategoryConfig,
        originalMessage: String,
        instruction: String,
        tonePrompt: String,
        personMemoryContext: String? = null
    ): Result<String> {
        val systemPrompt = """
You are a message refinement specialist.
Refine and rewrite the provided message strictly according to the user's refining instruction and desired tone.

Tone Directive:
$tonePrompt

CRITICAL OUTPUT RULES:
1. Output ONLY the refined text to send directly.
2. Do NOT talk to the user or include introductory phrases (e.g. 'Here is the refined message:').
3. Do NOT wrap in quotation marks.
4. Preserve the core facts and intent while applying the user's specific refinement.
""".trimIndent()

        val contextBlock = if (!personMemoryContext.isNullOrBlank()) {
            "\nTarget Recipient Context & Memory:\n$personMemoryContext\n"
        } else ""

        val userContent = """
Original Message to Refine:
"$originalMessage"
$contextBlock
Refining Instruction:
${if (instruction.isNotBlank()) instruction else "Polish and improve clarity."}

Draft the refined message directly.
""".trimIndent()

        return completeChatWithCategoryFallback(config, systemPrompt, userContent)
    }

    suspend fun testEndpoint(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = buildChatEndpoint(baseUrl)
            val requestJson = JSONObject().apply {
                put("model", model.ifBlank { "gpt-4o-mini" })
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Ping test. Reply with 'OK'")
                    })
                }
                put("messages", messages)
                put("max_tokens", 10)
            }
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
            }
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val code = response.code
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success("Connection successful! (HTTP $code)")
            } else {
                val err = parseErrorMessage(body, "HTTP $code: $body")
                Result.failure(Exception("Connection failed: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(body: String, fallback: String): String {
        return try {
            val json = JSONObject(body)
            if (json.has("error")) {
                val errorObj = json.optJSONObject("error")
                if (errorObj != null && errorObj.has("message")) {
                    return errorObj.getString("message")
                }
                return json.optString("error", fallback)
            }
            fallback
        } catch (e: Exception) {
            fallback
        }
    }
}
