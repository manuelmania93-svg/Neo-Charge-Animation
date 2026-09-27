package com.redmagic.neocharge.animation.core

class BatteryPhysicsIntegrator {

    private var currentBasePercent: Int = -1
    private var decimalAccumulator: Float = 0.05f

    fun update(raw: BatteryHardwareProvider.RawTelemetry, deltaTimeSec: Float): TelemetryState {
        if (currentBasePercent != raw.percent) {
            currentBasePercent = raw.percent
            decimalAccumulator = 0.02f
        } else {
            val speedFactor = when {
                raw.watts >= 50f -> 0.050f
                raw.watts >= 25f -> 0.030f
                raw.watts > 0f   -> 0.015f
                else             -> 0.020f
            }

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
