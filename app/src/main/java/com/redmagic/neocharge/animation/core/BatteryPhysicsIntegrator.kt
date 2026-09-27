package com.redmagic.neocharge.animation.core

class BatteryPhysicsIntegrator {

    private var currentBasePercent: Int = -1
    private var decimalAccumulator: Float = 0.05f

    fun update(raw: BatteryHardwareProvider.RawTelemetry, deltaTimeSec: Float): TelemetryState {
        // When real hardware gains a percent (e.g., 88% -> 89%), step seamlessly into 89.00%
        if (currentBasePercent != raw.percent) {
            currentBasePercent = raw.percent
            decimalAccumulator = 0.02f
        } else {
            // Realistic physical tick speed based on actual charging wattage
            val speedFactor = when {
                raw.watts >= 50f -> 0.050f // Fast RedMagic charging brick
                raw.watts >= 25f -> 0.030f // Standard fast charge
                raw.watts > 0f   -> 0.015f // Slow USB
                else             -> 0.020f // Test / Preview fallback
            }

            // Advance accumulator smoothly; clamp at 0.99 until hardware advances
            if (decimalAccumulator < 0.985f) {
                decimalAccumulator += speedFactor * deltaTimeSec
            }
        }

        val clampedDecimal = decimalAccumulator.coerceIn(0.00f, 0.99f)
        val combinedPercentage = currentBasePercent.toFloat() + clampedDecimal

        return TelemetryState(
            basePercent = currentBasePercent,
            displayPercentage = combinedPercentage,
            watts = raw.watts,
            tempCelsius = raw.tempCelsius,
            isConnected = raw.isPlugged,
            chargeGrade = raw.grade
        )
    }

    fun reset() {
        currentBasePercent = -1
        decimalAccumulator = 0.02f
    }
}
