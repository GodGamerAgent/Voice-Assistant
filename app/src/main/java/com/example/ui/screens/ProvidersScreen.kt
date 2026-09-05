package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FallbackEndpoint
import com.example.data.model.ModelCategoryConfig
import com.example.data.model.ProviderConfig
import com.example.data.model.ProviderPreset
import com.example.data.storage.AppPreferencesRepository
import java.util.UUID

@Composable
fun ProvidersScreen(repository: AppPreferencesRepository) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Sub-tab: 0 = Model Routes & Gateways, 1 = Preset Configuration Window
    var selectedSubTab by remember { mutableStateOf(0) }

    val extractorConfig by repository.extractorConfig.collectAsState()
    val summarizerConfig by repository.summarizerConfig.collectAsState()
    val replyModelConfig by repository.replyModelConfig.collectAsState()
    val sttConfig by repository.sttConfig.collectAsState()
    val customPresets by repository.customPresets.collectAsState()
    val allPresets = remember(customPresets) { repository.getAllAvailablePresets(null) }

    var sttState by remember(sttConfig) { mutableStateOf(sttConfig) }

    // Dialog state for adding / editing a fallback
    var fallbackTargetCategory by remember { mutableStateOf<String?>(null) }
    var fallbackToEdit by remember { mutableStateOf<FallbackEndpoint?>(null) }

    // Preset picker dialog state: targetCategory, forPrimary (true = primary, false = add fallback)
    var presetPickerTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // Export / Import state
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportedJson by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    fun saveAll() {
        repository.saveSttConfig(sttState)
        Toast.makeText(context, "All provider & model settings saved", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Sub-Tab Switcher: Model Routes vs Preset Configuration Window
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = {
                    Text(
                        text = "AI Model Routes",
                        fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                },
                icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Presets Window",
                            fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (selectedSubTab == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${allPresets.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                },
                icon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (selectedSubTab == 1) {
            // Presets Configuration Window
            PresetConfigurationScreen(
                repository = repository,
                onBackToRoutes = { selectedSubTab = 0 }
            )
        } else {
            // Main Providers & Routes Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Title & Description
                Column {
                    Text(
                        text = "AI Model Gateways",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Configure 3 separate AI models with independent API Keys, URLs, and multi-fallback routes with toggle support.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Action Row: Export & Import Routes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                exportedJson = repository.exportConfigJson()
                                showExportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export Routes", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                importJsonInput = ""
                                importError = null
                                showImportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import Routes", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1-Tap Balanced Multi-Provider Setup Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⚡ Balanced Multi-Provider Setup",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Gemini 2.5 Flash → Claude 3.5 Haiku → Groq Llama 3.3 → DeepSeek Chat with zero key overwrite.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    repository.loadBalancedPresetPoolForAllCategories(preserveApiKeys = true)
                                    Toast.makeText(context, "Applied balanced multi-provider fallback setup across all categories!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Apply All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 1. EXTRACTOR MODEL CARD
                CategoryModelConfigCard(
                    category = extractorConfig,
                    icon = Icons.Default.Image,
                    badge = "1. EXTRACTOR",
                    subtitle = "Vision model extracts chat bubbles from screenshots into '<Name>: <Chat>' format.",
                    onConfigUpdated = { repository.saveExtractorConfig(it) },
                    onOpenPresetPickerForPrimary = {
                        presetPickerTarget = Pair(ModelCategoryConfig.CATEGORY_EXTRACTOR, true)
                    },
                    onOpenPresetPickerForFallback = {
                        presetPickerTarget = Pair(ModelCategoryConfig.CATEGORY_EXTRACTOR, false)
                    },
                    onAddFallback = {
                        fallbackTargetCategory = ModelCategoryConfig.CATEGORY_EXTRACTOR
                        fallbackToEdit = null
                    },
                    onAddBatchPresets = {
                        val pool = ModelCategoryConfig.getStandardFallbackPool(ModelCategoryConfig.CATEGORY_EXTRACTOR)
                        repository.addBatchFallbacksToCategory(ModelCategoryConfig.CATEGORY_EXTRACTOR, pool)
                        Toast.makeText(context, "Added standard fallback pool to Extractor", Toast.LENGTH_SHORT).show()
                    },
                    onToggleFallback = { fbId, enabled ->
                        repository.toggleFallbackInCategory(ModelCategoryConfig.CATEGORY_EXTRACTOR, fbId, enabled)
                    },
                    onDuplicateFallback = { fbId ->
                        repository.duplicateFallbackInCategory(ModelCategoryConfig.CATEGORY_EXTRACTOR, fbId)
                        Toast.makeText(context, "Duplicated fallback endpoint", Toast.LENGTH_SHORT).show()
                    },
                    onReorderFallback = { fbId, moveUp ->
                        repository.reorderFallbackInCategory(ModelCategoryConfig.CATEGORY_EXTRACTOR, fbId, moveUp)
                    },
                    onDeleteFallback = { fbId ->
                        repository.deleteFallbackFromCategory(ModelCategoryConfig.CATEGORY_EXTRACTOR, fbId)
                    },
                    onEditFallback = { fb ->
                        fallbackTargetCategory = ModelCategoryConfig.CATEGORY_EXTRACTOR
                        fallbackToEdit = fb
                    }
                )

                // 2. SUMMARIZER MODEL CARD (With Max Words Setting)
                CategoryModelConfigCard(
                    category = summarizerConfig,
                    icon = Icons.Default.Compress,
                    badge = "2. SUMMARIZER",
                    subtitle = "Compresses 8 chat messages into less than n words and saves to Relationship Summary.",
                    showMaxWordsSetting = true,
                    onConfigUpdated = { repository.saveSummarizerConfig(it) },
                    onOpenPresetPickerForPrimary = {
                        presetPickerTarget = Pair(ModelCategoryConfig.CATEGORY_SUMMARIZER, true)
                    },
                    onOpenPresetPickerForFallback = {
                        presetPickerTarget = Pair(ModelCategoryConfig.CATEGORY_SUMMARIZER, false)
                    },
                    onAddFallback = {
                        fallbackTargetCategory = ModelCategoryConfig.CATEGORY_SUMMARIZER
                        fallbackToEdit = null
                    },
                    onAddBatchPresets = {
                        val pool = ModelCategoryConfig.getStandardFallbackPool(ModelCategoryConfig.CATEGORY_SUMMARIZER)
                        repository.addBatchFallbacksToCategory(ModelCategoryConfig.CATEGORY_SUMMARIZER, pool)
                        Toast.makeText(context, "Added standard fallback pool to Summarizer", Toast.LENGTH_SHORT).show()
                    },
                    onToggleFallback = { fbId, enabled ->
                        repository.toggleFallbackInCategory(ModelCategoryConfig.CATEGORY_SUMMARIZER, fbId, enabled)
                    },
                    onDuplicateFallback = { fbId ->
                        repository.duplicateFallbackInCategory(ModelCategoryConfig.CATEGORY_SUMMARIZER, fbId)
                        Toast.makeText(context, "Duplicated fallback endpoint", Toast.LENGTH_SHORT).show()
                    },
                    onReorderFallback = { fbId, moveUp ->
                        repository.reorderFallbackInCategory(ModelCategoryConfig.CATEGORY_SUMMARIZER, fbId, moveUp)
                    },
                    onDeleteFallback = { fbId ->
                        repository.deleteFallbackFromCategory(ModelCategoryConfig.CATEGORY_SUMMARIZER, fbId)
                    },
                    onEditFallback = { fb ->
                        fallbackTargetCategory = ModelCategoryConfig.CATEGORY_SUMMARIZER
                        fallbackToEdit = fb
                    }
                )

                // 3. REALTIME REPLY GENERATOR CARD
                CategoryModelConfigCard(
                    category = replyModelConfig,
                    icon = Icons.Default.Reply,
                    badge = "3. REALTIME REPLY GENERATOR",
                    subtitle = "Generates contextual replies using Latest Chat Memory, Relationship Summary, and Person Memory.",
                    onConfigUpdated = { repository.saveReplyModelConfig(it) },
                    onOpenPresetPickerForPrimary = {
                        presetPickerTarget = Pair(ModelCategoryConfig.CATEGORY_REPLY, true)
                    },
                    onOpenPresetPickerForFallback = {
                        presetPickerTarget = Pair(ModelCategoryConfig.CATEGORY_REPLY, false)
                    },
                    onAddFallback = {
                        fallbackTargetCategory = ModelCategoryConfig.CATEGORY_REPLY
                        fallbackToEdit = null
                    },
                    onAddBatchPresets = {
                        val pool = ModelCategoryConfig.getStandardFallbackPool(ModelCategoryConfig.CATEGORY_REPLY)
                        repository.addBatchFallbacksToCategory(ModelCategoryConfig.CATEGORY_REPLY, pool)
                        Toast.makeText(context, "Added standard fallback pool to Reply Model", Toast.LENGTH_SHORT).show()
                    },
                    onToggleFallback = { fbId, enabled ->
                        repository.toggleFallbackInCategory(ModelCategoryConfig.CATEGORY_REPLY, fbId, enabled)
                    },
                    onDuplicateFallback = { fbId ->
                        repository.duplicateFallbackInCategory(ModelCategoryConfig.CATEGORY_REPLY, fbId)
                        Toast.makeText(context, "Duplicated fallback endpoint", Toast.LENGTH_SHORT).show()
                    },
                    onReorderFallback = { fbId, moveUp ->
                        repository.reorderFallbackInCategory(ModelCategoryConfig.CATEGORY_REPLY, fbId, moveUp)
                    },
                    onDeleteFallback = { fbId ->
                        repository.deleteFallbackFromCategory(ModelCategoryConfig.CATEGORY_REPLY, fbId)
                    },
                    onEditFallback = { fb ->
                        fallbackTargetCategory = ModelCategoryConfig.CATEGORY_REPLY
                        fallbackToEdit = fb
                    }
                )

                // 4. SPEECH-TO-TEXT (WHISPER) CARD
                LegacyProviderCard(
                    title = "Speech-to-Text (Whisper)",
                    badge = "AUDIO TRANSCRIPTION",
                    icon = Icons.Default.Mic,
                    config = sttState,
                    defaultModel = "whisper-1",
                    onConfigChange = { sttState = it },
                    onOpenPresetPicker = {
                        presetPickerTarget = Pair("stt", true)
                    }
                )

                // Save All Button
                Button(
                    onClick = { saveAll() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save All Settings", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    // Preset Picker Dialog for fast 1-tap Main / Fallback configuration
    if (presetPickerTarget != null) {
        val (targetCat, forPrimary) = presetPickerTarget!!
        val applicablePresets = remember(targetCat, allPresets) {
            allPresets.filter { it.category == "all" || it.category == targetCat }
        }

        PresetPickerDialog(
            title = if (forPrimary) "Apply Preset to ${targetCat.uppercase()} Primary" else "Add Preset to ${targetCat.uppercase()} Fallbacks",
            presets = applicablePresets,
            onDismiss = { presetPickerTarget = null },
            onSelectPreset = { preset ->
                if (forPrimary) {
                    repository.applyPresetToPrimary(targetCat, preset)
                    if (targetCat == "stt") {
                        sttState = sttState.copy(
                            baseUrl = preset.baseUrl,
                            model = preset.defaultModel,
                            apiKey = if (preset.apiKey.isNotBlank()) preset.apiKey else sttState.apiKey
                        )
                    }
                    Toast.makeText(context, "Applied preset '${preset.name}' to $targetCat Primary!", Toast.LENGTH_SHORT).show()
                } else {
                    repository.addPresetToFallback(targetCat, preset)
                    Toast.makeText(context, "Added '${preset.name}' to $targetCat Fallback chain!", Toast.LENGTH_SHORT).show()
                }
                presetPickerTarget = null
            }
        )
    }

    // Add / Edit Fallback Endpoint Dialog
    if (fallbackTargetCategory != null) {
        val catId = fallbackTargetCategory!!
        val existing = fallbackToEdit
        val presets = remember(catId, allPresets) {
            allPresets.filter { it.category == "all" || it.category == catId }
        }
        var fbName by remember(existing) { mutableStateOf(existing?.name ?: "Fallback API ${getFallbackCount(catId, extractorConfig, summarizerConfig, replyModelConfig) + 1}") }
        var fbBaseUrl by remember(existing) { mutableStateOf(existing?.baseUrl ?: "https://api.openai.com") }
        var fbApiKey by remember(existing) { mutableStateOf(existing?.apiKey ?: "") }
        var fbModel by remember(existing) { mutableStateOf(existing?.model ?: "gpt-4o-mini") }
        var fbEnabled by remember(existing) { mutableStateOf(existing?.enabled ?: true) }
        var isApiKeyVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                fallbackTargetCategory = null
                fallbackToEdit = null
            },
            title = {
                Text(if (existing == null) "Add Fallback API Route" else "Edit Fallback API Route")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "If the primary API fails (rate limits, network failure, or invalid response), active fallbacks are tried sequentially.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Quick Provider Presets Chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Quick Presets:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = (fbName == preset.name && fbBaseUrl == preset.baseUrl),
                                    onClick = {
                                        fbName = preset.name
                                        fbBaseUrl = preset.baseUrl
                                        fbModel = preset.defaultModel
                                        if (preset.apiKey.isNotBlank()) {
                                            fbApiKey = preset.apiKey
                                        }
                                    },
                                    label = { Text(preset.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Enable / Disable Switch in Dialog
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Enable Endpoint",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (fbEnabled) "Active in fallback chain" else "Disabled / bypassed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = fbEnabled,
                                onCheckedChange = { fbEnabled = it }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = fbName,
                        onValueChange = { fbName = it },
                        label = { Text("Fallback Label / Name") },
                        placeholder = { Text("e.g. Groq, OpenRouter, DeepSeek") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fbBaseUrl,
                        onValueChange = { fbBaseUrl = it },
                        label = { Text("Provider Base URL") },
                        placeholder = { Text("https://api.openai.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fbApiKey,
                        onValueChange = { fbApiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fbModel,
                        onValueChange = { fbModel = it },
                        label = { Text("Model Name") },
                        placeholder = { Text("gpt-4o-mini") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val endpoint = FallbackEndpoint(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = fbName.trim().ifBlank { "Fallback API" },
                            baseUrl = fbBaseUrl.trim(),
                            apiKey = fbApiKey.trim(),
                            model = fbModel.trim().ifBlank { "gpt-4o-mini" },
                            enabled = fbEnabled
                        )
                        repository.saveFallbackToCategory(catId, endpoint)
                        fallbackTargetCategory = null
                        fallbackToEdit = null
                        Toast.makeText(context, "Saved fallback endpoint", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save Endpoint")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        fallbackTargetCategory = null
                        fallbackToEdit = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Config & Presets") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Complete JSON of active routes, fallbacks, and custom presets:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = exportedJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("VoiceAssist Config", exportedJson))
                    Toast.makeText(context, "Copied config JSON to clipboard!", Toast.LENGTH_SHORT).show()
                    showExportDialog = false
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Config & Presets") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste configuration JSON below:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = {
                            importJsonInput = it
                            importError = null
                        },
                        label = { Text("JSON Payload") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    if (importError != null) {
                        Text(importError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (importJsonInput.isBlank()) {
                        importError = "Please paste valid JSON"
                        return@Button
                    }
                    val result = repository.importConfigJson(importJsonInput)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Imported routes and presets successfully!", Toast.LENGTH_SHORT).show()
                        showImportDialog = false
                    } else {
                        importError = "Import failed: ${result.exceptionOrNull()?.message}"
                    }
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun getFallbackCount(
    categoryId: String,
    ext: ModelCategoryConfig,
    sum: ModelCategoryConfig,
    rep: ModelCategoryConfig
): Int {
    return when (categoryId) {
        ModelCategoryConfig.CATEGORY_EXTRACTOR -> ext.fallbacks.size
        ModelCategoryConfig.CATEGORY_SUMMARIZER -> sum.fallbacks.size
        else -> rep.fallbacks.size
    }
}

@Composable
fun CategoryModelConfigCard(
    category: ModelCategoryConfig,
    icon: ImageVector,
    badge: String,
    subtitle: String,
    showMaxWordsSetting: Boolean = false,
    onConfigUpdated: (ModelCategoryConfig) -> Unit,
    onOpenPresetPickerForPrimary: () -> Unit,
    onOpenPresetPickerForFallback: () -> Unit,
    onAddFallback: () -> Unit,
    onAddBatchPresets: () -> Unit,
    onToggleFallback: (String, Boolean) -> Unit,
    onDuplicateFallback: (String) -> Unit,
    onReorderFallback: (String, Boolean) -> Unit,
    onDeleteFallback: (String) -> Unit,
    onEditFallback: (FallbackEndpoint) -> Unit
) {
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var primaryUrl by remember(category.primaryBaseUrl) { mutableStateOf(category.primaryBaseUrl) }
    var primaryKey by remember(category.primaryApiKey) { mutableStateOf(category.primaryApiKey) }
    var primaryModel by remember(category.primaryModel) { mutableStateOf(category.primaryModel) }
    var enabled by remember(category.enabled) { mutableStateOf(category.enabled) }
    var maxWordsSlider by remember(category.maxSummaryWords) { mutableFloatStateOf(category.maxSummaryWords.toFloat()) }

    fun commitChanges() {
        onConfigUpdated(
            category.copy(
                enabled = enabled,
                primaryBaseUrl = primaryUrl.trim(),
                primaryApiKey = primaryKey.trim(),
                primaryModel = primaryModel.trim(),
                maxSummaryWords = maxWordsSlider.toInt()
            )
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        commitChanges()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = enabled) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Primary Endpoint Section
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Primary Endpoint",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                FilledTonalButton(
                                    onClick = onOpenPresetPickerForPrimary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Apply Preset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedTextField(
                                value = primaryUrl,
                                onValueChange = {
                                    primaryUrl = it
                                    commitChanges()
                                },
                                label = { Text("Base URL") },
                                placeholder = { Text("https://api.openai.com/v1") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Input,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = primaryKey,
                                onValueChange = {
                                    primaryKey = it
                                    commitChanges()
                                },
                                label = { Text("API Key") },
                                placeholder = { Text("sk-...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                        Icon(
                                            imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Visibility",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = primaryModel,
                                onValueChange = {
                                    primaryModel = it
                                    commitChanges()
                                },
                                label = { Text("Model Name") },
                                placeholder = { Text("e.g. gpt-4o-mini, gemini-1.5-flash") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Special Max Words Setting for Summarizer
                    if (showMaxWordsSetting) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Target Word Limit (n words)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "< ${maxWordsSlider.toInt()} words",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Text(
                                    text = "Every 8 messages will be compressed to less than this word count in the Relationship Summary.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = maxWordsSlider,
                                    onValueChange = {
                                        maxWordsSlider = it
                                        commitChanges()
                                    },
                                    valueRange = 25f..250f,
                                    steps = 8
                                )
                            }
                        }
                    }

                    // Fallback APIs Section (N fallbacks with Batch & Toggle support)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Header Row 1: Title, Count, and Status Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Fallback Chain",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${category.fallbacks.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            val activeCount = category.fallbacks.count { it.enabled }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (activeCount > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                if (activeCount > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "$activeCount active in failover",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Header Row 2: Action Buttons (Equal width, no truncation)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onOpenPresetPickerForFallback,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Preset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            FilledTonalButton(
                                onClick = onAddBatchPresets,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Pool", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            Button(
                                onClick = onAddFallback,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Custom", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        if (category.fallbacks.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "No fallback APIs configured yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap '+ Preset' to add from saved presets, '+ Pool' to batch-add standard fallback models, or '+ Custom' to configure manually.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            category.fallbacks.forEachIndexed { index, fb ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (fb.enabled) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (fb.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        // TOP ROW: Priority Badge, Name, Status Chip, and Toggle Switch
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (fb.enabled) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "${index + 1}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (fb.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = fb.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (fb.enabled) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                // Status Badge that NEVER wraps
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (fb.enabled) Color(0xFF1B5E20).copy(alpha = 0.15f)
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = if (fb.enabled) "ACTIVE" else "OFF",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (fb.enabled) Color(0xFF4CAF50)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            // Smooth toggle switch on the top right
                                            Switch(
                                                checked = fb.enabled,
                                                onCheckedChange = { onToggleFallback(fb.id, it) },
                                                modifier = Modifier
                                                    .scale(0.8f)
                                                    .padding(start = 6.dp),
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // MIDDLE CONTENT: Model Chip & Endpoint URL
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = fb.model,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            if (fb.apiKey.isBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                ) {
                                                    Text(
                                                        text = "No Key",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                                ) {
                                                    Text(
                                                        text = "Key Set",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = fb.baseUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // BOTTOM ROW: Clean Action Toolbar (Reorder, Duplicate, Edit, Delete)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Left: Priority Controls
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = { onReorderFallback(fb.id, true) },
                                                        enabled = index > 0,
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowUp,
                                                            contentDescription = "Move Up Priority",
                                                            modifier = Modifier.size(18.dp),
                                                            tint = if (index > 0) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.outlineVariant
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { onReorderFallback(fb.id, false) },
                                                        enabled = index < category.fallbacks.size - 1,
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowDown,
                                                            contentDescription = "Move Down Priority",
                                                            modifier = Modifier.size(18.dp),
                                                            tint = if (index < category.fallbacks.size - 1) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.outlineVariant
                                                        )
                                                    }
                                                    Text(
                                                        text = "Priority #${index + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 10.sp
                                                    )
                                                }

                                                // Right: Copy, Edit, Delete
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { onDuplicateFallback(fb.id) },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentCopy,
                                                            contentDescription = "Duplicate Fallback",
                                                            modifier = Modifier.size(15.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { onEditFallback(fb) },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit Fallback",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { onDeleteFallback(fb.id) },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Fallback",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegacyProviderCard(
    title: String,
    badge: String,
    icon: ImageVector,
    config: ProviderConfig,
    defaultModel: String,
    onConfigChange: (ProviderConfig) -> Unit,
    onOpenPresetPicker: () -> Unit
) {
    var isApiKeyVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            AnimatedVisibility(visible = config.enabled) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STT Endpoint",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = onOpenPresetPicker,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Apply Preset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = config.baseUrl,
                        onValueChange = { onConfigChange(config.copy(baseUrl = it)) },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Input,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = config.apiKey,
                        onValueChange = { onConfigChange(config.copy(apiKey = it)) },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = config.model,
                        onValueChange = { onConfigChange(config.copy(model = it)) },
                        label = { Text("Model Name (default: $defaultModel)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun PresetPickerDialog(
    title: String,
    presets: List<ProviderPreset>,
    onDismiss: () -> Unit,
    onSelectPreset: (ProviderPreset) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (presets.isEmpty()) {
                    Text(
                        "No presets found for this category. Switch to the Presets Window to create custom presets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    presets.forEach { preset ->
                        Surface(
                            onClick = { onSelectPreset(preset) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(preset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        if (preset.tag.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = preset.tag,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (preset.isCustom) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                            ) {
                                                Text(
                                                    text = "Custom",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${preset.defaultModel} • ${preset.baseUrl}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    if (preset.apiKey.isNotBlank()) {
                                        Text(
                                            text = "Key: ●●●●●●●● (Configured)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
