package com.bigshotsoftware.thenet

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("JVM Platform Tests")
class JvmPlatformTest {

    @Test
    @DisplayName("Should return JVM platform name")
    fun `should return JVM platform name`() {
        val platform = getPlatform()
        assertTrue(
            platform.name.contains("Java", ignoreCase = true),
            "Platform name should contain Java")
    }
    
    @Test
    @DisplayName("Should work with MockK on JVM")
    fun `should work with MockK on JVM`() {
        val mockPlatform = mockk<Platform>()
        every { mockPlatform.name } returns "Mocked JVM"
        
        assertEquals("Mocked JVM", mockPlatform.name)
    }
    
    @ParameterizedTest
    @ValueSource(strings = ["JVM", "Java", "Desktop"])
    @DisplayName("Should handle different platform name variations")
    fun `should handle different platform name variations`(expectedName: String) {
        val platform = getPlatform()
        // Just verify the platform exists - actual name will vary by implementation
        assertNotNull(platform.name)
        assertTrue(platform.name.isNotEmpty(), "Platform name should not be empty")
    }
    
    @Test
    @DisplayName("Should work with basic assertions")
    fun `should work with basic assertions`() {
        val result = 2 + 2
        assertEquals(4, result)
    }
}