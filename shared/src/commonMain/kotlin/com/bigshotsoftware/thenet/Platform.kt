package com.bigshotsoftware.thenet

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
