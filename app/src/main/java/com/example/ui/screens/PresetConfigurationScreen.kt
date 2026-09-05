package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ModelCategoryConfig
import com.example.data.model.ProviderPreset
import com.example.data.storage.AppPreferencesRepository
import com.example.network.OpenAiClient
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PresetConfigurationScreen(
    repository: AppPreferencesRepository,
    onBackToRoutes: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val customPresets by repository.customPresets.collectAsState()
    val allPresets = remember(customPresets) { repository.getAllAvailablePresets(null) }

    var selectedCategoryFilter by remember { mutableStateOf("all") }
    var presetToEdit by remember { mutableStateOf<ProviderPreset?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    var testingPresetId by remember { mutableStateOf<String?>(null) }
    var testResultDialog by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // Export / Import dialog state
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportedJson by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    val filteredPresets = remember(allPresets, selectedCategoryFilter) {
        if (selectedCategoryFilter == "all") allPresets
        else allPresets.filter { it.category == "all" || it.category == selectedCategoryFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))

            // Navigation back chip / Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "PRESETS CONFIGURATION WINDOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (onBackToRoutes != null) {
                    TextButton(onClick = onBackToRoutes) {
                        Text("← Back to AI Routes", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Preset Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Configure reusable model presets with custom URLs, models, and API keys. Enable or apply them to Main or Fallback routes in 1 tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Bar: Create Preset + Export / Import
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Custom Preset", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = {
                        exportedJson = repository.exportConfigJson()
                        showExportDialog = true
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        importJsonInput = ""
                        importError = null
                        showImportDialog = true
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        repository.loadBalancedPresetPoolForAllCategories(preserveApiKeys = true)
                        Toast.makeText(context, "Applied balanced multi-provider fallback setup across all categories!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("⚡ Balanced Pool", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf(
                    "all" to "All Categories",
                    ModelCategoryConfig.CATEGORY_REPLY to "Reply Generator",
                    ModelCategoryConfig.CATEGORY_EXTRACTOR to "Extractor",
                    ModelCategoryConfig.CATEGORY_SUMMARIZER to "Summarizer",
                    "stt" to "STT (Whisper)"
                )
                categories.forEach { (catId, label) ->
                    FilterChip(
                        selected = selectedCategoryFilter == catId,
                        onClick = { selectedCategoryFilter = catId },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }

        // Preset Items
        items(filteredPresets, key = { it.id }) { preset ->
            PresetItemCard(
                preset = preset,
                isTesting = testingPresetId == preset.id,
                onTest = {
                    testingPresetId = preset.id
                    scope.launch {
                        val result = OpenAiClient.testEndpoint(
                            baseUrl = preset.baseUrl,
                            apiKey = preset.apiKey,
                            model = preset.defaultModel
                        )
                        testingPresetId = null
                        if (result.isSuccess) {
                            testResultDialog = Pair("Test Connection Succeeded:\n${result.getOrNull()}", true)
                        } else {
                            testResultDialog = Pair("Connection Test Failed:\n${result.exceptionOrNull()?.message ?: "Unknown error"}", false)
                        }
                    }
                },
                onApplyToMain = { targetCategory ->
                    repository.applyPresetToPrimary(targetCategory, preset)
                    Toast.makeText(context, "Applied '${preset.name}' to $targetCategory Main!", Toast.LENGTH_SHORT).show()
                },
                onAddToFallback = { targetCategory ->
                    repository.addPresetToFallback(targetCategory, preset)
                    Toast.makeText(context, "Added '${preset.name}' to $targetCategory Fallback chain!", Toast.LENGTH_SHORT).show()
                },
                onEdit = {
                    presetToEdit = preset
                },
                onClone = {
                    val clone = preset.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${preset.name} (Copy)",
                        isCustom = true
                    )
                    repository.addCustomPreset(clone)
                    Toast.makeText(context, "Preset cloned!", Toast.LENGTH_SHORT).show()
                },
                onDelete = {
                    repository.deleteCustomPreset(preset.id)
                    Toast.makeText(context, "Custom preset deleted.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    // Create / Edit Preset Dialog
    if (showCreateDialog || presetToEdit != null) {
        val existing = presetToEdit
        PresetEditorDialog(
            existing = existing,
            onDismiss = {
                showCreateDialog = false
                presetToEdit = null
            },
            onSave = { updated ->
                if (existing != null && existing.isCustom) {
                    repository.updateCustomPreset(updated)
                    Toast.makeText(context, "Preset '${updated.name}' updated!", Toast.LENGTH_SHORT).show()
                } else {
                    repository.addCustomPreset(updated)
                    Toast.makeText(context, "New preset '${updated.name}' created!", Toast.LENGTH_SHORT).show()
                }
                showCreateDialog = false
                presetToEdit = null
            }
        )
    }

    // Connection Test Result Dialog
    if (testResultDialog != null) {
        val (message, isSuccess) = testResultDialog!!
        AlertDialog(
            onDismissRequest = { testResultDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(if (isSuccess) "Endpoint Verified" else "Endpoint Check Failed")
                }
            },
            text = {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(onClick = { testResultDialog = null }) {
                    Text("OK")
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
                    Text("Full configuration JSON including custom presets, fallbacks, and routes:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = exportedJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Config JSON", exportedJson))
                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
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
                    Text("Paste configuration JSON below:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = {
                            importJsonInput = it
                            importError = null
                        },
                        label = { Text("JSON Input") },
                        modifier = Modifier.fillMaxWidth(),
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
                    val res = repository.importConfigJson(importJsonInput)
                    if (res.isSuccess) {
                        Toast.makeText(context, "Configuration & presets imported successfully!", Toast.LENGTH_LONG).show()
                        showImportDialog = false
                    } else {
                        importError = "Import error: ${res.exceptionOrNull()?.message}"
                    }
                }) {
                    Text("Apply & Restore")
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

@Composable
fun PresetItemCard(
    preset: ProviderPreset,
    isTesting: Boolean,
    onTest: () -> Unit,
    onApplyToMain: (String) -> Unit,
    onAddToFallback: (String) -> Unit,
    onEdit: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit
) {
    var showMainDropdown by remember { mutableStateOf(false) }
    var showFallbackDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (preset.isCustom) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            1.dp,
            if (preset.isCustom) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Name, Tag, Custom Badge, Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (preset.isCustom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (preset.isCustom) Icons.Default.Edit else Icons.Default.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (preset.isCustom) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (preset.tag.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = preset.tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (preset.isCustom) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "Custom",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Model: ${preset.defaultModel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Edit / Clone / Delete Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (preset.isCustom) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        IconButton(onClick = onClone, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Clone as Custom", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // URL & API Key summary
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = preset.baseUrl,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (preset.apiKey.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("API Key configured (Encrypted)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (preset.docUrl.isNotBlank()) {
                        Text(
                            text = "Docs: ${preset.docUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                text = "Temp: ${preset.suggestedTemperature}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                text = "Max Tokens: ${preset.suggestedMaxTokens}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Action Row: Test | Apply to Main | Add as Fallback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Test Button
                OutlinedButton(
                    onClick = onTest,
                    enabled = !isTesting,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Test", fontSize = 12.sp)
                    }
                }

                // Apply to Main Dropdown Button
                Box {
                    FilledTonalButton(
                        onClick = { showMainDropdown = true },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Set as Main", fontSize = 12.sp)
                    }

                    DropdownMenu(
                        expanded = showMainDropdown,
                        onDismissRequest = { showMainDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Set as Reply Generator Main") },
                            onClick = {
                                showMainDropdown = false
                                onApplyToMain(ModelCategoryConfig.CATEGORY_REPLY)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Set as Extractor Main") },
                            onClick = {
                                showMainDropdown = false
                                onApplyToMain(ModelCategoryConfig.CATEGORY_EXTRACTOR)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Set as Summarizer Main") },
                            onClick = {
                                showMainDropdown = false
                                onApplyToMain(ModelCategoryConfig.CATEGORY_SUMMARIZER)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Set as STT (Whisper) Main") },
                            onClick = {
                                showMainDropdown = false
                                onApplyToMain("stt")
                            }
                        )
                    }
                }

                // Add as Fallback Dropdown Button
                Box {
                    Button(
                        onClick = { showFallbackDropdown = true },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Fallback", fontSize = 12.sp)
                    }

                    DropdownMenu(
                        expanded = showFallbackDropdown,
                        onDismissRequest = { showFallbackDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to Reply Generator Fallbacks") },
                            onClick = {
                                showFallbackDropdown = false
                                onAddToFallback(ModelCategoryConfig.CATEGORY_REPLY)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Extractor Fallbacks") },
                            onClick = {
                                showFallbackDropdown = false
                                onAddToFallback(ModelCategoryConfig.CATEGORY_EXTRACTOR)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Summarizer Fallbacks") },
                            onClick = {
                                showFallbackDropdown = false
                                onAddToFallback(ModelCategoryConfig.CATEGORY_SUMMARIZER)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetEditorDialog(
    existing: ProviderPreset?,
    onDismiss: () -> Unit,
    onSave: (ProviderPreset) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var baseUrl by remember(existing) { mutableStateOf(existing?.baseUrl ?: "https://api.openai.com") }
    var defaultModel by remember(existing) { mutableStateOf(existing?.defaultModel ?: "gpt-4o-mini") }
    var apiKey by remember(existing) { mutableStateOf(existing?.apiKey ?: "") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: "all") }
    var tag by remember(existing) { mutableStateOf(existing?.tag ?: "Custom") }
    var docUrl by remember(existing) { mutableStateOf(existing?.docUrl ?: "") }
    var suggestedTemperature by remember(existing) { mutableStateOf(existing?.suggestedTemperature?.toString() ?: "0.7") }
    var suggestedMaxTokens by remember(existing) { mutableStateOf(existing?.suggestedMaxTokens?.toString() ?: "1000") }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing != null) "Edit Preset" else "Create Custom Preset")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick URL chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Quick Provider Templates:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val templates = listOf(
                            Triple("OpenAI", "https://api.openai.com", "gpt-4o-mini"),
                            Triple("Groq", "https://api.groq.com/openai", "llama-3.3-70b-versatile"),
                            Triple("OpenRouter", "https://openrouter.ai/api", "meta-llama/llama-3.3-70b-instruct"),
                            Triple("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
                            Triple("Together", "https://api.together.xyz", "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
                            Triple("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-1.5-flash"),
                            Triple("Ollama (Local)", "http://10.0.2.2:11434", "llama3.2")
                        )
                        templates.forEach { (tplName, tplUrl, tplModel) ->
                            FilterChip(
                                selected = (baseUrl == tplUrl),
                                onClick = {
                                    if (name.isBlank() || templates.any { it.first == name }) {
                                        name = tplName
                                    }
                                    baseUrl = tplUrl
                                    defaultModel = tplModel
                                },
                                label = { Text(tplName, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name") },
                    placeholder = { Text("e.g. Groq Fast, Gemini Vision") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text("Default Model") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
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
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag / Badge Label") },
                    placeholder = { Text("Speed, Vision, Custom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = docUrl,
                    onValueChange = { docUrl = it },
                    label = { Text("Documentation URL (Optional)") },
                    placeholder = { Text("https://docs.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = suggestedTemperature,
                        onValueChange = { suggestedTemperature = it },
                        label = { Text("Temp") },
                        placeholder = { Text("0.7") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = suggestedMaxTokens,
                        onValueChange = { suggestedMaxTokens = it },
                        label = { Text("Max Tokens") },
                        placeholder = { Text("1000") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Category selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Target Category:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val cats = listOf(
                            "all" to "All Categories",
                            ModelCategoryConfig.CATEGORY_REPLY to "Reply",
                            ModelCategoryConfig.CATEGORY_EXTRACTOR to "Extractor",
                            ModelCategoryConfig.CATEGORY_SUMMARIZER to "Summarizer",
                            "stt" to "STT"
                        )
                        cats.forEach { (catId, catLabel) ->
                            FilterChip(
                                selected = category == catId,
                                onClick = { category = catId },
                                label = { Text(catLabel, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Test in-editor
                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testStatus = "Testing endpoint..."
                        scope.launch {
                            val res = OpenAiClient.testEndpoint(baseUrl, apiKey, defaultModel)
                            isTesting = false
                            testStatus = if (res.isSuccess) "✓ ${res.getOrNull()}" else "✗ ${res.exceptionOrNull()?.message}"
                        }
                    },
                    enabled = !isTesting && baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test Connection Now")
                }

                if (testStatus != null) {
                    Text(
                        text = testStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testStatus!!.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val id = existing?.id ?: UUID.randomUUID().toString()
                    val parsedTemp = suggestedTemperature.toDoubleOrNull() ?: 0.7
                    val parsedTokens = suggestedMaxTokens.toIntOrNull() ?: 1000
                    onSave(
                        ProviderPreset(
                            id = id,
                            name = name.trim(),
                            baseUrl = baseUrl.trim(),
                            defaultModel = defaultModel.trim(),
                            apiKey = apiKey.trim(),
                            category = category,
                            tag = tag.trim(),
                            isCustom = true,
                            docUrl = docUrl.trim(),
                            suggestedTemperature = parsedTemp,
                            suggestedMaxTokens = parsedTokens
                        )
                    )
                }
            ) {
                Text("Save Preset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
