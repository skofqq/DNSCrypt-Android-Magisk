package com.skofqq.shieldy

/** Holds the splash screen until the first module status has been read. */
object AppStartGate {
    @Volatile
    var ready: Boolean = false
        private set

    fun markReady() {
        ready = true
    }
}
