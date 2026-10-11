package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class TransferMeasurementsTest {
    @Test fun firstCallbackHasNoSpeedOrEta() {
        assertEquals(TransferMeasurement(),TransferMeasurements().sample(1,100,10000,5000))
    }
    @Test fun insufficientElapsedTimeHasNoSpeed() {
        val meter=TransferMeasurements()
        meter.sample(1,0,10000,1000)
        assertNull(meter.sample(1,400,10000,1999).bytesPerSecond)
    }
    @Test fun speedUsesRealByteDeltaAndMonotonicElapsedTime() {
        val meter=TransferMeasurements()
        meter.sample(1,100,10000,1000)
        val measured=meter.sample(1,2100,10000,3000)
        assertEquals(1000.0,measured.bytesPerSecond!!,0.001)
        assertEquals(8L,measured.remainingSeconds)
    }
    @Test fun etaWaitsForSufficientMeasurements() {
        val meter=TransferMeasurements()
        meter.sample(1,0,10000,1000)
        val measured=meter.sample(1,1000,10000,2000)
        assertNotNull(measured.bytesPerSecond)
        assertNull(measured.remainingSeconds)
    }
    @Test fun unknownSizeNeverProducesEta() {
        val meter=TransferMeasurements()
        meter.sample(1,0,-1,0)
        assertNull(meter.sample(1,3000,-1,3000).remainingSeconds)
    }
    @Test fun completionNeverProducesNegativeEta() {
        val meter=TransferMeasurements()
        meter.sample(1,0,1000,0)
        assertNull(meter.sample(1,1100,1000,3000).remainingSeconds)
    }
    @Test fun newFileCannotReusePreviousSpeed() {
        val meter=TransferMeasurements()
        meter.sample(1,0,10000,0)
        assertNotNull(meter.sample(1,3000,10000,3000).bytesPerSecond)
        assertEquals(TransferMeasurement(),meter.sample(2,0,10000,4000))
    }
    @Test fun backwardBytesAndClockResetMeasurement() {
        val meter=TransferMeasurements()
        meter.sample(1,500,10000,3000)
        assertEquals(TransferMeasurement(),meter.sample(1,100,10000,4000))
        assertEquals(TransferMeasurement(),meter.sample(1,200,10000,2000))
    }
    @Test fun zeroMovementDoesNotInventSpeed() {
        val meter=TransferMeasurements()
        meter.sample(1,500,10000,0)
        assertNull(meter.sample(1,500,10000,5000).bytesPerSecond)
    }
    @Test fun resetRemovesPreviousSessionMeasurements() {
        val meter=TransferMeasurements()
        meter.sample(1,0,10000,0)
        meter.reset()
        assertNull(meter.sample(1,3000,10000,3000).bytesPerSecond)
    }
}
