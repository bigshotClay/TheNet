package com.bigshotsoftware.thenet.android

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

interface TestService {
    fun getValue(): String
}

class MainActivityTest {

    @Test
    fun `framework integration should work`() {
        // Test basic functionality
        val result = 2 + 2
        assertEquals(4, result)
    }

    @Test
    fun `MockK should work in Android app tests`() {
        val mockService = mockk<TestService>()
        every { mockService.getValue() } returns "android_mocked"

        assertEquals("android_mocked", mockService.getValue())
    }
}
