package com.example.data.model

data class ProviderConfig(
    val enabled: Boolean = true,
    val baseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val model: String = ""
)
