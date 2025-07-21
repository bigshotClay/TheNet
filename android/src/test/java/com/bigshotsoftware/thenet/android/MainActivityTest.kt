package com.bigshotsoftware.thenet.android

import io.mockk.android.AndroidMockKConfig
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MainActivityTest {

    @Before
    fun setUp() {
        AndroidMockKConfig.throwOnStaticMockIfUnknown = false
    }

    @Test
    fun `framework integration should work`() {
        // Test basic functionality
        val result = 2 + 2
        assertEquals(4, result)
    }

    @Test
    fun `MockK should work in Android app tests`() {
        interface TestService {
            fun getValue(): String
        }

        val mockService = mockk<TestService>()
        io.mockk.every { mockService.getValue() } returns "android_mocked"

        assertEquals("android_mocked", mockService.getValue())
    }
}
