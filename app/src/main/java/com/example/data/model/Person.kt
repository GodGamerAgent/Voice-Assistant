package com.example.data.model

import java.util.UUID

/**
 * Represents a person with 3 main memory layers:
 * 1. Person Memory: Info about the person + user's goals with them
 * 2. Relationship Summary: Compressed relationship summary from previous texts
 * 3. Latest Chat Memory: Up to the last 8 chats formatted as:
 *    <Person Name>: <Their Chat>
 *    <My Name>: <My Chat>
 */
data class Person(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val myName: String = "Me",
    val personMemory: String = "",
    val relationshipSummary: String = "",
    val latestChatMemory: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAX_CHAT_MEMORY_ITEMS = 8

        val DEFAULT_PERSON = Person(
            id = "default_sarah",
            name = "Sarah",
            myName = "Me",
            personMemory = "Sarah is my senior engineering lead. Goal: communicate progress proactively, be clear and concise, avoid defensive explanations, and confirm delivery timelines promptly.",
            relationshipSummary = "We previously collaborated on the Q2 release sprint. Sarah appreciates bullet-pointed progress updates and asks to be notified early if any technical blockers arise.",
            latestChatMemory = listOf(
                "Sarah: Hey, do you have a quick update on the API integration progress?",
                "Me: Yes, the core endpoints are connected. Working on the fallback error handling now.",
                "Sarah: Great! Please make sure the unit tests cover timeout retries.",
                "Me: Absolutely, writing Robolectric tests for the retry policy right now.",
                "Sarah: Awesome, let me know once the PR is up for review."
            )
        )
    }
}
