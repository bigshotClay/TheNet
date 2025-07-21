package com.bigshotsoftware.thenet.desktop

import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

interface TestService {
    fun getValue(): String
}

@DisplayName("Desktop Application Tests")
class DesktopAppTest {

    @Nested
    @DisplayName("Framework Integration Tests")
    inner class FrameworkIntegrationTests {

        @Test
        @DisplayName("JUnit 5 integration should work")
        fun `JUnit 5 integration should work`() {
            val result = 2 + 2
            assertEquals(4, result)
        }

        @Test
        @DisplayName("MockK should work in desktop tests")
        fun `MockK should work in desktop tests`() {
            val mockService = mockk<TestService>()
            io.mockk.every { mockService.getValue() } returns "mocked"

            assertEquals("mocked", mockService.getValue())
        }

        @Test
        @DisplayName("Basic assertions should work")
        fun `Basic assertions should work`() {
            val list = listOf(1, 2, 3)
            assertEquals(3, list.size)
        }
    }
}
