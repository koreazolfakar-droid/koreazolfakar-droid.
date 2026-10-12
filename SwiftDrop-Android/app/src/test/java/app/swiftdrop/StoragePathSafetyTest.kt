package app.swiftdrop

import app.swiftdrop.ui.screens.files.withinStorageRoot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoragePathSafetyTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun browserNeverEscapesSharedStorageRoot() {
        val root = temp.newFolder("storage")
        val nested = File(root, "DCIM/Camera")
        assertTrue(withinStorageRoot(root, root))
        assertTrue(withinStorageRoot(root, nested))
        assertFalse(withinStorageRoot(root, temp.newFolder("storage-other")))
        assertFalse(withinStorageRoot(root, File(root, "../outside")))
    }
}
