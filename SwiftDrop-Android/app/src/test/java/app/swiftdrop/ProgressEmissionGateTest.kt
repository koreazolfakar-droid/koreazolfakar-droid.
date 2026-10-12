package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class ProgressEmissionGateTest {
    @Test fun firstUpdateImmediatelyVisibleAndHighFrequencyUpdatesAreThrottled() {
        val gate=ProgressEmissionGate(100)
        assertTrue(gate.shouldEmit(1,0,1000,false))
        for (t in 1001L..1099L) assertFalse(gate.shouldEmit(1,t, t,false))
        assertTrue(gate.shouldEmit(1,100,1100,false))
    }
    @Test fun terminalCallbackCannotBeDropped() {
        val gate=ProgressEmissionGate()
        gate.shouldEmit(1,0,0,false)
        assertTrue(gate.shouldEmit(1,4,1,true))
    }
    @Test fun newFileAndResetPublishImmediately() {
        val gate=ProgressEmissionGate()
        gate.shouldEmit(1,100,100,false)
        assertTrue(gate.shouldEmit(2,0,101,false))
        gate.reset()
        assertTrue(gate.shouldEmit(2,1,102,false))
    }
    @Test fun backwardsCountersOrMonotonicClockForceRefresh() {
        val gate=ProgressEmissionGate()
        gate.shouldEmit(1,100,300,false)
        assertTrue(gate.shouldEmit(1,20,301,false))
        assertTrue(gate.shouldEmit(1,21,100,false))
    }
}
