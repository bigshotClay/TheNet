package com.bigshotsoftware.thenet

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPlatformTest {

    @Test
    fun `should return Android platform name`() {
        val platform = getPlatform()
        assertTrue(
            "Platform name should contain Android",
            platform.name.contains("Android", ignoreCase = true),
        )
    }

    @Test
    fun `should work with MockK on Android`() {
        val mockPlatform = mockk<Platform>()
        every { mockPlatform.name } returns "Mocked Android"

        assertEquals("Mocked Android", mockPlatform.name)
    }

    @Test
    fun `should verify greeting contains Hello`() {
        val greeting = Greeting()
        assertTrue(
            "Greeting should contain Hello",
            greeting.greet().contains("Hello"),
        )
    }
}
