package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AssistMode
import com.example.data.model.FallbackEndpoint
import com.example.data.model.ModelCategoryConfig
import com.example.data.model.Person
import com.example.data.model.ProviderConfig
import com.example.data.model.ProviderPreset
import com.example.data.model.TonePreset
import com.example.network.OpenAiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AppPreferencesRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_assist_prefs", Context.MODE_PRIVATE)

    // --- 3 AI Model Categories with Fallback APIs ---
    private val _extractorConfig = MutableStateFlow(loadCategoryConfig(ModelCategoryConfig.CATEGORY_EXTRACTOR))
    val extractorConfig: StateFlow<ModelCategoryConfig> = _extractorConfig.asStateFlow()

    private val _summarizerConfig = MutableStateFlow(loadCategoryConfig(ModelCategoryConfig.CATEGORY_SUMMARIZER))
    val summarizerConfig: StateFlow<ModelCategoryConfig> = _summarizerConfig.asStateFlow()

    private val _replyModelConfig = MutableStateFlow(loadCategoryConfig(ModelCategoryConfig.CATEGORY_REPLY))
    val replyModelConfig: StateFlow<ModelCategoryConfig> = _replyModelConfig.asStateFlow()

    // --- People & Person Memory ---
    private val _people = MutableStateFlow(loadPeople())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    private val _activePersonId = MutableStateFlow(loadActivePersonId())
    val activePersonId: StateFlow<String> = _activePersonId.asStateFlow()

    // --- Legacy / Shared Configs ---
    private val _sttConfig = MutableStateFlow(loadSttConfig())
    val sttConfig: StateFlow<ProviderConfig> = _sttConfig.asStateFlow()

    private val _cleanupConfig = MutableStateFlow(loadCleanupConfig())
    val cleanupConfig: StateFlow<ProviderConfig> = _cleanupConfig.asStateFlow()

    private val _activeMode = MutableStateFlow(loadActiveMode())
    val activeMode: StateFlow<AssistMode> = _activeMode.asStateFlow()

    private val _saveToClipboard = MutableStateFlow(loadSaveToClipboard())
    val saveToClipboard: StateFlow<Boolean> = _saveToClipboard.asStateFlow()

    private val _activeToneId = MutableStateFlow(loadActiveToneId())
    val activeToneId: StateFlow<String> = _activeToneId.asStateFlow()

    private val _tonePresets = MutableStateFlow(loadTonePresets())
    val tonePresets: StateFlow<List<TonePreset>> = _tonePresets.asStateFlow()

    private val _shareHandoffMode = MutableStateFlow(loadShareHandoffMode())
    val shareHandoffMode: StateFlow<Boolean> = _shareHandoffMode.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(loadOnboardingCompleted())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _showBubbleAlerts = MutableStateFlow(loadShowBubbleAlerts())
    val showBubbleAlerts: StateFlow<Boolean> = _showBubbleAlerts.asStateFlow()

    private val _customReplyPrompt = MutableStateFlow(loadCustomReplyPrompt())
    val customReplyPrompt: StateFlow<String> = _customReplyPrompt.asStateFlow()

    private val _prioritizeCustomPrompt = MutableStateFlow(loadPrioritizeCustomPrompt())
    val prioritizeCustomPrompt: StateFlow<Boolean> = _prioritizeCustomPrompt.asStateFlow()

    private val _appendMode = MutableStateFlow(loadAppendMode())
    val appendMode: StateFlow<Boolean> = _appendMode.asStateFlow()

    // --- Provider Presets (Separate window configuration) ---
    private val _customPresets = MutableStateFlow(loadCustomPresets())
    val customPresets: StateFlow<List<ProviderPreset>> = _customPresets.asStateFlow()

    // --- Extractor Option: Accessibility Service vs Vision Screenshot ---
    private val _useAccessibilityExtractor = MutableStateFlow(loadUseAccessibilityExtractor())
    val useAccessibilityExtractor: StateFlow<Boolean> = _useAccessibilityExtractor.asStateFlow()

    // Helper for currently active person
    fun getActivePerson(): Person? {
        val id = _activePersonId.value
        return _people.value.find { it.id == id } ?: _people.value.firstOrNull()
    }

    // --- People Management ---
    private fun loadPeople(): List<Person> {
        val rawJson = prefs.getString(KEY_PEOPLE, null)
        if (rawJson.isNullOrBlank()) {
            return listOf(Person.DEFAULT_PERSON)
        }
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<Person>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val chatArr = obj.optJSONArray("latestChatMemory")
                val chats = mutableListOf<String>()
                if (chatArr != null) {
                    for (j in 0 until chatArr.length()) {
                        chats.add(chatArr.getString(j))
                    }
                }
                list.add(
                    Person(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        myName = obj.optString("myName", "Me"),
                        personMemory = obj.optString("personMemory", ""),
                        relationshipSummary = obj.optString("relationshipSummary", ""),
                        latestChatMemory = chats.takeLast(Person.MAX_CHAT_MEMORY_ITEMS),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isEmpty()) listOf(Person.DEFAULT_PERSON) else list
        } catch (e: Exception) {
            listOf(Person.DEFAULT_PERSON)
        }
    }

    private fun loadActivePersonId(): String {
        val saved = prefs.getString(KEY_ACTIVE_PERSON_ID, null)
        return if (!saved.isNullOrBlank()) saved else Person.DEFAULT_PERSON.id
    }

    fun savePeople(peopleList: List<Person>) {
        val arr = JSONArray()
        for (p in peopleList) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("myName", p.myName)
                put("personMemory", p.personMemory)
                put("relationshipSummary", p.relationshipSummary)
                val chatArr = JSONArray()
                p.latestChatMemory.takeLast(Person.MAX_CHAT_MEMORY_ITEMS).forEach { chatArr.put(it) }
                put("latestChatMemory", chatArr)
                put("updatedAt", p.updatedAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PEOPLE, arr.toString()).apply()
        _people.value = peopleList
    }

    fun addOrUpdatePerson(person: Person) {
        val current = _people.value.toMutableList()
        val index = current.indexOfFirst { it.id == person.id }
        if (index >= 0) {
            current[index] = person.copy(updatedAt = System.currentTimeMillis())
        } else {
            current.add(person.copy(updatedAt = System.currentTimeMillis()))
        }
        savePeople(current)
        if (_activePersonId.value.isBlank() || _people.value.none { it.id == _activePersonId.value }) {
            setActivePersonId(person.id)
        }
    }

    fun deletePerson(id: String) {
        val current = _people.value.filter { it.id != id }
        val finalPeople = if (current.isEmpty()) listOf(Person.DEFAULT_PERSON) else current
        savePeople(finalPeople)
        if (_activePersonId.value == id) {
            setActivePersonId(finalPeople.first().id)
        }
    }

    fun setActivePersonId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PERSON_ID, id).apply()
        _activePersonId.value = id
    }

    /**
     * Checks whether an incoming chat line is an exact or normalized duplicate of an existing message.
     */
    private fun isDuplicateChatMessage(existing: String, incoming: String): Boolean {
        val cleanExisting = existing.trim().replace(Regex("^<[^>]+>:\\s*"), "").trim()
        val cleanIncoming = incoming.trim().replace(Regex("^<[^>]+>:\\s*"), "").trim()
        if (cleanExisting.equals(cleanIncoming, ignoreCase = true)) {
            val senderExisting = Regex("^<([^>]+)>").find(existing.trim())?.groupValues?.getOrNull(1)?.trim()
            val senderIncoming = Regex("^<([^>]+)>").find(incoming.trim())?.groupValues?.getOrNull(1)?.trim()
            if (senderExisting != null && senderIncoming != null) {
                return senderExisting.equals(senderIncoming, ignoreCase = true)
            }
            return true
        }
        return false
    }

    /**
     * Appends only truly new messages to Latest Chat Memory.
     * Crucially: allows messages to grow beyond 8 without premature truncation,
     * so that the full batch can be compressed by the Summarizer.
     *
     * @return Pair(number of new messages added, updated Person?)
     */
    fun appendDeduplicatedChatMessages(personId: String, newMessages: List<String>): Pair<Int, Person?> {
        val current = _people.value.toMutableList()
        val index = current.indexOfFirst { it.id == personId }
        if (index < 0) return Pair(0, null)

        val person = current[index]
        val existingList = person.latestChatMemory

        val trulyNew = mutableListOf<String>()
        for (msg in newMessages) {
            val trimmed = msg.trim()
            if (trimmed.isBlank()) continue
            val isDuplicate = existingList.any { isDuplicateChatMessage(it, trimmed) } ||
                    trulyNew.any { isDuplicateChatMessage(it, trimmed) }
            if (!isDuplicate) {
                trulyNew.add(trimmed)
            }
        }

        if (trulyNew.isEmpty()) {
            return Pair(0, person)
        }

        val updatedList = existingList + trulyNew
        val updatedPerson = person.copy(
            latestChatMemory = updatedList,
            updatedAt = System.currentTimeMillis()
        )
        current[index] = updatedPerson
        savePeople(current)
        return Pair(trulyNew.size, updatedPerson)
    }

    fun updateLatestChatMemory(personId: String, newMessages: List<String>) {
        appendDeduplicatedChatMessages(personId, newMessages)
    }

    fun clearLatestChatMemory(personId: String) {
        val current = _people.value.toMutableList()
        val index = current.indexOfFirst { it.id == personId }
        if (index >= 0) {
            val person = current[index]
            current[index] = person.copy(
                latestChatMemory = emptyList(),
                updatedAt = System.currentTimeMillis()
            )
            savePeople(current)
        }
    }

    /**
     * Summarizer workflow step:
     * If Latest Chat Memory has reached or exceeded the threshold (default 8),
     * sends all pending messages to the Summarizing AI, appends the compressed result
     * to Relationship Summary, and clears the latest messages.
     */
    suspend fun processSummarizerIfThresholdExceeded(
        personId: String,
        threshold: Int = 8,
        onStatus: ((String) -> Unit)? = null
    ): Result<Boolean> {
        val person = _people.value.find { it.id == personId }
            ?: return Result.failure(IllegalArgumentException("Person $personId not found"))

        if (person.latestChatMemory.size < threshold) {
            return Result.success(false)
        }

        val sumConfig = _summarizerConfig.value
        if (!sumConfig.enabled) {
            return Result.failure(IllegalStateException("Summarizer AI is disabled in settings."))
        }

        onStatus?.invoke("Compressing ${person.latestChatMemory.size} messages into Relationship Summary...")

        val sumResult = OpenAiClient.summarizeChatHistory(
            config = sumConfig,
            personName = person.name,
            myName = person.myName,
            existingSummary = person.relationshipSummary,
            chatMessages = person.latestChatMemory,
            maxWords = sumConfig.maxSummaryWords
        )

        if (sumResult.isFailure) {
            return Result.failure(sumResult.exceptionOrNull() ?: Exception("Summarizer AI failed"))
        }

        val newSummary = sumResult.getOrNull()?.trim() ?: ""
        if (newSummary.isNotBlank()) {
            val current = _people.value.toMutableList()
            val idx = current.indexOfFirst { it.id == personId }
            if (idx >= 0) {
                val p = current[idx]
                val combinedSummary = if (p.relationshipSummary.isBlank()) {
                    newSummary
                } else {
                    "${p.relationshipSummary.trim()}\n\n$newSummary"
                }
                current[idx] = p.copy(
                    relationshipSummary = combinedSummary,
                    latestChatMemory = emptyList(), // Clear as explicitly required!
                    updatedAt = System.currentTimeMillis()
                )
                savePeople(current)
            }
        }
        return Result.success(true)
    }

    fun replaceLatestChatMemory(personId: String, messages: List<String>) {
        val current = _people.value.toMutableList()
        val index = current.indexOfFirst { it.id == personId }
        if (index >= 0) {
            val person = current[index]
            current[index] = person.copy(
                latestChatMemory = messages.takeLast(Person.MAX_CHAT_MEMORY_ITEMS),
                updatedAt = System.currentTimeMillis()
            )
            savePeople(current)
        }
    }

    fun updateRelationshipSummary(personId: String, summary: String) {
        val current = _people.value.toMutableList()
        val index = current.indexOfFirst { it.id == personId }
        if (index >= 0) {
            val person = current[index]
            current[index] = person.copy(relationshipSummary = summary, updatedAt = System.currentTimeMillis())
            savePeople(current)
        }
    }

    fun updatePersonMemory(personId: String, memory: String) {
        val current = _people.value.toMutableList()
        val index = current.indexOfFirst { it.id == personId }
        if (index >= 0) {
            val person = current[index]
            current[index] = person.copy(personMemory = memory, updatedAt = System.currentTimeMillis())
            savePeople(current)
        }
    }

    // --- Category AI Models with Fallbacks ---
    private fun loadCategoryConfig(categoryId: String): ModelCategoryConfig {
        val key = "category_model_$categoryId"
        val raw = prefs.getString(key, null)
        val def = when (categoryId) {
            ModelCategoryConfig.CATEGORY_EXTRACTOR -> ModelCategoryConfig.defaultExtractor()
            ModelCategoryConfig.CATEGORY_SUMMARIZER -> ModelCategoryConfig.defaultSummarizer()
            else -> ModelCategoryConfig.defaultReplyGenerator()
        }
        if (raw.isNullOrBlank()) {
            // For backward compatibility, check legacy reply config if category is reply
            if (categoryId == ModelCategoryConfig.CATEGORY_REPLY) {
                val legacyKey = EncryptedKeyStoreManager.decrypt(prefs.getString("reply_api_key_enc", "") ?: "")
                val legacyUrl = prefs.getString("reply_base_url", "https://api.openai.com") ?: "https://api.openai.com"
                val legacyModel = prefs.getString("reply_model", "gpt-4o-mini") ?: "gpt-4o-mini"
                if (legacyKey.isNotBlank()) {
                    return def.copy(primaryBaseUrl = legacyUrl, primaryApiKey = legacyKey, primaryModel = legacyModel)
                }
            }
            return def
        }

        return try {
            val obj = JSONObject(raw)
            val fbArray = obj.optJSONArray("fallbacks")
            val fallbacks = mutableListOf<FallbackEndpoint>()
            if (fbArray != null) {
                for (i in 0 until fbArray.length()) {
                    val fbObj = fbArray.getJSONObject(i)
                    fallbacks.add(
                        FallbackEndpoint(
                            id = fbObj.getString("id"),
                            name = fbObj.optString("name", "Fallback API"),
                            baseUrl = fbObj.optString("baseUrl", "https://api.openai.com"),
                            apiKey = EncryptedKeyStoreManager.decrypt(fbObj.optString("apiKeyEnc", "")),
                            model = fbObj.optString("model", "gpt-4o-mini"),
                            enabled = fbObj.optBoolean("enabled", true)
                        )
                    )
                }
            }
            ModelCategoryConfig(
                categoryId = categoryId,
                displayName = obj.optString("displayName", def.displayName),
                description = obj.optString("description", def.description),
                enabled = obj.optBoolean("enabled", true),
                primaryBaseUrl = obj.optString("primaryBaseUrl", "https://api.openai.com"),
                primaryApiKey = EncryptedKeyStoreManager.decrypt(obj.optString("primaryApiKeyEnc", "")),
                primaryModel = obj.optString("primaryModel", "gpt-4o-mini"),
                fallbacks = fallbacks,
                maxSummaryWords = obj.optInt("maxSummaryWords", def.maxSummaryWords)
            )
        } catch (e: Exception) {
            def
        }
    }

    private fun persistCategoryConfig(config: ModelCategoryConfig) {
        val key = "category_model_${config.categoryId}"
        val obj = JSONObject().apply {
            put("categoryId", config.categoryId)
            put("displayName", config.displayName)
            put("description", config.description)
            put("enabled", config.enabled)
            put("primaryBaseUrl", config.primaryBaseUrl)
            put("primaryApiKeyEnc", EncryptedKeyStoreManager.encrypt(config.primaryApiKey))
            put("primaryModel", config.primaryModel)
            put("maxSummaryWords", config.maxSummaryWords)

            val fbArr = JSONArray()
            for (fb in config.fallbacks) {
                val fbObj = JSONObject().apply {
                    put("id", fb.id)
                    put("name", fb.name)
                    put("baseUrl", fb.baseUrl)
                    put("apiKeyEnc", EncryptedKeyStoreManager.encrypt(fb.apiKey))
                    put("model", fb.model)
                    put("enabled", fb.enabled)
                }
                fbArr.put(fbObj)
            }
            put("fallbacks", fbArr)
        }
        prefs.edit().putString(key, obj.toString()).apply()
    }

    fun saveExtractorConfig(config: ModelCategoryConfig) {
        persistCategoryConfig(config)
        _extractorConfig.value = config
    }

    fun saveSummarizerConfig(config: ModelCategoryConfig) {
        persistCategoryConfig(config)
        _summarizerConfig.value = config
    }

    fun saveReplyModelConfig(config: ModelCategoryConfig) {
        persistCategoryConfig(config)
        _replyModelConfig.value = config
        // Also keep legacy replyConfig in sync for components that read it
        saveReplyConfig(
            ProviderConfig(
                enabled = config.enabled,
                baseUrl = config.primaryBaseUrl,
                apiKey = config.primaryApiKey,
                model = config.primaryModel
            )
        )
    }

    private fun updateCategoryFallbacks(categoryId: String, transform: (List<FallbackEndpoint>) -> List<FallbackEndpoint>) {
        when (categoryId) {
            ModelCategoryConfig.CATEGORY_EXTRACTOR -> {
                val current = _extractorConfig.value
                saveExtractorConfig(current.copy(fallbacks = transform(current.fallbacks)))
            }
            ModelCategoryConfig.CATEGORY_SUMMARIZER -> {
                val current = _summarizerConfig.value
                saveSummarizerConfig(current.copy(fallbacks = transform(current.fallbacks)))
            }
            ModelCategoryConfig.CATEGORY_REPLY -> {
                val current = _replyModelConfig.value
                saveReplyModelConfig(current.copy(fallbacks = transform(current.fallbacks)))
            }
        }
    }

    fun addFallbackToCategory(categoryId: String, fallback: FallbackEndpoint) {
        updateCategoryFallbacks(categoryId) { current ->
            current + fallback
        }
    }

    fun saveFallbackToCategory(categoryId: String, fallback: FallbackEndpoint) {
        updateCategoryFallbacks(categoryId) { current ->
            if (current.any { it.id == fallback.id }) {
                current.map { if (it.id == fallback.id) fallback else it }
            } else {
                current + fallback
            }
        }
    }

    fun addBatchFallbacksToCategory(categoryId: String, newFallbacks: List<FallbackEndpoint>) {
        updateCategoryFallbacks(categoryId) { current ->
            // Append while avoiding exact duplicates by name
            val existingNames = current.map { it.name.lowercase().trim() }.toSet()
            val filtered = newFallbacks.filter { it.name.lowercase().trim() !in existingNames }
            current + (if (filtered.isNotEmpty()) filtered else newFallbacks)
        }
    }

    fun toggleFallbackInCategory(categoryId: String, fallbackId: String, enabled: Boolean) {
        updateCategoryFallbacks(categoryId) { current ->
            current.map { if (it.id == fallbackId) it.copy(enabled = enabled) else it }
        }
    }

    fun duplicateFallbackInCategory(categoryId: String, fallbackId: String) {
        updateCategoryFallbacks(categoryId) { current ->
            val target = current.find { it.id == fallbackId }
            if (target != null) {
                val clone = target.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${target.name} (Copy)"
                )
                current + clone
            } else {
                current
            }
        }
    }

    fun reorderFallbackInCategory(categoryId: String, fallbackId: String, moveUp: Boolean) {
        updateCategoryFallbacks(categoryId) { current ->
            val index = current.indexOfFirst { it.id == fallbackId }
            if (index == -1) return@updateCategoryFallbacks current
            val targetIndex = if (moveUp) index - 1 else index + 1
            if (targetIndex < 0 || targetIndex >= current.size) return@updateCategoryFallbacks current
            val list = current.toMutableList()
            val item = list.removeAt(index)
            list.add(targetIndex, item)
            list
        }
    }

    fun updateFallbackInCategory(categoryId: String, fallback: FallbackEndpoint) {
        updateCategoryFallbacks(categoryId) { current ->
            current.map { if (it.id == fallback.id) fallback else it }
        }
    }

    fun deleteFallbackFromCategory(categoryId: String, fallbackId: String) {
        updateCategoryFallbacks(categoryId) { current ->
            current.filter { it.id != fallbackId }
        }
    }

    fun setSummarizerMaxWords(maxWords: Int) {
        val current = _summarizerConfig.value
        val updated = current.copy(maxSummaryWords = maxWords.coerceIn(10, 500))
        saveSummarizerConfig(updated)
    }

    // --- Legacy / Settings ---
    private fun loadSttConfig(): ProviderConfig {
        return ProviderConfig(
            enabled = prefs.getBoolean(KEY_STT_ENABLED, true),
            baseUrl = prefs.getString(KEY_STT_BASE_URL, "https://api.openai.com") ?: "https://api.openai.com",
            apiKey = EncryptedKeyStoreManager.decrypt(prefs.getString(KEY_STT_API_KEY, "") ?: ""),
            model = prefs.getString(KEY_STT_MODEL, "whisper-1") ?: "whisper-1"
        )
    }

    private fun loadCleanupConfig(): ProviderConfig {
        return ProviderConfig(
            enabled = prefs.getBoolean(KEY_CLEANUP_ENABLED, true),
            baseUrl = prefs.getString(KEY_CLEANUP_BASE_URL, "https://api.openai.com") ?: "https://api.openai.com",
            apiKey = EncryptedKeyStoreManager.decrypt(prefs.getString(KEY_CLEANUP_API_KEY, "") ?: ""),
            model = prefs.getString(KEY_CLEANUP_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        )
    }

    private fun loadActiveMode(): AssistMode {
        val modeStr = prefs.getString(KEY_ACTIVE_MODE, AssistMode.WHISPER.name)
        return try {
            AssistMode.valueOf(modeStr ?: AssistMode.WHISPER.name)
        } catch (e: Exception) {
            AssistMode.WHISPER
        }
    }

    private fun loadSaveToClipboard(): Boolean = prefs.getBoolean(KEY_SAVE_TO_CLIPBOARD, false)
    private fun loadActiveToneId(): String = prefs.getString(KEY_ACTIVE_TONE_ID, "casual") ?: "casual"
    private fun loadShareHandoffMode(): Boolean = prefs.getBoolean(KEY_SHARE_HANDOFF, false)
    private fun loadOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    private fun loadThemeMode(): String = prefs.getString(KEY_THEME_MODE, "System") ?: "System"
    private fun loadShowBubbleAlerts(): Boolean = prefs.getBoolean(KEY_SHOW_BUBBLE_ALERTS, true)
    private fun loadCustomReplyPrompt(): String = prefs.getString(KEY_CUSTOM_REPLY_PROMPT, "") ?: ""
    private fun loadPrioritizeCustomPrompt(): Boolean = prefs.getBoolean(KEY_PRIORITIZE_CUSTOM_PROMPT, false)
    private fun loadAppendMode(): Boolean = prefs.getBoolean(KEY_APPEND_MODE, false)

    private fun loadTonePresets(): List<TonePreset> {
        val rawJson = prefs.getString(KEY_TONE_PRESETS, null)
        if (rawJson.isNullOrBlank()) {
            return TonePreset.DEFAULT_PRESETS
        }
        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<TonePreset>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val isBuiltIn = obj.optBoolean("isBuiltIn", false)
                val defaultBuiltIn = if (isBuiltIn) TonePreset.DEFAULT_PRESETS.find { it.id == id } else null
                list.add(
                    TonePreset(
                        id = id,
                        name = obj.getString("name"),
                        systemPrompt = defaultBuiltIn?.systemPrompt ?: obj.getString("systemPrompt"),
                        isBuiltIn = isBuiltIn
                    )
                )
            }
            if (list.isEmpty()) TonePreset.DEFAULT_PRESETS else list
        } catch (e: Exception) {
            TonePreset.DEFAULT_PRESETS
        }
    }

    fun saveSttConfig(config: ProviderConfig) {
        prefs.edit()
            .putBoolean(KEY_STT_ENABLED, config.enabled)
            .putString(KEY_STT_BASE_URL, config.baseUrl)
            .putString(KEY_STT_API_KEY, EncryptedKeyStoreManager.encrypt(config.apiKey))
            .putString(KEY_STT_MODEL, config.model)
            .apply()
        _sttConfig.value = config
    }

    fun saveCleanupConfig(config: ProviderConfig) {
        prefs.edit()
            .putBoolean(KEY_CLEANUP_ENABLED, config.enabled)
            .putString(KEY_CLEANUP_BASE_URL, config.baseUrl)
            .putString(KEY_CLEANUP_API_KEY, EncryptedKeyStoreManager.encrypt(config.apiKey))
            .putString(KEY_CLEANUP_MODEL, config.model)
            .apply()
        _cleanupConfig.value = config
    }

    fun saveReplyConfig(config: ProviderConfig) {
        prefs.edit()
            .putBoolean(KEY_REPLY_ENABLED, config.enabled)
            .putString(KEY_REPLY_BASE_URL, config.baseUrl)
            .putString(KEY_REPLY_API_KEY, EncryptedKeyStoreManager.encrypt(config.apiKey))
            .putString(KEY_REPLY_MODEL, config.model)
            .apply()
    }

    fun setActiveMode(mode: AssistMode) {
        prefs.edit().putString(KEY_ACTIVE_MODE, mode.name).apply()
        _activeMode.value = mode
    }

    fun setSaveToClipboard(save: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_TO_CLIPBOARD, save).apply()
        _saveToClipboard.value = save
    }

    fun setActiveToneId(toneId: String) {
        prefs.edit().putString(KEY_ACTIVE_TONE_ID, toneId).apply()
        _activeToneId.value = toneId
    }

    fun setShareHandoffMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHARE_HANDOFF, enabled).apply()
        _shareHandoffMode.value = enabled
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
        _onboardingCompleted.value = completed
    }

    fun setThemeMode(theme: String) {
        prefs.edit().putString(KEY_THEME_MODE, theme).apply()
        _themeMode.value = theme
    }

    fun setShowBubbleAlerts(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_BUBBLE_ALERTS, show).apply()
        _showBubbleAlerts.value = show
    }

    fun setCustomReplyPrompt(prompt: String) {
        prefs.edit().putString(KEY_CUSTOM_REPLY_PROMPT, prompt).apply()
        _customReplyPrompt.value = prompt
    }

    fun setPrioritizeCustomPrompt(prioritize: Boolean) {
        prefs.edit().putBoolean(KEY_PRIORITIZE_CUSTOM_PROMPT, prioritize).apply()
        _prioritizeCustomPrompt.value = prioritize
    }

    fun setAppendMode(append: Boolean) {
        prefs.edit().putBoolean(KEY_APPEND_MODE, append).apply()
        _appendMode.value = append
    }

    fun saveTonePresets(presets: List<TonePreset>) {
        val jsonArray = JSONArray()
        for (preset in presets) {
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("systemPrompt", preset.systemPrompt)
                put("isBuiltIn", preset.isBuiltIn)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_TONE_PRESETS, jsonArray.toString()).apply()
        _tonePresets.value = presets
    }

    fun addOrUpdateTonePreset(preset: TonePreset) {
        val current = _tonePresets.value.toMutableList()
        val index = current.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            current[index] = preset
        } else {
            current.add(preset)
        }
        saveTonePresets(current)
    }

    fun deleteTonePreset(presetId: String) {
        val current = _tonePresets.value.filter { it.id != presetId }
        saveTonePresets(current)
        if (_activeToneId.value == presetId) {
            setActiveToneId(current.firstOrNull()?.id ?: "casual")
        }
    }

    // --- Provider Presets Management ---
    private fun loadCustomPresets(): List<ProviderPreset> {
        val raw = prefs.getString(KEY_CUSTOM_PROVIDER_PRESETS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ProviderPreset>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val encKey = obj.optString("apiKeyEnc", "")
                val plainKey = if (encKey.isNotBlank()) EncryptedKeyStoreManager.decrypt(encKey) else obj.optString("apiKey", "")
                list.add(
                    ProviderPreset(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        baseUrl = obj.getString("baseUrl"),
                        defaultModel = obj.getString("defaultModel"),
                        apiKey = plainKey,
                        category = obj.optString("category", "all"),
                        tag = obj.optString("tag", "Custom"),
                        isCustom = true,
                        docUrl = obj.optString("docUrl", ""),
                        suggestedTemperature = obj.optDouble("suggestedTemperature", 0.7),
                        suggestedMaxTokens = obj.optInt("suggestedMaxTokens", 1000)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomPresets(presets: List<ProviderPreset>) {
        val arr = JSONArray()
        for (p in presets) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("baseUrl", p.baseUrl)
                put("defaultModel", p.defaultModel)
                put("apiKeyEnc", EncryptedKeyStoreManager.encrypt(p.apiKey))
                put("category", p.category)
                put("tag", p.tag)
                put("isCustom", true)
                put("docUrl", p.docUrl)
                put("suggestedTemperature", p.suggestedTemperature)
                put("suggestedMaxTokens", p.suggestedMaxTokens)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_PROVIDER_PRESETS, arr.toString()).apply()
        _customPresets.value = presets
    }

    fun addCustomPreset(preset: ProviderPreset) {
        val current = _customPresets.value.toMutableList()
        current.add(preset.copy(isCustom = true))
        saveCustomPresets(current)
    }

    fun updateCustomPreset(preset: ProviderPreset) {
        val current = _customPresets.value.map { if (it.id == preset.id) preset.copy(isCustom = true) else it }
        saveCustomPresets(current)
    }

    fun deleteCustomPreset(presetId: String) {
        val current = _customPresets.value.filter { it.id != presetId }
        saveCustomPresets(current)
    }

    fun getAllAvailablePresets(categoryId: String? = null): List<ProviderPreset> {
        val builtIn = if (categoryId != null && categoryId != "all") {
            ModelCategoryConfig.getPresetsForCategory(categoryId)
        } else {
            ModelCategoryConfig.getAllBuiltInPresets()
        }
        val customs = if (categoryId != null && categoryId != "all") {
            _customPresets.value.filter { it.category == "all" || it.category == categoryId }
        } else {
            _customPresets.value
        }
        return (customs + builtIn).distinctBy { it.name.lowercase() + it.baseUrl + it.defaultModel }
    }

    fun applyPresetToPrimary(categoryId: String, preset: ProviderPreset) {
        when (categoryId) {
            ModelCategoryConfig.CATEGORY_EXTRACTOR -> {
                val cur = _extractorConfig.value
                val updated = cur.copy(
                    primaryBaseUrl = preset.baseUrl,
                    primaryModel = preset.defaultModel,
                    primaryApiKey = if (preset.apiKey.isNotBlank()) preset.apiKey else cur.primaryApiKey
                )
                saveExtractorConfig(updated)
            }
            ModelCategoryConfig.CATEGORY_SUMMARIZER -> {
                val cur = _summarizerConfig.value
                val updated = cur.copy(
                    primaryBaseUrl = preset.baseUrl,
                    primaryModel = preset.defaultModel,
                    primaryApiKey = if (preset.apiKey.isNotBlank()) preset.apiKey else cur.primaryApiKey
                )
                saveSummarizerConfig(updated)
            }
            ModelCategoryConfig.CATEGORY_REPLY -> {
                val cur = _replyModelConfig.value
                val updated = cur.copy(
                    primaryBaseUrl = preset.baseUrl,
                    primaryModel = preset.defaultModel,
                    primaryApiKey = if (preset.apiKey.isNotBlank()) preset.apiKey else cur.primaryApiKey
                )
                saveReplyModelConfig(updated)
            }
            "stt" -> {
                val cur = _sttConfig.value
                val updated = cur.copy(
                    baseUrl = preset.baseUrl,
                    model = preset.defaultModel,
                    apiKey = if (preset.apiKey.isNotBlank()) preset.apiKey else cur.apiKey
                )
                saveSttConfig(updated)
            }
            "cleanup" -> {
                val cur = _cleanupConfig.value
                val updated = cur.copy(
                    baseUrl = preset.baseUrl,
                    model = preset.defaultModel,
                    apiKey = if (preset.apiKey.isNotBlank()) preset.apiKey else cur.apiKey
                )
                saveCleanupConfig(updated)
            }
        }
    }

    fun addPresetToFallback(categoryId: String, preset: ProviderPreset) {
        val fb = FallbackEndpoint(
            id = UUID.randomUUID().toString(),
            name = preset.name,
            baseUrl = preset.baseUrl,
            apiKey = preset.apiKey,
            model = preset.defaultModel,
            enabled = true
        )
        addFallbackToCategory(categoryId, fb)
    }

    /**
     * One-Tap Balanced Multi-Provider Fallback Pool Setup (Requirement 1.C.3):
     * Populates Primary (Gemini 2.5 Flash) and sequential Fallback chain
     * (Claude 3.5 Haiku -> Groq Llama 3.3 -> DeepSeek Chat) while preserving user API keys.
     */
    fun loadBalancedPresetPoolForCategory(categoryId: String, preserveApiKeys: Boolean = true) {
        val primaryPreset = ModelCategoryConfig.getBalancedPrimaryPreset(categoryId)
        val fallbacks = ModelCategoryConfig.getBalancedFallbackChain(categoryId)

        when (categoryId) {
            ModelCategoryConfig.CATEGORY_EXTRACTOR -> {
                val cur = _extractorConfig.value
                val primaryKey = if (preserveApiKeys && cur.primaryApiKey.isNotBlank()) cur.primaryApiKey else primaryPreset.apiKey
                val updatedFallbacks = if (preserveApiKeys) {
                    fallbacks.map { fb ->
                        val existingMatch = cur.fallbacks.find { it.baseUrl.equals(fb.baseUrl, ignoreCase = true) || it.name.equals(fb.name, ignoreCase = true) }
                        if (existingMatch != null && existingMatch.apiKey.isNotBlank()) fb.copy(apiKey = existingMatch.apiKey) else fb
                    }
                } else fallbacks
                saveExtractorConfig(cur.copy(
                    primaryBaseUrl = primaryPreset.baseUrl,
                    primaryModel = primaryPreset.defaultModel,
                    primaryApiKey = primaryKey,
                    fallbacks = updatedFallbacks
                ))
            }
            ModelCategoryConfig.CATEGORY_SUMMARIZER -> {
                val cur = _summarizerConfig.value
                val primaryKey = if (preserveApiKeys && cur.primaryApiKey.isNotBlank()) cur.primaryApiKey else primaryPreset.apiKey
                val updatedFallbacks = if (preserveApiKeys) {
                    fallbacks.map { fb ->
                        val existingMatch = cur.fallbacks.find { it.baseUrl.equals(fb.baseUrl, ignoreCase = true) || it.name.equals(fb.name, ignoreCase = true) }
                        if (existingMatch != null && existingMatch.apiKey.isNotBlank()) fb.copy(apiKey = existingMatch.apiKey) else fb
                    }
                } else fallbacks
                saveSummarizerConfig(cur.copy(
                    primaryBaseUrl = primaryPreset.baseUrl,
                    primaryModel = primaryPreset.defaultModel,
                    primaryApiKey = primaryKey,
                    fallbacks = updatedFallbacks
                ))
            }
            ModelCategoryConfig.CATEGORY_REPLY -> {
                val cur = _replyModelConfig.value
                val primaryKey = if (preserveApiKeys && cur.primaryApiKey.isNotBlank()) cur.primaryApiKey else primaryPreset.apiKey
                val updatedFallbacks = if (preserveApiKeys) {
                    fallbacks.map { fb ->
                        val existingMatch = cur.fallbacks.find { it.baseUrl.equals(fb.baseUrl, ignoreCase = true) || it.name.equals(fb.name, ignoreCase = true) }
                        if (existingMatch != null && existingMatch.apiKey.isNotBlank()) fb.copy(apiKey = existingMatch.apiKey) else fb
                    }
                } else fallbacks
                saveReplyModelConfig(cur.copy(
                    primaryBaseUrl = primaryPreset.baseUrl,
                    primaryModel = primaryPreset.defaultModel,
                    primaryApiKey = primaryKey,
                    fallbacks = updatedFallbacks
                ))
            }
            "stt" -> {
                val cur = _sttConfig.value
                val primaryKey = if (preserveApiKeys && cur.apiKey.isNotBlank()) cur.apiKey else primaryPreset.apiKey
                saveSttConfig(cur.copy(
                    baseUrl = primaryPreset.baseUrl,
                    model = primaryPreset.defaultModel,
                    apiKey = primaryKey
                ))
            }
        }
    }

    /**
     * One-Tap Setup for all categories at once:
     * Configures the balanced multi-provider fallback pool across Reply, Extractor, Summarizer, and STT.
     */
    fun loadBalancedPresetPoolForAllCategories(preserveApiKeys: Boolean = true) {
        loadBalancedPresetPoolForCategory(ModelCategoryConfig.CATEGORY_EXTRACTOR, preserveApiKeys)
        loadBalancedPresetPoolForCategory(ModelCategoryConfig.CATEGORY_SUMMARIZER, preserveApiKeys)
        loadBalancedPresetPoolForCategory(ModelCategoryConfig.CATEGORY_REPLY, preserveApiKeys)
        loadBalancedPresetPoolForCategory("stt", preserveApiKeys)
    }

    // --- Extractor Accessibility Mode ---
    private fun loadUseAccessibilityExtractor(): Boolean =
        prefs.getBoolean(KEY_USE_ACCESSIBILITY_EXTRACTOR, false)

    fun setUseAccessibilityExtractor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_ACCESSIBILITY_EXTRACTOR, enabled).apply()
        _useAccessibilityExtractor.value = enabled
    }

    fun exportConfigJson(): String {
        val root = JSONObject()
        root.put("version", 3)
        root.put("exportTimestamp", System.currentTimeMillis())
        root.put("activeMode", _activeMode.value.name)
        root.put("saveToClipboard", _saveToClipboard.value)
        root.put("appendMode", _appendMode.value)
        root.put("activeToneId", _activeToneId.value)
        root.put("themeMode", _themeMode.value)
        root.put("showBubbleAlerts", _showBubbleAlerts.value)
        root.put("customReplyPrompt", _customReplyPrompt.value)
        root.put("prioritizeCustomPrompt", _prioritizeCustomPrompt.value)

        // Helper to serialize category config with all fallback routes
        fun serializeCategory(config: ModelCategoryConfig): JSONObject {
            return JSONObject().apply {
                put("categoryId", config.categoryId)
                put("displayName", config.displayName)
                put("description", config.description)
                put("enabled", config.enabled)
                put("primaryBaseUrl", config.primaryBaseUrl)
                put("primaryApiKey", config.primaryApiKey)
                put("primaryModel", config.primaryModel)
                put("maxSummaryWords", config.maxSummaryWords)
                val fbArr = JSONArray()
                for (fb in config.fallbacks) {
                    fbArr.put(JSONObject().apply {
                        put("id", fb.id)
                        put("name", fb.name)
                        put("baseUrl", fb.baseUrl)
                        put("apiKey", fb.apiKey)
                        put("model", fb.model)
                        put("enabled", fb.enabled)
                    })
                }
                put("fallbacks", fbArr)
            }
        }

        // Providers & Models
        root.put("extractorConfig", serializeCategory(_extractorConfig.value))
        root.put("summarizerConfig", serializeCategory(_summarizerConfig.value))
        root.put("replyModelConfig", serializeCategory(_replyModelConfig.value))

        // Speech-To-Text (Whisper)
        root.put("sttConfig", JSONObject().apply {
            val stt = _sttConfig.value
            put("enabled", stt.enabled)
            put("baseUrl", stt.baseUrl)
            put("apiKey", stt.apiKey)
            put("model", stt.model)
        })

        // Tone Presets
        val tonesArray = JSONArray()
        _tonePresets.value.forEach { t ->
            tonesArray.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("systemPrompt", t.systemPrompt)
                put("isBuiltIn", t.isBuiltIn)
            })
        }
        root.put("tonePresets", tonesArray)

        // People Memory
        val peopleArray = JSONArray()
        _people.value.forEach { p ->
            peopleArray.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("myName", p.myName)
                put("personMemory", p.personMemory)
                put("relationshipSummary", p.relationshipSummary)
            })
        }
        root.put("people", peopleArray)
        root.put("activePersonId", _activePersonId.value)
        root.put("useAccessibilityExtractor", _useAccessibilityExtractor.value)

        // Custom Presets
        val presetsArray = JSONArray()
        _customPresets.value.forEach { p ->
            presetsArray.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("baseUrl", p.baseUrl)
                put("defaultModel", p.defaultModel)
                put("apiKey", p.apiKey)
                put("category", p.category)
                put("tag", p.tag)
                put("docUrl", p.docUrl)
                put("suggestedTemperature", p.suggestedTemperature)
                put("suggestedMaxTokens", p.suggestedMaxTokens)
            })
        }
        root.put("customPresets", presetsArray)

        return root.toString(2)
    }

    fun importConfigJson(jsonStr: String): Result<Unit> {
        return try {
            val root = JSONObject(jsonStr)
            if (root.has("activeMode")) {
                val modeStr = root.optString("activeMode")
                runCatching { AssistMode.valueOf(modeStr) }.getOrNull()?.let { setActiveMode(it) }
            }
            if (root.has("saveToClipboard")) {
                setSaveToClipboard(root.optBoolean("saveToClipboard", true))
            }
            if (root.has("appendMode")) {
                setAppendMode(root.optBoolean("appendMode", true))
            }
            if (root.has("activeToneId")) {
                setActiveToneId(root.optString("activeToneId", "casual"))
            }
            if (root.has("themeMode")) {
                setThemeMode(root.optString("themeMode", "System"))
            }
            if (root.has("showBubbleAlerts")) {
                setShowBubbleAlerts(root.optBoolean("showBubbleAlerts", true))
            }
            if (root.has("customReplyPrompt")) {
                setCustomReplyPrompt(root.optString("customReplyPrompt", ""))
            }
            if (root.has("prioritizeCustomPrompt")) {
                setPrioritizeCustomPrompt(root.optBoolean("prioritizeCustomPrompt", false))
            }
            if (root.has("useAccessibilityExtractor")) {
                setUseAccessibilityExtractor(root.optBoolean("useAccessibilityExtractor", false))
            }

            fun parseCategory(obj: JSONObject, existing: ModelCategoryConfig): ModelCategoryConfig {
                val fallbacks = if (obj.has("fallbacks")) {
                    val fbArray = obj.getJSONArray("fallbacks")
                    val parsed = mutableListOf<FallbackEndpoint>()
                    for (i in 0 until fbArray.length()) {
                        val fbObj = fbArray.optJSONObject(i) ?: continue
                        val apiKey = when {
                            fbObj.has("apiKey") -> fbObj.optString("apiKey", "")
                            fbObj.has("apiKeyEnc") -> EncryptedKeyStoreManager.decrypt(fbObj.optString("apiKeyEnc", ""))
                            else -> ""
                        }
                        parsed.add(
                            FallbackEndpoint(
                                id = fbObj.optString("id", UUID.randomUUID().toString()),
                                name = fbObj.optString("name", "Fallback API ${i + 1}"),
                                baseUrl = fbObj.optString("baseUrl", "https://api.openai.com"),
                                apiKey = apiKey,
                                model = fbObj.optString("model", "gpt-4o-mini"),
                                enabled = fbObj.optBoolean("enabled", true)
                            )
                        )
                    }
                    parsed
                } else {
                    // Backward-compatibility: if imported file is v1 or v2 without fallbacks, preserve existing fallbacks
                    existing.fallbacks
                }

                val primaryApiKey = when {
                    obj.has("primaryApiKey") -> obj.optString("primaryApiKey", existing.primaryApiKey)
                    obj.has("primaryApiKeyEnc") -> EncryptedKeyStoreManager.decrypt(obj.optString("primaryApiKeyEnc", ""))
                    else -> existing.primaryApiKey
                }

                return existing.copy(
                    enabled = obj.optBoolean("enabled", existing.enabled),
                    primaryBaseUrl = obj.optString("primaryBaseUrl", existing.primaryBaseUrl),
                    primaryApiKey = primaryApiKey,
                    primaryModel = obj.optString("primaryModel", existing.primaryModel),
                    maxSummaryWords = obj.optInt("maxSummaryWords", existing.maxSummaryWords),
                    fallbacks = fallbacks
                )
            }

            if (root.has("extractorConfig")) {
                saveExtractorConfig(parseCategory(root.getJSONObject("extractorConfig"), _extractorConfig.value))
            }
            if (root.has("summarizerConfig")) {
                saveSummarizerConfig(parseCategory(root.getJSONObject("summarizerConfig"), _summarizerConfig.value))
            }
            if (root.has("replyModelConfig")) {
                saveReplyModelConfig(parseCategory(root.getJSONObject("replyModelConfig"), _replyModelConfig.value))
            }

            if (root.has("sttConfig")) {
                val sttObj = root.getJSONObject("sttConfig")
                val existing = _sttConfig.value
                val sttKey = when {
                    sttObj.has("apiKey") -> sttObj.optString("apiKey", existing.apiKey)
                    sttObj.has("apiKeyEnc") -> EncryptedKeyStoreManager.decrypt(sttObj.optString("apiKeyEnc", ""))
                    else -> existing.apiKey
                }
                saveSttConfig(existing.copy(
                    enabled = sttObj.optBoolean("enabled", existing.enabled),
                    baseUrl = sttObj.optString("baseUrl", existing.baseUrl),
                    apiKey = sttKey,
                    model = sttObj.optString("model", existing.model)
                ))
            }

            if (root.has("customPresets")) {
                val pArray = root.getJSONArray("customPresets")
                val parsedPresets = mutableListOf<ProviderPreset>()
                for (i in 0 until pArray.length()) {
                    val pObj = pArray.getJSONObject(i)
                    parsedPresets.add(
                        ProviderPreset(
                            id = pObj.optString("id", UUID.randomUUID().toString()),
                            name = pObj.getString("name"),
                            baseUrl = pObj.getString("baseUrl"),
                            defaultModel = pObj.getString("defaultModel"),
                            apiKey = pObj.optString("apiKey", ""),
                            category = pObj.optString("category", "all"),
                            tag = pObj.optString("tag", "Custom"),
                            isCustom = true,
                            docUrl = pObj.optString("docUrl", ""),
                            suggestedTemperature = pObj.optDouble("suggestedTemperature", 0.7),
                            suggestedMaxTokens = pObj.optInt("suggestedMaxTokens", 1000)
                        )
                    )
                }
                saveCustomPresets(parsedPresets)
            }

            if (root.has("people")) {
                val array = root.getJSONArray("people")
                val list = mutableListOf<Person>()
                for (i in 0 until array.length()) {
                    val pObj = array.getJSONObject(i)
                    list.add(Person(
                        id = pObj.optString("id", UUID.randomUUID().toString()),
                        name = pObj.optString("name", "Contact"),
                        myName = pObj.optString("myName", "Me"),
                        personMemory = pObj.optString("personMemory", ""),
                        relationshipSummary = pObj.optString("relationshipSummary", "")
                    ))
                }
                if (list.isNotEmpty()) {
                    savePeople(list)
                }
            }
            if (root.has("activePersonId")) {
                setActivePersonId(root.optString("activePersonId", ""))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var instance: AppPreferencesRepository? = null

        fun getInstance(context: Context): AppPreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: AppPreferencesRepository(context.applicationContext).also { instance = it }
            }
        }

        private const val KEY_PEOPLE = "people_list_json"
        private const val KEY_ACTIVE_PERSON_ID = "active_person_id"

        private const val KEY_STT_ENABLED = "stt_enabled"
        private const val KEY_STT_BASE_URL = "stt_base_url"
        private const val KEY_STT_API_KEY = "stt_api_key_enc"
        private const val KEY_STT_MODEL = "stt_model"

        private const val KEY_CLEANUP_ENABLED = "cleanup_enabled"
        private const val KEY_CLEANUP_BASE_URL = "cleanup_base_url"
        private const val KEY_CLEANUP_API_KEY = "cleanup_api_key_enc"
        private const val KEY_CLEANUP_MODEL = "cleanup_model"

        private const val KEY_REPLY_ENABLED = "reply_enabled"
        private const val KEY_REPLY_BASE_URL = "reply_base_url"
        private const val KEY_REPLY_API_KEY = "reply_api_key_enc"
        private const val KEY_REPLY_MODEL = "reply_model"

        private const val KEY_ACTIVE_MODE = "active_mode"
        private const val KEY_SAVE_TO_CLIPBOARD = "save_to_clipboard"
        private const val KEY_ACTIVE_TONE_ID = "active_tone_id"
        private const val KEY_TONE_PRESETS = "tone_presets_json"
        private const val KEY_SHARE_HANDOFF = "share_handoff_mode"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SHOW_BUBBLE_ALERTS = "show_bubble_alerts"
        private const val KEY_CUSTOM_REPLY_PROMPT = "custom_reply_prompt"
        private const val KEY_PRIORITIZE_CUSTOM_PROMPT = "prioritize_custom_prompt"
        private const val KEY_APPEND_MODE = "append_mode"
        private const val KEY_CUSTOM_PROVIDER_PRESETS = "custom_provider_presets_json"
        private const val KEY_USE_ACCESSIBILITY_EXTRACTOR = "use_accessibility_extractor"
    }
}
