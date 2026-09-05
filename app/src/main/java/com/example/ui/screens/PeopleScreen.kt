package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Person
import com.example.data.storage.AppPreferencesRepository
import com.example.network.OpenAiClient
import com.example.service.VoiceAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun PeopleScreen(repository: AppPreferencesRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val people by repository.people.collectAsState()
    val activePersonId by repository.activePersonId.collectAsState()
    val extractorConfig by repository.extractorConfig.collectAsState()
    val summarizerConfig by repository.summarizerConfig.collectAsState()

    val useAccessibilityExtractor by repository.useAccessibilityExtractor.collectAsState()

    var showAddPersonDialog by remember { mutableStateOf(false) }
    var personToEdit by remember { mutableStateOf<Person?>(null) }
    var personForExtraction by remember { mutableStateOf<Person?>(null) }
    var showExtractionChoiceDialog by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var isSummarizing by remember { mutableStateOf(false) }
    var extractionStatus by remember { mutableStateOf<String?>(null) }

    fun processSummarizerCheck(personId: String, currentCount: Int) {
        if (currentCount >= 8) {
            extractionStatus = "Compressing pending chats into Relationship Summary..."
            scope.launch {
                val sumRes = repository.processSummarizerIfThresholdExceeded(personId, threshold = 8)
                if (sumRes.isSuccess && sumRes.getOrNull() == true) {
                    Toast.makeText(
                        context,
                        "Latest chats exceeded 8 messages: Compressed to Relationship Summary & cleared memory!",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (sumRes.isFailure) {
                    Toast.makeText(context, "Summarizer note: ${sumRes.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
                extractionStatus = null
            }
        }
    }

    // Android Photo Picker for Screenshot extraction (Zero-permission compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && personForExtraction != null) {
            val targetPerson = personForExtraction!!
            isExtracting = true
            extractionStatus = "Analyzing chat screenshot with Extractor AI..."

            scope.launch {
                try {
                    val base64 = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            val out = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        }
                    }

                    if (base64 == null) {
                        Toast.makeText(context, "Could not read image file", Toast.LENGTH_SHORT).show()
                        isExtracting = false
                        extractionStatus = null
                        return@launch
                    }

                    val result = OpenAiClient.extractChatFromImage(
                        config = extractorConfig,
                        personName = targetPerson.name,
                        myName = targetPerson.myName,
                        imageBase64 = base64,
                        mimeType = "image/jpeg"
                    )

                    if (result.isSuccess) {
                        val extractedLines = result.getOrNull() ?: emptyList()
                        if (extractedLines.isNotEmpty()) {
                            // Helper deduplication: only adds truly new messages, allows > 8
                            val (addedCount, updatedPerson) = repository.appendDeduplicatedChatMessages(targetPerson.id, extractedLines)
                            if (addedCount > 0) {
                                Toast.makeText(
                                    context,
                                    "Added $addedCount new messages (exact duplicates skipped)!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "No duplicates added. All ${extractedLines.size} extracted messages already in memory.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            // Summarizer: if latest chat memory reached or exceeded 8 threshold, compress & clear
                            val totalChats = updatedPerson?.latestChatMemory?.size ?: 0
                            processSummarizerCheck(targetPerson.id, totalChats)
                        } else {
                            Toast.makeText(context, "No chat bubbles found in screenshot", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Extraction failed"
                        Toast.makeText(context, "Extractor error: $err", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isExtracting = false
                    extractionStatus = null
                    personForExtraction = null
                }
            }
        }
    }

    fun extractViaAccessibility(person: Person) {
        if (!VoiceAccessibilityService.isConnected.value) {
            Toast.makeText(context, "Voice Assist accessibility service is not active. Please enable it in Android Accessibility Settings.", Toast.LENGTH_LONG).show()
            return
        }
        isExtracting = true
        extractionStatus = "Extracting chat bubbles via Accessibility Service..."
        scope.launch {
            try {
                val bubbles = VoiceAccessibilityService.extractChatBubbles(person.name, person.myName)
                if (bubbles.isNotEmpty()) {
                    val (addedCount, updatedPerson) = repository.appendDeduplicatedChatMessages(person.id, bubbles)
                    if (addedCount > 0) {
                        Toast.makeText(context, "Extracted $addedCount new messages via Accessibility!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "All detected messages are already saved.", Toast.LENGTH_SHORT).show()
                    }
                    val totalChats = updatedPerson?.latestChatMemory?.size ?: 0
                    processSummarizerCheck(person.id, totalChats)
                } else {
                    Toast.makeText(context, "No chat messages found on current screen. Ensure chat app is visible.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Accessibility extraction error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isExtracting = false
                extractionStatus = null
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPersonDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Person")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Header Tag
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "PEOPLE & RELATIONSHIP MEMORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "People & Context",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Maintains Person Memory, Relationship Summary (compressed past chats), and Latest Chat Memory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Extractor Source Toggle Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Accessibility Chat Extractor",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (useAccessibilityExtractor)
                                    "Reads on-screen chats via Accessibility node coordinates (Left/Right bubbles)."
                                else
                                    "Uses Vision AI on captured chat screenshots.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = useAccessibilityExtractor,
                            onCheckedChange = { repository.setUseAccessibilityExtractor(it) }
                        )
                    }
                }
            }

            // Progress Banner if Extracting or Summarizing
            if (isExtracting || isSummarizing) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = extractionStatus ?: "AI Model processing...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // List of Persons
            items(people, key = { it.id }) { person ->
                val isActive = person.id == activePersonId

                PersonCard(
                    person = person,
                    isActive = isActive,
                    onSetActive = { repository.setActivePersonId(person.id) },
                    onEdit = { personToEdit = person },
                    onDelete = { repository.deletePerson(person.id) },
                    onExtractChats = {
                        personForExtraction = person
                        showExtractionChoiceDialog = true
                    },
                    onCompressSummary = {
                        isSummarizing = true
                        extractionStatus = "Compressing chat history into Relationship Summary..."
                        scope.launch {
                            val result = OpenAiClient.summarizeChatHistory(
                                config = summarizerConfig,
                                personName = person.name,
                                myName = person.myName,
                                existingSummary = person.relationshipSummary,
                                chatMessages = person.latestChatMemory,
                                maxWords = summarizerConfig.maxSummaryWords
                            )
                            if (result.isSuccess) {
                                val updatedSummary = result.getOrNull() ?: ""
                                val combinedSummary = if (person.relationshipSummary.isBlank()) {
                                    updatedSummary
                                } else {
                                    "${person.relationshipSummary.trim()}\n\n$updatedSummary"
                                }
                                repository.updateRelationshipSummary(person.id, combinedSummary)
                                repository.clearLatestChatMemory(person.id)
                                Toast.makeText(
                                    context,
                                    "Compressed into Relationship Summary & cleared Latest Chat Memory!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Summarizer error: ${result.exceptionOrNull()?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            isSummarizing = false
                            extractionStatus = null
                        }
                    },
                    onClearChats = {
                        repository.clearLatestChatMemory(person.id)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Extraction Choice Dialog (Vision Screenshot vs Accessibility Service)
    if (showExtractionChoiceDialog && personForExtraction != null) {
        val target = personForExtraction!!
        AlertDialog(
            onDismissRequest = {
                showExtractionChoiceDialog = false
                personForExtraction = null
            },
            title = {
                Text("Extract Chats for ${target.name}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose how to extract conversation bubbles (<${target.name}>: message and <${target.myName}>: message):",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExtractionChoiceDialog = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Upload Screenshot (Vision AI)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Pick a chat screenshot. AI analyzes Left/Right speech bubbles and deduplicates.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExtractionChoiceDialog = false
                                extractViaAccessibility(target)
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column {
                                Text("Extract Active Chat (Accessibility)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Reads text bubbles directly from the currently open chat app coordinates.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showExtractionChoiceDialog = false
                    personForExtraction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add / Edit Person Dialog
    if (showAddPersonDialog || personToEdit != null) {
        val existing = personToEdit
        var name by remember { mutableStateOf(existing?.name ?: "") }
        var myName by remember { mutableStateOf(existing?.myName ?: "Me") }
        var memory by remember { mutableStateOf(existing?.personMemory ?: "") }
        var summary by remember { mutableStateOf(existing?.relationshipSummary ?: "") }

        AlertDialog(
            onDismissRequest = {
                showAddPersonDialog = false
                personToEdit = null
            },
            title = {
                Text(if (existing == null) "Add Person & Goal" else "Edit ${existing.name}")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Person's Name") },
                        placeholder = { Text("e.g. Sarah, Alex, Mom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = myName,
                        onValueChange = { myName = it },
                        label = { Text("My Display Name") },
                        placeholder = { Text("e.g. Me, John") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = memory,
                        onValueChange = { memory = it },
                        label = { Text("Person Memory & Your Goal") },
                        placeholder = { Text("Who they are, background context, and your specific goal with them (e.g. be proactive, keep replies brief).") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                    OutlinedTextField(
                        value = summary,
                        onValueChange = { summary = it },
                        label = { Text("Relationship Summary (Previous texts)") },
                        placeholder = { Text("Compressed summary of past commitments, topics, and relationship dynamics.") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val newPerson = (existing ?: Person(name = name)).copy(
                                name = name.trim(),
                                myName = if (myName.isNotBlank()) myName.trim() else "Me",
                                personMemory = memory.trim(),
                                relationshipSummary = summary.trim()
                            )
                            repository.addOrUpdatePerson(newPerson)
                            showAddPersonDialog = false
                            personToEdit = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddPersonDialog = false
                        personToEdit = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PersonCard(
    person: Person,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExtractChats: () -> Unit,
    onCompressSummary: () -> Unit,
    onClearChats: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(isActive) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            1.5.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Person Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = person.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = person.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Active for Replies",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = "You: ${person.myName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isActive) {
                        IconButton(onClick = onSetActive) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Set Active",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Person", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }
                }
            }

            // Quick summary preview if collapsed
            if (!isExpanded && person.personMemory.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = person.personMemory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. PERSON MEMORY CARD
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "1. Person Memory & User Goal",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (person.personMemory.isNotBlank()) person.personMemory else "No person memory or goals set yet. Tap Edit to add.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 2. RELATIONSHIP SUMMARY CARD (Compressed from previous texts)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Compress,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "2. Relationship Summary",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                if (person.latestChatMemory.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = onCompressSummary,
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Summarize", fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (person.relationshipSummary.isNotBlank()) person.relationshipSummary
                                else "No relationship summary yet. Extract chat screenshots or click Summarize to compress conversation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 3. LATEST CHAT MEMORY (Last 8 Chats)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "3. Latest Chat Memory (${person.latestChatMemory.size} messages)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }

                                if (person.latestChatMemory.isNotEmpty()) {
                                    TextButton(
                                        onClick = onClearChats,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("Clear", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            if (person.latestChatMemory.size >= 8) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Threshold Reached (${person.latestChatMemory.size} msgs)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                "Messages can be compressed into Relationship Summary.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        TextButton(onClick = onCompressSummary) {
                                            Text("Summarize & Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (person.latestChatMemory.isEmpty()) {
                                Text(
                                    text = "No recent chat messages stored. Tap 'Extract Chats' below to extract conversation bubbles (<${person.name}>: message and <${person.myName}>: message).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    person.latestChatMemory.forEach { chatLine ->
                                        val isMe = chatLine.startsWith("<${person.myName}>:") || chatLine.startsWith("<Me>:")
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isMe) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier.fillMaxWidth(0.92f)
                                            ) {
                                                Text(
                                                    text = chatLine,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons: Screenshot Extraction & Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onExtractChats,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Extract Chats", fontSize = 13.sp)
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Person",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
