package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.d(TAG, "VoiceAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Track active focus or window changes if needed
    }

    override fun onInterrupt() {
        Log.d(TAG, "VoiceAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isConnected.value = false
        }
    }

    companion object {
        private const val TAG = "VoiceAccessibility"

        @Volatile
        var instance: VoiceAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /**
         * Injects text directly into the focused editable text field in the active window.
         * Falls back to recursive search and clipboard paste if ACTION_SET_TEXT is not directly accepted.
         *
         * @param text The text to insert/append.
         * @param append If true, appends to real user text (ignoring placeholders/hints).
         *               If false, completely replaces field content.
         */
        fun injectText(text: String, append: Boolean = false): Boolean {
            val service = instance ?: return false
            try {
                val focusedNode = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focusedNode != null && focusedNode.isEditable) {
                    val success = performSetText(focusedNode, text, append)
                    if (success) return true
                }

                // If findFocus failed, search active window tree for editable node
                val root = service.rootInActiveWindow ?: return false
                val editableNode = findFirstEditableNode(root)
                if (editableNode != null) {
                    val success = performSetText(editableNode, text, append)
                    if (success) return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error injecting text via accessibility", e)
            }
            return false
        }

        private fun performSetText(node: AccessibilityNodeInfo, text: String, append: Boolean): Boolean {
            val realExistingText = extractGenuineExistingText(node)
            val textToSet = if (append && realExistingText.isNotBlank()) {
                if (realExistingText.endsWith(" ") || realExistingText.endsWith("\n") || text.startsWith(" ") || text.startsWith("\n")) {
                    "$realExistingText$text"
                } else {
                    "$realExistingText $text"
                }
            } else {
                text
            }

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToSet)
            }
            val setResult = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (setResult) {
                try {
                    val selectionArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, textToSet.length)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, textToSet.length)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                } catch (e: Exception) {
                    // ignore
                }
                return true
            }

            // Fallback: copy to clipboard and trigger paste action
            val service = instance ?: return false
            return try {
                val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("VoiceAssist_Inject", text)
                clipboard.setPrimaryClip(clip)
                if (append && realExistingText.isNotEmpty()) {
                    try {
                        val selectionArgs = Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, realExistingText.length)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, realExistingText.length)
                        }
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                    } catch (e: Exception) {
                        // ignore
                    }
                } else if (!append) {
                    // If replacing, select all first so paste overwrites
                    try {
                        val rawLength = node.text?.length ?: 0
                        if (rawLength > 0) {
                            val selectAllArgs = Bundle().apply {
                                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, rawLength)
                            }
                            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectAllArgs)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Inspects the editable node and returns the user's REAL text.
         * If the node is currently displaying placeholder / hint text (e.g., "Type a message...",
         * "Message", "Add a comment", etc.), returns an empty string to prevent placeholder contamination.
         */
        private fun extractGenuineExistingText(node: AccessibilityNodeInfo): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (node.isShowingHintText) {
                    return ""
                }
            }

            val rawText = node.text?.toString() ?: return ""
            if (rawText.isBlank()) return ""

            val hint = node.hintText?.toString()?.trim()
            if (!hint.isNullOrBlank() && rawText.trim().equals(hint, ignoreCase = true)) {
                return ""
            }

            if (isCommonPlaceholderText(rawText.trim())) {
                return ""
            }

            return rawText
        }

        private fun isCommonPlaceholderText(trimmed: String): Boolean {
            val normalized = trimmed.lowercase().removeSuffix("...").removeSuffix(".").trim()
            val commonPlaceholders = setOf(
                "type a message",
                "message",
                "send a message",
                "write a message",
                "write a reply",
                "reply to",
                "reply",
                "add a comment",
                "comment",
                "start a conversation",
                "start typing",
                "type something",
                "search or type a url",
                "search",
                "compose new message",
                "text message",
                "sms"
            )
            return normalized in commonPlaceholders ||
                    normalized.startsWith("type a message") ||
                    normalized.startsWith("write a message") ||
                    normalized.startsWith("message @")
        }

        private fun findFirstEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (root.isEditable && (root.isFocused || root.isFocusable)) {
                return root
            }
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val found = findFirstEditableNode(child)
                if (found != null) return found
            }
            return null
        }

        /**
         * Collects visible message text from the current screen context for smart replies.
         */
        fun readOnScreenContext(): String {
            val service = instance ?: return ""
            return try {
                val root = service.rootInActiveWindow ?: return ""
                val collected = mutableListOf<String>()
                extractTextNodes(root, collected)

                // Filter out common system button strings and deduplicate
                val filtered = collected
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length > 1 }
                    .filterNot { isIgnoredUiText(it) }
                    .distinct()

                if (filtered.isEmpty()) return ""

                // Format lines clearly so the AI understands conversation progression
                // The last element on screen is typically the incoming message needing reply
                val recentLines = filtered.takeLast(8)
                val sb = StringBuilder()
                recentLines.forEachIndexed { index, line ->
                    if (index == recentLines.lastIndex) {
                        sb.append("[Latest Incoming Message to Reply to]: \"").append(line).append("\"\n")
                    } else {
                        sb.append("[Prior Chat Context]: ").append(line).append("\n")
                    }
                }
                sb.toString().trim()
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting on-screen text context", e)
                ""
            }
        }

        private fun extractTextNodes(node: AccessibilityNodeInfo, outList: MutableList<String>) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && !node.isEditable) {
                outList.add(text)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                extractTextNodes(child, outList)
            }
        }

        private fun isIgnoredUiText(text: String): Boolean {
            val lower = text.lowercase().trim()
            if (lower in listOf(
                "send", "cancel", "ok", "done", "search", "back", "more", "menu",
                "settings", "type a message", "message", "reply", "compose", "attach",
                "camera", "emoji", "voice message", "typing...", "online", "yesterday", "today"
            )) return true

            // Filter standalone time stamps (e.g. 10:45 AM, 14:20)
            if (lower.matches(Regex("^\\d{1,2}:\\d{2}(\\s*(am|pm))?$"))) return true

            return false
        }

        /**
         * Accurately extracts chat messages from the active chat window using accessibility node coordinates.
         * Chat apps position outgoing messages (sent by the user) on the right half of the screen,
         * and incoming messages (received from the other person) on the left half of the screen.
         *
         * Chronologically sorts bubbles top-to-bottom and outputs:
         * listOf("<personName>: message", "<myName>: message")
         */
        fun extractChatBubbles(personName: String, myName: String): List<String> {
            val service = instance ?: return emptyList()
            return try {
                val root = service.rootInActiveWindow ?: return emptyList()
                val metrics = service.resources.displayMetrics
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels

                data class RawBubble(val text: String, val top: Int, val isRightAligned: Boolean)

                val rawBubbles = mutableListOf<RawBubble>()

                fun inspectNode(node: AccessibilityNodeInfo) {
                    val text = node.text?.toString()?.trim()
                    val isEditable = node.isEditable

                    if (!text.isNullOrBlank() && !isEditable && text.length > 1 && !isIgnoredUiText(text)) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)

                        // Ignore outside typical chat viewport (system bars)
                        val topBoundary = (screenHeight * 0.04).toInt()
                        val bottomBoundary = (screenHeight * 0.94).toInt()

                        if (rect.top >= topBoundary && rect.bottom <= bottomBoundary &&
                            rect.width() > 30 && rect.height() > 15
                        ) {
                            val centerX = rect.centerX()
                            // Right aligned if center is to the right of 48% screen width, or right edge aligns with right screen border
                            val isRight = centerX >= (screenWidth * 0.48) || rect.right >= (screenWidth * 0.88)
                            rawBubbles.add(RawBubble(text, rect.top, isRight))
                        }
                    }

                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        inspectNode(child)
                    }
                }

                inspectNode(root)

                // Sort top-to-bottom for chronological timeline
                val sorted = rawBubbles.sortedBy { it.top }

                sorted.map { bubble ->
                    val sender = if (bubble.isRightAligned) myName else personName
                    "<$sender>: ${bubble.text}"
                }.distinct()
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting chat bubbles via accessibility", e)
                emptyList()
            }
        }
    }
}
