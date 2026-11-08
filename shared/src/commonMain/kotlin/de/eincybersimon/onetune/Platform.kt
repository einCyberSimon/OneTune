package de.eincybersimon.onetune

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform