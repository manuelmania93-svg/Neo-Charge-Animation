package com.redmagic.neocharge.animation.service

import org.junit.Assert.*
import org.junit.Test

class ChargingSessionTest {
    private class Harness {
        var mayAttach = true
        var attached = false
        var held = false
        var acquisitions = 0
        var updates = false
        val pending = linkedMapOf<Runnable, Long>()
        val session = ChargingSession(
            showOverlay = { attached = mayAttach; mayAttach },
            beginUpdates = { updates = true },
            endUpdates = { updates = false },
            dismissOverlay = { attached = false },
            acquireScreenWakeLock = { held = true; acquisitions++ },
            releaseScreenWakeLock = { held = false },
            scheduleTimeout = { task, delay -> pending[task] = delay },
            cancelTimeout = { pending.remove(it); Unit }
        )
    }

    @Test fun deniedOverlayDoesNotAcquireWakeLockOrStartUpdates() {
        val h = Harness().apply { mayAttach = false }
        assertFalse(h.session.start(false, true, true))
        assertFalse(h.session.isActive)
        assertFalse(h.held)
        assertFalse(h.updates)
        assertEquals(0, h.acquisitions)
        assertTrue(h.pending.isEmpty())
    }

    @Test fun permanentOffDismissesAfterSevenSecondsAndReleasesWakeLock() {
        val h = Harness()
        assertTrue(h.session.start(false, false, true))
        assertEquals(7_000L, h.pending.values.single())
        h.pending.keys.single().run()
        assertFalse(h.session.isActive)
        assertFalse(h.attached)
        assertFalse(h.held)
        assertFalse(h.updates)
        assertTrue(h.pending.isEmpty())
    }

    @Test fun permanentOnHasNoDismissTimer() {
        val h = Harness()
        assertTrue(h.session.start(false, true, true))
        assertTrue(h.session.isActive)
        assertTrue(h.pending.isEmpty())
        h.session.stop()
        assertFalse(h.held)
    }

    @Test fun alreadyConnectedStartIsImmediateAndIdempotent() {
        val h = Harness()
        assertTrue(h.session.start(false, true, true))
        assertTrue(h.session.start(false, true, true))
        assertEquals(1, h.acquisitions)
        assertTrue(h.attached)
    }

    @Test fun unpluggedRealSessionDoesNotStart() {
        val h = Harness()
        assertFalse(h.session.start(false, true, false))
        assertEquals(0, h.acquisitions)
    }

    @Test fun stalePreviewTimerCannotStopRepluggedRealSession() {
        val h = Harness()
        h.session.start(true, true, false)
        val oldTimer = h.pending.keys.single()
        assertEquals(5_000L, h.pending.values.single())
        h.session.stop() // unplug
        assertTrue(h.pending.isEmpty())
        h.session.start(false, true, true) // replug before the old 5s expires
        oldTimer.run() // even an already dispatched old callback is harmless
        assertTrue(h.session.isActive)
        assertFalse(h.session.isPreview)
        assertTrue(h.held)
    }

    @Test fun pluggingInDuringPreviewPromotesToRealSession() {
        val h = Harness()
        h.session.start(true, true, false)
        val oldTimer = h.pending.keys.single()
        h.session.start(false, true, true)
        assertFalse(h.session.isPreview)
        assertTrue(h.pending.isEmpty())
        oldTimer.run()
        assertTrue(h.session.isActive)
    }

    @Test fun stopCancelsPreviewTimeoutAndReleasesResources() {
        val h = Harness()
        h.session.start(true, true, false)
        val oldTimer = h.pending.keys.single()
        h.session.stop() // tap-dismiss or service destruction
        oldTimer.run()
        assertFalse(h.session.isActive)
        assertFalse(h.attached)
        assertFalse(h.held)
        assertFalse(h.updates)
        assertTrue(h.pending.isEmpty())
    }
}
