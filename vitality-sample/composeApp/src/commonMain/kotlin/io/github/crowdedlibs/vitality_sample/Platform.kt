package io.github.crowdedlibs.vitality_sample

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform