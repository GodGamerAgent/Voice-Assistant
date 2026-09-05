package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FallbackEndpoint
import com.example.data.model.ModelCategoryConfig
import com.example.data.storage.AppPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FallbackAndExportImportTest {

    private lateinit var context: Context
    private lateinit var repository: AppPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = AppPreferencesRepository(context)
    }

    @Test
    fun testBatchAddAndToggleFallbacks() {
        // Add a batch of standard presets to Extractor
        val presets = ModelCategoryConfig.getStandardFallbackPool(ModelCategoryConfig.CATEGORY_EXTRACTOR)
        assertTrue("Presets pool should not be empty", presets.isNotEmpty())

        repository.addBatchFallbacksToCategory(ModelCategoryConfig.CATEGORY_EXTRACTOR, presets)

        var config = repository.extractorConfig.value
        assertEquals(presets.size, config.fallbacks.size)
        assertTrue(config.fallbacks.all { it.enabled })

        // Toggle first fallback off
        val firstId = config.fallbacks[0].id
        repository.toggleFallbackInCategory(ModelCategoryConfig.CATEGORY_EXTRACTOR, firstId, false)

        config = repository.extractorConfig.value
        val toggled = config.fallbacks.first { it.id == firstId }
        assertFalse("First fallback should be toggled off", toggled.enabled)

        // Duplicate second fallback
        val secondId = config.fallbacks[1].id
        repository.duplicateFallbackInCategory(ModelCategoryConfig.CATEGORY_EXTRACTOR, secondId)

        config = repository.extractorConfig.value
        assertEquals(presets.size + 1, config.fallbacks.size)
        val copy = config.fallbacks.find { it.name.contains("(Copy)") }
        assertNotNull("Duplicated fallback should exist", copy)
    }

    @Test
    fun testExportAndImportConfigCompatibility() {
        // Set custom fallback
        val customFb = FallbackEndpoint(
            name = "Test Route",
            baseUrl = "https://api.test.com",
            apiKey = "sk-test-key",
            model = "test-model-v1",
            enabled = false
        )
        repository.addFallbackToCategory(ModelCategoryConfig.CATEGORY_REPLY, customFb)

        // Export config
        val exported = repository.exportConfigJson()
        assertTrue("Exported JSON should contain version 3", exported.contains("\"version\": 3") || exported.contains("\"version\":3"))
        assertTrue("Exported JSON should contain fallbacks", exported.contains("test-model-v1"))

        // Import config
        val result = repository.importConfigJson(exported)
        assertTrue("Import should succeed", result.isSuccess)

        val replyConfig = repository.replyModelConfig.value
        val importedFb = replyConfig.fallbacks.find { it.model == "test-model-v1" }
        assertNotNull("Imported fallback should exist", importedFb)
        assertEquals("Test Route", importedFb?.name)
        assertFalse("Enabled flag should be preserved as false", importedFb?.enabled ?: true)
    }

    @Test
    fun testBalancedFallbackPoolSetup() {
        // Run balanced setup across all categories
        repository.loadBalancedPresetPoolForAllCategories(preserveApiKeys = false)

        val reply = repository.replyModelConfig.value
        val extractor = repository.extractorConfig.value
        val summarizer = repository.summarizerConfig.value

        assertEquals("gemini-2.5-flash", reply.primaryModel)
        assertEquals("gemini-2.5-flash", extractor.primaryModel)
        assertEquals("gemini-2.5-flash", summarizer.primaryModel)

        assertTrue("Reply should have multiple fallbacks", reply.fallbacks.size >= 3)
        assertTrue("Extractor should have vision fallbacks", extractor.fallbacks.size >= 3)
        assertTrue("Summarizer should have fallbacks", summarizer.fallbacks.size >= 3)

        // Verify Claude, Groq, DeepSeek present in reply fallback chain
        assertTrue(reply.fallbacks.any { it.name.contains("Claude") })
        assertTrue(reply.fallbacks.any { it.name.contains("Groq") })
        assertTrue(reply.fallbacks.any { it.name.contains("DeepSeek") })
    }
}
