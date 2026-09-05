package com.example.service

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioRecorderHelper
import com.example.data.model.AssistMode
import com.example.data.model.Person
import com.example.data.model.TonePreset
import com.example.data.storage.AppPreferencesRepository
import com.example.network.OpenAiClient
import com.example.ui.overlay.OverlayBubbleContent
import com.example.ui.overlay.OverlayState
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.hypot

/**
 * Dedicated touch container that intercepts touches when the bubble is floating
 * so dragging, single-tapping, and long-press work with 100% reliability,
 * while allowing full touch pass-through to Compose when the menu dialog is open.
 */
class OverlayTouchContainer(
    context: Context,
    private val onMove: (dx: Int, dy: Int) -> Unit,
    private val onDragStart: () -> Unit,
    private val onDragEnd: () -> Unit,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val isMenuOpenProvider: () -> Boolean
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.coerceAtLeast(14)
    private val longPressTimeout = 400L
    private val mainHandler = Handler(Looper.getMainLooper())

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isLongPressed = false

    private val longPressRunnable = Runnable {
        if (!isDragging && !isMenuOpenProvider()) {
            isLongPressed = true
            try {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } catch (e: Exception) {
                // ignore
            }
            onLongPress()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // If menu is open, let Compose handle all touch interactions
        if (isMenuOpenProvider()) {
            return false
        }
        // Intercept all touches when in bubble mode so container handles drag, tap, long-press reliably
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isMenuOpenProvider()) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                isLongPressed = false
                onDragStart()
                mainHandler.removeCallbacks(longPressRunnable)
                mainHandler.postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (!isDragging) {
                    val dist = hypot(dx.toDouble(), dy.toDouble())
                    if (dist > touchSlop) {
                        isDragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                }

                if (isDragging && !isLongPressed) {
                    onMove(dx, dy)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressRunnable)
                if (isDragging) {
                    isDragging = false
                    onDragEnd()
                } else if (!isLongPressed) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } catch (e: Exception) {
                        // ignore
                    }
                    onTap()
                }
                isLongPressed = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                if (isDragging) {
                    isDragging = false
                    onDragEnd()
                }
                isLongPressed = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

class OverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var repository: AppPreferencesRepository
    private lateinit var audioRecorder: AudioRecorderHelper

    private var overlayContainer: OverlayTouchContainer? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Overlay state
    private var overlayState by mutableStateOf(OverlayState.IDLE)
    private var audioAmplitude by mutableFloatStateOf(0f)
    private var isMenuOpen by mutableStateOf(false)
    private var statusMessage by mutableStateOf<String?>(null)

    // Bubble sizing and position tracking
    private val density by lazy { resources.displayMetrics.density }
    private val bubbleWidthPx by lazy { (64 * density).toInt() }
    private val marginPx by lazy { (10 * density).toInt() }

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var bubbleX = 0
    private var bubbleY = 0

    // Coordinates at touch down for smooth dragging
    private var downWindowX = 0
    private var downWindowY = 0

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        repository = AppPreferencesRepository.getInstance(this)
        audioRecorder = AudioRecorderHelper(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        updateScreenDimensions()
        createNotificationChannel()
        startForegroundServiceWithNotification()

        setupOverlayView()
    }

    private fun updateScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            screenWidth = windowMetrics.bounds.width()
            screenHeight = windowMetrics.bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
        if (screenWidth <= 0) screenWidth = 1080
        if (screenHeight <= 0) screenHeight = 1920
    }

    private fun startForegroundServiceWithNotification() {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            102,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupOverlayView() {
        lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner?.start()

        updateScreenDimensions()
        bubbleX = screenWidth - bubbleWidthPx - marginPx
        bubbleY = screenHeight / 3

        layoutParams = WindowManager.LayoutParams(
            bubbleWidthPx,
            bubbleWidthPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }

        composeView = ComposeView(this).apply {
            lifecycleOwner?.let {
                setViewTreeLifecycleOwner(it)
                setViewTreeViewModelStoreOwner(it)
                setViewTreeSavedStateRegistryOwner(it)
            }

            setContent {
                MyApplicationTheme {
                    val activeMode by repository.activeMode.collectAsState()
                    val saveToClipboard by repository.saveToClipboard.collectAsState()
                    val appendMode by repository.appendMode.collectAsState()
                    val activeToneId by repository.activeToneId.collectAsState()
                    val tonePresets by repository.tonePresets.collectAsState()
                    val people by repository.people.collectAsState()
                    val activePersonId by repository.activePersonId.collectAsState()
                    val customReplyPrompt by repository.customReplyPrompt.collectAsState()
                    val prioritizeCustomPrompt by repository.prioritizeCustomPrompt.collectAsState()

                    OverlayBubbleContent(
                        overlayState = overlayState,
                        activeMode = activeMode,
                        audioAmplitude = audioAmplitude,
                        isMenuOpen = isMenuOpen,
                        saveToClipboard = saveToClipboard,
                        appendMode = appendMode,
                        activeToneId = activeToneId,
                        tonePresets = tonePresets,
                        people = people,
                        activePersonId = activePersonId,
                        statusMessage = statusMessage,
                        customReplyPrompt = customReplyPrompt,
                        prioritizeCustomPrompt = prioritizeCustomPrompt,
                        onModeToggle = { mode -> repository.setActiveMode(mode) },
                        onSaveToClipboardToggle = { save -> repository.setSaveToClipboard(save) },
                        onAppendModeToggle = { append -> repository.setAppendMode(append) },
                        onToneSelect = { toneId -> repository.setActiveToneId(toneId) },
                        onPersonSelect = { pId -> repository.setActivePersonId(pId) },
                        onCustomReplyPromptChange = { text -> repository.setCustomReplyPrompt(text) },
                        onPrioritizeCustomPromptToggle = { prioritize -> repository.setPrioritizeCustomPrompt(prioritize) },
                        onGenerateReply = {
                            setMenuExpanded(false)
                            handleReplyTap()
                        },
                        onRefineMessage = { orig, inst ->
                            handleRefineMessage(orig, inst)
                        },
                        onDismissMenu = { setMenuExpanded(false) },
                        onOpenApp = {
                            val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            setMenuExpanded(false)
                        },
                        onCloseOverlay = {
                            stopSelf()
                        },
                        onRecenterBubble = {
                            recenterBubble()
                            setMenuExpanded(false)
                        }
                    )
                }
            }
        }

        overlayContainer = OverlayTouchContainer(
            context = this,
            onMove = { dx, dy ->
                bubbleX = (downWindowX + dx).coerceIn(marginPx, screenWidth - bubbleWidthPx - marginPx)
                bubbleY = (downWindowY + dy).coerceIn(marginPx * 2, screenHeight - bubbleWidthPx - marginPx * 2)
                layoutParams.x = bubbleX
                layoutParams.y = bubbleY
                try {
                    windowManager.updateViewLayout(overlayContainer, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "Error moving overlay bubble", e)
                }
            },
            onDragStart = {
                downWindowX = layoutParams.x
                downWindowY = layoutParams.y
            },
            onDragEnd = {
                // Snap to nearest screen edge (left or right)
                val targetX = if (layoutParams.x + bubbleWidthPx / 2 < screenWidth / 2) {
                    marginPx
                } else {
                    screenWidth - bubbleWidthPx - marginPx
                }
                snapToEdge(targetX)
            },
            onTap = {
                handleSingleTap()
            },
            onLongPress = {
                handleLongPress()
            },
            isMenuOpenProvider = { isMenuOpen }
        ).apply {
            lifecycleOwner?.let {
                setViewTreeLifecycleOwner(it)
                setViewTreeViewModelStoreOwner(it)
                setViewTreeSavedStateRegistryOwner(it)
            }
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        try {
            windowManager.addView(overlayContainer, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
        }
    }

    private fun snapToEdge(targetX: Int) {
        val startX = layoutParams.x
        if (startX == targetX) return
        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val currX = va.animatedValue as Int
                bubbleX = currX
                layoutParams.x = currX
                if (!isMenuOpen) {
                    try {
                        windowManager.updateViewLayout(overlayContainer, layoutParams)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
        animator.start()
    }

    private fun recenterBubble() {
        updateScreenDimensions()
        bubbleX = screenWidth - bubbleWidthPx - marginPx
        bubbleY = screenHeight / 3
        layoutParams.x = bubbleX
        layoutParams.y = bubbleY
        if (!isMenuOpen) {
            try {
                windowManager.updateViewLayout(overlayContainer, layoutParams)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun setMenuExpanded(open: Boolean) {
        if (isMenuOpen == open) return
        isMenuOpen = open
        if (open) {
            updateScreenDimensions()
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.x = 0
            layoutParams.y = 0
            try {
                windowManager.updateViewLayout(overlayContainer, layoutParams)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening overlay menu", e)
            }
        } else {
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            layoutParams.width = bubbleWidthPx
            layoutParams.height = bubbleWidthPx
            layoutParams.x = bubbleX
            layoutParams.y = bubbleY
            try {
                windowManager.updateViewLayout(overlayContainer, layoutParams)
            } catch (e: Exception) {
                Log.e(TAG, "Error closing overlay menu", e)
            }
        }
    }

    private fun handleLongPress() {
        setMenuExpanded(!isMenuOpen)
    }

    private fun handleSingleTap() {
        if (isMenuOpen) {
            setMenuExpanded(false)
            return
        }

        val mode = repository.activeMode.value
        when (mode) {
            AssistMode.WHISPER -> handleWhisperTap()
            AssistMode.REPLY -> handleReplyTap()
        }
    }

    private fun handleWhisperTap() {
        if (overlayState == OverlayState.RECORDING) {
            stopAndProcessAudio()
        } else {
            startAudioRecording()
        }
    }

    private fun startAudioRecording() {
        val audioFile = File(cacheDir, "voice_assist_input.m4a")
        val started = audioRecorder.startRecording(audioFile, serviceScope) { amp ->
            audioAmplitude = amp
        }
        if (started) {
            overlayState = OverlayState.RECORDING
            showTemporaryStatus("Listening...")
        } else {
            overlayState = OverlayState.ERROR
            showTemporaryStatus("Mic error. Check permission.")
            Toast.makeText(this, "Microphone recording failed. Please grant Mic permission in app.", Toast.LENGTH_SHORT).show()
            serviceScope.launch {
                delay(2200)
                if (overlayState == OverlayState.ERROR) {
                    overlayState = OverlayState.IDLE
                }
            }
        }
    }

    private fun stopAndProcessAudio() {
        overlayState = OverlayState.GENERATING
        showTemporaryStatus("Transcribing...")
        val recordedFile = audioRecorder.stopRecording()

        if (recordedFile == null || !recordedFile.exists() || recordedFile.length() == 0L) {
            if (repository.showBubbleAlerts.value) {
                overlayState = OverlayState.ERROR
                showTemporaryStatus("No audio recorded")
                serviceScope.launch {
                    delay(1800)
                    if (overlayState == OverlayState.ERROR) {
                        overlayState = OverlayState.IDLE
                    }
                }
            } else {
                overlayState = OverlayState.IDLE
            }
            return
        }

        serviceScope.launch {
            val sttConfig = repository.sttConfig.value
            val result = OpenAiClient.transcribeAudio(sttConfig, recordedFile)
            if (result.isFailure) {
                val err = result.exceptionOrNull()?.message ?: "Transcription failed"
                if (repository.showBubbleAlerts.value) {
                    overlayState = OverlayState.ERROR
                    showTemporaryStatus("Error: $err")
                    serviceScope.launch {
                        delay(2200)
                        if (overlayState == OverlayState.ERROR) {
                            overlayState = OverlayState.IDLE
                        }
                    }
                } else {
                    overlayState = OverlayState.IDLE
                }
                return@launch
            }

            var finalTranscript = result.getOrNull() ?: ""
            if (finalTranscript.isBlank()) {
                if (repository.showBubbleAlerts.value) {
                    overlayState = OverlayState.ERROR
                    showTemporaryStatus("Empty transcript")
                    serviceScope.launch {
                        delay(1800)
                        if (overlayState == OverlayState.ERROR) {
                            overlayState = OverlayState.IDLE
                        }
                    }
                } else {
                    overlayState = OverlayState.IDLE
                }
                return@launch
            }

            // Cleanup if enabled
            val cleanupConfig = repository.cleanupConfig.value
            if (cleanupConfig.enabled && cleanupConfig.apiKey.isNotBlank()) {
                showTemporaryStatus("Polishing text...")
                val cleanupPrompt = "You are an AI dictation editor. Clean up this raw voice transcript: fix punctuation, capitalization, grammar, and remove verbal filler words (e.g. 'um', 'uh', 'like'). Preserve the user's exact intended message. Return ONLY the polished text without quotes or explanations."
                val cleanupResult = OpenAiClient.completeChat(cleanupConfig, cleanupPrompt, finalTranscript)
                if (cleanupResult.isSuccess) {
                    val cleaned = cleanupResult.getOrNull()
                    if (!cleaned.isNullOrBlank()) {
                        finalTranscript = cleaned
                    }
                }
            }

            deliverFinalText(finalTranscript)
        }
    }

    private fun handleReplyTap() {
        if (overlayState == OverlayState.GENERATING) return
        overlayState = OverlayState.GENERATING
        showTemporaryStatus("Generating reply...")

        serviceScope.launch {
            val prioritizePrompt = repository.prioritizeCustomPrompt.value
            val customPrompt = repository.customReplyPrompt.value.trim()
            val activePerson = repository.getActivePerson() ?: Person.DEFAULT_PERSON

            // 1. Gather context & sync recent messages if accessibility extractor is enabled
            var contextText = ""
            if (!prioritizePrompt) {
                if (repository.useAccessibilityExtractor.value) {
                    val onScreenBubbles = VoiceAccessibilityService.extractChatBubbles(activePerson.name, activePerson.myName)
                    if (onScreenBubbles.isNotEmpty()) {
                        repository.appendDeduplicatedChatMessages(activePerson.id, onScreenBubbles)
                    }
                }

                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val clipItemText = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                    if (clipItemText.length > 2 && !clipItemText.startsWith("http")) {
                        contextText = "[Copied Message to Reply to]: \"$clipItemText\""
                    }
                }

                if (contextText.isBlank()) {
                    val onScreenText = VoiceAccessibilityService.readOnScreenContext()
                    if (onScreenText.isNotBlank()) {
                        contextText = onScreenText
                    }
                }
            }

            // Re-fetch active person to ensure freshly extracted messages are included
            val currentPerson = repository.people.value.find { it.id == activePerson.id } ?: activePerson

            // 2. Fetch active tone preset
            val toneId = repository.activeToneId.value
            val preset = repository.tonePresets.value.find { it.id == toneId }
                ?: repository.tonePresets.value.firstOrNull()
                ?: TonePreset.DEFAULT_PRESETS.first()

            val replyConfig = repository.replyModelConfig.value

            // 3. Construct prompt with 3-Layer Person Memory
            val personMemoryHeader = """
Target Person: ${currentPerson.name}
User Display Name: ${currentPerson.myName}

1. PERSON MEMORY & USER GOAL:
${if (currentPerson.personMemory.isNotBlank()) currentPerson.personMemory else "No specific person memory."}

2. RELATIONSHIP SUMMARY (Past Chats):
${if (currentPerson.relationshipSummary.isNotBlank()) currentPerson.relationshipSummary else "No past relationship summary."}

3. LATEST CHAT MEMORY:
${if (currentPerson.latestChatMemory.isNotEmpty()) currentPerson.latestChatMemory.joinToString("\n") else "No recent messages."}
""".trimIndent()

            val userMessage = if (prioritizePrompt) {
                val promptToSend = if (customPrompt.isNotBlank()) customPrompt else "Compose a direct reply."
                """
$personMemoryHeader

User Override Instruction:
$promptToSend

CRITICAL DIRECTIVE:
Draft the direct reply on behalf of '${currentPerson.myName}' to '${currentPerson.name}'.
Apply the specified tone: ${preset.name}.
Output ONLY the message to send. No quotes or preamble.
""".trimIndent()
            } else {
                val additionalInstruction = if (customPrompt.isNotBlank()) {
                    "\nAdditional User Instruction:\n$customPrompt\n"
                } else ""

                val baseContext = if (contextText.isNotBlank()) contextText else "[General Context]: Drafting a timely, context-aware reply."

                """
$personMemoryHeader

Current Context:
$baseContext
$additionalInstruction

CRITICAL DIRECTIVE:
You are '${currentPerson.myName}' sending a direct reply to '${currentPerson.name}'.
Align with the Person Memory goals and Relationship Summary.
Tone: ${preset.name}.
Output ONLY the raw text response to send. No preamble, no quotes.
""".trimIndent()
            }

            // Call Realtime Reply Generator with fallback support!
            val replyResult = OpenAiClient.completeChatWithCategoryFallback(
                config = replyConfig,
                systemPrompt = preset.systemPrompt,
                userContent = userMessage
            )

            if (replyResult.isFailure) {
                overlayState = OverlayState.ERROR
                val err = replyResult.exceptionOrNull()?.message ?: "Reply generation failed"
                showTemporaryStatus("Error: $err")
                delay(2400)
                overlayState = OverlayState.IDLE
                return@launch
            }

            val replyText = replyResult.getOrNull() ?: ""
            if (replyText.isBlank()) {
                overlayState = OverlayState.IDLE
                showTemporaryStatus("Empty reply")
                return@launch
            }

            // Update Latest Chat Memory with the sent reply (deduplicated)
            repository.appendDeduplicatedChatMessages(currentPerson.id, listOf("<${currentPerson.myName}>: $replyText"))

            // Trigger Summarizer if memory threshold (>= 8) is exceeded
            repository.processSummarizerIfThresholdExceeded(currentPerson.id, threshold = 8)

            deliverFinalText(replyText)
        }
    }

    private fun handleRefineMessage(originalText: String, instruction: String) {
        overlayState = OverlayState.GENERATING
        showTemporaryStatus("Refining message...")

        serviceScope.launch {
            val activePerson = repository.getActivePerson()
            val toneId = repository.activeToneId.value
            val preset = repository.tonePresets.value.find { it.id == toneId }
                ?: TonePreset.DEFAULT_PRESETS.first()
            val replyConfig = repository.replyModelConfig.value

            val personContext = if (activePerson != null) {
                "Recipient: ${activePerson.name}, Goal: ${activePerson.personMemory}"
            } else null

            val result = OpenAiClient.refineMessage(
                config = replyConfig,
                originalMessage = originalText,
                instruction = instruction,
                tonePrompt = preset.systemPrompt,
                personMemoryContext = personContext
            )

            if (result.isSuccess) {
                val refined = result.getOrNull() ?: ""
                deliverFinalText(refined)
                showTemporaryStatus("Refined & Injected!")
            } else {
                overlayState = OverlayState.ERROR
                val err = result.exceptionOrNull()?.message ?: "Refine failed"
                showTemporaryStatus("Error: $err")
                delay(2200)
                overlayState = OverlayState.IDLE
            }
        }
    }

    private suspend fun deliverFinalText(text: String) {
        // Save to clipboard if switch is on
        if (repository.saveToClipboard.value) {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("VoiceAssist_Reply", text)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                Log.e(TAG, "Error copying to clipboard", e)
            }
        }

        // Inject text via AccessibilityService with appendMode setting
        val appendMode = repository.appendMode.value
        val injected = VoiceAccessibilityService.injectText(text, append = appendMode)

        if (injected) {
            overlayState = OverlayState.SUCCESS
            showTemporaryStatus("Injected!")
        } else {
            // If injection failed, copy to clipboard as seamless fallback
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("VoiceAssist_Fallback", text)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                // ignore
            }
            overlayState = OverlayState.SUCCESS
            showTemporaryStatus("Copied to clipboard")
        }

        delay(1400)
        overlayState = OverlayState.IDLE
    }

    private val statusDismissRunnable = Runnable {
        statusMessage = null
    }

    private fun showTemporaryStatus(message: String) {
        statusMessage = message
        if (repository.showBubbleAlerts.value) {
            try {
                overlayContainer?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (e: Exception) {
                // ignore
            }
        }
        mainHandler.removeCallbacks(statusDismissRunnable)
        mainHandler.postDelayed(statusDismissRunnable, 3200)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenDimensions()
        bubbleX = bubbleX.coerceIn(marginPx, screenWidth - bubbleWidthPx - marginPx)
        bubbleY = bubbleY.coerceIn(marginPx * 2, screenHeight - bubbleWidthPx - marginPx * 2)
        if (isMenuOpen) {
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.x = 0
            layoutParams.y = 0
            try {
                windowManager.updateViewLayout(overlayContainer, layoutParams)
            } catch (e: Exception) {
                // ignore
            }
        } else {
            layoutParams.width = bubbleWidthPx
            layoutParams.height = bubbleWidthPx
            layoutParams.x = bubbleX
            layoutParams.y = bubbleY
            try {
                windowManager.updateViewLayout(overlayContainer, layoutParams)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        audioRecorder.cancelRecording()
        serviceScope.cancel()
        mainHandler.removeCallbacks(statusDismissRunnable)
        lifecycleOwner?.destroy()
        lifecycleOwner = null

        overlayContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        overlayContainer = null
        composeView = null
        Log.d(TAG, "OverlayService destroyed")
    }

    companion object {
        private const val TAG = "OverlayService"
        const val CHANNEL_ID = "voice_assist_overlay_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.example.voiceassist.STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }
}
