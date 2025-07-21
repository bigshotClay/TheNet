package com.bigshotsoftware.thenet

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class PlatformTest {

    @Test
    fun testPlatformName() {
        val platform = getPlatform()
        assertNotNull(platform.name, "Platform name should not be null")
    }
    
    @Test
    fun testMockKIntegration() {
        val mockPlatform = mockk<Platform>()
        assertNotNull(mockPlatform, "MockK should create mock objects")
    }
    
    @Test
    fun testCoroutinesSupport() = runTest {
        // Test that coroutines testing works
        val platform = getPlatform()
        assertNotNull(platform.name)
    }
}