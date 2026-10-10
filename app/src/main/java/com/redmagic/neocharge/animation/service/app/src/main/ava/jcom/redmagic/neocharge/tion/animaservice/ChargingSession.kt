package com.redmagic.neocharge.animation.service

/** Main-thread session owner. Timers cannot outlive the session that scheduled them. */
internal class ChargingSession(
    private val showOverlay: () -> Boolean,
    private val beginUpdates: () -> Unit,
    private val endUpdates: () -> Unit,
    private val dismissOverlay: () -> Unit,
    private val acquireScreenWakeLock: () -> Unit,
    private val releaseScreenWakeLock: () -> Unit,
    private val scheduleTimeout: (Runnable, Long) -> Unit,
    private val cancelTimeout: (Runnable) -> Unit
) {
    var isActive = false
        private set
    var isPreview = false
        private set
    private var timeout: Runnable? = null
    private var generation = 0L

    fun start(preview: Boolean, permanent: Boolean, connected: Boolean): Boolean {
        if (!preview && !connected) return false
        if (isActive && !(isPreview && !preview)) return true
        // Plugging in during a preview starts a fresh real session, without its old timer.
        stop()
        if (!showOverlay()) return false
        isActive = true
        isPreview = preview
        acquireScreenWakeLock()
        beginUpdates()
        val delayMs = when {
            preview -> 5_000L
            !permanent -> 7_000L
            else -> null
        }
        if (delayMs != null) {
            val sessionGeneration = generation
            val task = Runnable {
                if (isActive && generation == sessionGeneration) stop()
            }
            timeout = task
            scheduleTimeout(task, delayMs)
        }
        return true
    }

    fun stop() {
        generation++
        timeout?.let(cancelTimeout)
        timeout = null
        isActive = false
        isPreview = false
        endUpdates()
        dismissOverlay()
        releaseScreenWakeLock()
    }
}
