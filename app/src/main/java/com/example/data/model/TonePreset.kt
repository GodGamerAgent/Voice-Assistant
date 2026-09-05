package com.example.data.model

data class TonePreset(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val isBuiltIn: Boolean = false
) {
    companion object {
        val DEFAULT_PRESETS = listOf(
            TonePreset(
                id = "casual",
                name = "Casual",
                systemPrompt = "You are drafting a direct text message on behalf of the user. Output ONLY the raw response to send to the other person. Never talk to the user. Never write conversational preamble (e.g. 'Here is a reply:'). Write from the first-person perspective in a natural, friendly, warm, and concise texting style (1-2 sentences).",
                isBuiltIn = true
            ),
            TonePreset(
                id = "formal",
                name = "Formal",
                systemPrompt = "You are drafting a direct professional message on behalf of the user. Output ONLY the raw response to send to the recipient. Never talk to the user. Never write conversational filler or preamble. Compose an articulate, courteous, concise, and business-appropriate response in the first person.",
                isBuiltIn = true
            ),
            TonePreset(
                id = "rizz",
                name = "Rizz",
                systemPrompt = "You are drafting a direct text message on behalf of the user. Output ONLY the raw text to send. Never talk to the user. Never add introductory comments. Reply with clever playful banter, effortless charm, confident warmth, and magnetic humor in the first person. Keep it short and natural.",
                isBuiltIn = true
            ),
            TonePreset(
                id = "boyk",
                name = "BOYK",
                systemPrompt = "Best Of Your Knowledge: You are drafting a direct message on behalf of the user. Output ONLY the exact text to send. Never speak to the user. Deliver the unvarnished truth without sugarcoating, fluff, or corporate buzzwords. Keep it crisp, honest, and straight to the point.",
                isBuiltIn = true
            )
        )
    }
}
