package app.swiftdrop

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyFailureTest {
    @Test fun missingCoarseLocationWithSystemLocationOffIsActionable() {
        assertEquals(NearbyFailure.LocationOff,classifyNearbyFailure(8034,false))
    }
    @Test fun missingCoarseLocationWithLocationOnNeedsServiceDiagnostics() {
        assertEquals(NearbyFailure.LocationDependency,classifyNearbyFailure(8034,true))
    }
    @Test fun missingFineLocationUsesSameRecoveryPath() {
        assertEquals(NearbyFailure.LocationOff,classifyNearbyFailure(8036,false))
    }
    @Test fun nearbyWifiPermissionHasDistinctRecovery() {
        assertEquals(NearbyFailure.MissingPermission,classifyNearbyFailure(8029,true))
    }
    @Test fun busyOrUnknownErrorsDoNotPretendToBePermissionErrors() {
        assertEquals(NearbyFailure.RadioBusy,classifyNearbyFailure(8050,true))
        assertEquals(NearbyFailure.Other,classifyNearbyFailure(8000,true))
        assertEquals(NearbyFailure.Other,classifyNearbyFailure(null,true))
    }
}
