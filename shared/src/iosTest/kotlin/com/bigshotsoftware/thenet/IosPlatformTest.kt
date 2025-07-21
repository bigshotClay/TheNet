package com.bigshotsoftware.thenet

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class IosPlatformTest {

    @Test
    fun testIosPlatformName() {
        val platform = getPlatform()
        assertNotNull(platform.name)
        assertTrue("Platform name should contain iOS", 
            platform.name.contains("iOS", ignoreCase = true))
    }
    
    @Test
    fun testGreetingOnIos() {
        val greeting = Greeting()
        assertTrue("Greeting should contain Hello", 
            greeting.greet().contains("Hello"))
    }
}