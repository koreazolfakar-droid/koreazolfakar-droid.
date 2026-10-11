package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class TransferProgressTest {
    @Test fun unknownSizeIsIndeterminate() {
        assertEquals(0,TransferProgress("file","Sending",100,-1).percent)
    }
    @Test fun oversizedCountersAreClamped() {
        assertEquals(100,TransferProgress("file","Sending",200,100).percent)
        assertEquals(0,TransferProgress("file","Sending",-1,100).percent)
    }
    @Test fun enormousFilesDoNotOverflowPercentage() {
        assertEquals(50,TransferProgress("file","Sending",Long.MAX_VALUE/2,Long.MAX_VALUE).percent)
    }
    @Test fun finishedFilesAreCompleteAndUnfinishedAreRunning() {
        assertEquals(TransferOutcome.Complete,TransferProgress("file","Received",10,10,true).outcome)
        assertEquals(TransferOutcome.Running,TransferProgress("file","Sending",0,10).outcome)
    }
}
