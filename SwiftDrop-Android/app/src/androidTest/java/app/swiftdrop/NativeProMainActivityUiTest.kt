package app.swiftdrop

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.swiftdrop.music.SwiftDropMusicService
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** These regressions launch the actual Activity, localized owners, routing and music service. */
@RunWith(AndroidJUnit4::class)
class NativeProMainActivityUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val model get()=ViewModelProvider(compose.activity)[SwiftDropViewModel::class.java]

    private fun home(language:AppLanguage=AppLanguage.English,dark:Boolean=false) {
        compose.runOnUiThread {
            model.settings.completeOnboarding()
            model.settings.setLanguage(language)
            model.settings.setAppearance(if(dark) Appearance.Dark else Appearance.Light)
            model.manager.setPage("home")
        }
        compose.waitForIdle()
        val sendLabel=if(language==AppLanguage.Arabic) "إرسال ملفات" else "Send files"
        compose.waitUntil(10_000) { compose.onAllNodesWithText(sendLabel).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openBrowserFolder(name:String) {
        // Storage is loaded on Dispatchers.IO; a lazy row may be outside composition.
        compose.waitUntil(15_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
    }
    private fun <T> musicState(block:()->T):T {
        val result=java.util.concurrent.atomic.AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(block()) }
        return result.get()
    }
    private fun screenshot(name:String) {
        if(android.os.Build.VERSION.SDK_INT<29) return
        compose.waitForIdle()
        // Let the platform window finish its short dialog transition before capture.
        Thread.sleep(250)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        // AGP uninstalls the app after connected tests; app-owned directories are removed.
        // Public, test-only MediaStore downloads survive so CI can retrieve the evidence.
        val resolver=compose.activity.contentResolver
        val uri=resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"$name.png")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/SwiftDrop-visual-qa")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
            }) ?: error("Cannot store visual QA screenshot")
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            ?: error("Cannot write visual QA screenshot")
        resolver.update(uri,android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,0)
        },null,null)
        bitmap.recycle()
    }

    @After fun restorePreferences() {
        compose.runOnUiThread {
            model.settings.setLanguage(AppLanguage.English)
            model.settings.setAppearance(Appearance.System)
            compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Test fun everyHomeCategoryOpensFromTheRealActivityInBothLanguages() {
        val entries=listOf(
            Triple("Photos","صور","gallery_photos"),
            Triple("Videos","فيديو","gallery_videos"),
            Triple("Music","موسيقى","gallery_music"),
            Triple("Apps","تطبيقات","apps"),
            Triple("Storage","الذاكرة","storage"),
            Triple("Documents","المستندات","documents"))
        for(arabic in listOf(false,true)) {
            for((english,arabicLabel,page) in entries) {
                home(if(arabic) AppLanguage.Arabic else AppLanguage.English,dark=arabic)
                if(page=="gallery_photos") screenshot(if(arabic) "home-ar-dark" else "home-en-light")
                val label=if(arabic) arabicLabel else english
                compose.onNodeWithText(label).performScrollTo().performClick()
                compose.runOnIdle { assertEquals(page,model.manager.state.value.page) }
                screenshot("$page-${if(arabic) "ar-dark" else "en-light"}")
                if(page=="storage" || page=="documents") {
                    compose.onNodeWithText(if(arabic) "اختيار ملفات" else "Choose files").assertExists()
                }
            }
        }
    }

    @Test fun filesCategoryButtonsOpenTheInternalBrowserAndFilterChips() {
        home()
        compose.onNodeWithText("Browse all").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("files",model.manager.state.value.page) }
        compose.onNodeWithText("Documents").performClick()
        compose.waitUntil(8_000) { model.manager.state.value.page == "documents" }
        compose.runOnIdle { assertEquals("documents",model.manager.state.value.page) }
        compose.onNodeWithText("PDF").performClick()
        compose.onNodeWithText("Choose files").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("files",model.manager.state.value.page) }
        compose.onNodeWithText("All files").performClick()
        compose.waitUntil(8_000) { model.manager.state.value.page == "storage" }
        compose.runOnIdle { assertEquals("storage",model.manager.state.value.page) }
        compose.onNodeWithText("PDF").performClick()
        compose.onNodeWithText("Choose files").assertExists()
    }

    @android.annotation.TargetApi(30)
    @Test fun fileFoldersSelectionAlbumsAndMediaPlaybackUseActualStorage() {
        val granted=android.os.Environment.isExternalStorageManager() &&
            compose.activity.checkSelfPermission("android.permission.READ_MEDIA_IMAGES")==android.content.pm.PackageManager.PERMISSION_GRANTED &&
            compose.activity.checkSelfPermission("android.permission.READ_MEDIA_AUDIO")==android.content.pm.PackageManager.PERMISSION_GRANTED
        if(InstrumentationRegistry.getArguments().getString("requireLibraryAccess")=="true")
            assertTrue("The granted-access pass must execute the real storage/media workflow",granted)
        else org.junit.Assume.assumeTrue("This pass requires emulator-only library grants",granted)
        val context=compose.activity.applicationContext
        val root=android.os.Environment.getExternalStorageDirectory()
        val folderName="SwiftDrop-QA-"+java.util.UUID.randomUUID().toString().take(8)
        val documents=File(root,"Documents/$folderName").apply { mkdirs() }
        val pictures=File(root,"Pictures/$folderName").apply { mkdirs() }
        val movies=File(root,"Movies/$folderName").apply { mkdirs() }
        val music=File(root,"Music/$folderName").apply { mkdirs() }
        val text=File(documents,"QA-document.txt").apply { writeText("SwiftDrop instrumentation fixture") }
        val photo=File(pictures,"QA-photo.jpg")
        val video=File(movies,"QA-video.mp4")
        val song=File(music,"QA-song.mp3")
        val bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(200,45,100))
            drawText("SwiftDrop QA",20f,130f,android.graphics.Paint().apply {
                color=android.graphics.Color.WHITE;textSize=28f
            })
        }
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) };bitmap.recycle()
        val testAssets=InstrumentationRegistry.getInstrumentation().context.assets
        testAssets.open("swiftdrop-qa.mp4").use { input -> video.outputStream().use { output -> input.copyTo(output) } }
        testAssets.open("swiftdrop-qa.mp3").use { input -> song.outputStream().use { output -> input.copyTo(output) } }
        val uris=java.util.Collections.synchronizedList(mutableListOf<Uri>())
        val scanned=java.util.concurrent.CountDownLatch(3)
        android.media.MediaScannerConnection.scanFile(context,arrayOf(photo.path,video.path,song.path),null) { _,uri ->
            if(uri!=null) uris.add(uri)
            scanned.countDown()
        }
        assertTrue("Fixtures must be indexed by the real MediaStore",scanned.await(30,TimeUnit.SECONDS))
        try {
            home()
            compose.onNodeWithText("Storage").performScrollTo().performClick()
            openBrowserFolder("Documents")
            openBrowserFolder(folderName)
            compose.waitUntil(15_000) { compose.onAllNodesWithText(text.name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(text.name).assertIsDisplayed()
            compose.onNode(isToggleable()).performClick()
            compose.onNodeWithText("Send selected").performClick()
            compose.waitUntil(15_000) { model.manager.state.value.selectedFiles.any { it.name==text.name } }
            compose.runOnIdle { assertEquals("selection",model.manager.state.value.page) }
            home()
            compose.onNodeWithText("Photos").performScrollTo().performClick()
            compose.onNodeWithText("Albums").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(folderName).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(folderName).performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription(photo.name).fetchSemanticsNodes().isNotEmpty() }
            screenshot("photos-album-with-fixture")
            compose.onNodeWithContentDescription(photo.name).performTouchInput { longClick() }
            compose.onNodeWithText("Send selected").performClick()
            compose.waitUntil(15_000) { model.manager.state.value.selectedFiles.any { it.name==photo.name } }
            home()
            compose.onNodeWithText("Videos").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription(video.name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(video.name).performClick()
            screenshot("video-player-with-fixture")
            compose.runOnUiThread { compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitForIdle()
            compose.onNodeWithText("Close").assertExists()
            screenshot("video-player-landscape")
            compose.onNodeWithText("Close").performClick()
            compose.runOnUiThread { compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitForIdle()
            home()
            compose.onNodeWithText("Music").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("QA-song").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("QA-song").performClick()
            compose.onNodeWithText("Queue").performScrollTo().assertExists()
            screenshot("music-library-fixture-player")
            compose.onNodeWithText("Close").performClick()
        } finally {
            val future=MediaController.Builder(context,
                SessionToken(context,ComponentName(context,SwiftDropMusicService::class.java))).buildAsync()
            val controller=future.get(15,TimeUnit.SECONDS)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                controller.stop();controller.clearMediaItems();MediaController.releaseFuture(future)
            }
            uris.forEach { uri -> runCatching { context.contentResolver.delete(uri,null,null) } }
            listOf(documents,pictures,movies,music).forEach { it.deleteRecursively() }
        }
    }

    @Test fun installedAppsPrepareAnActualApkForSending() {
        home()
        compose.onNodeWithText("Apps").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("SwiftDrop").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SwiftDrop").performScrollTo().performClick()
        compose.onNodeWithText("Send selected").performClick()
        compose.waitUntil(20_000) { model.manager.state.value.page=="selection" &&
            model.manager.state.value.selectedFiles.any { it.name.endsWith(".apk") && it.bytes>0 } }
        screenshot("apps-apk-selection")
    }

    @Test fun settingsAndQrEntrySurviveRotationAndThemeChanges() {
        home()
        compose.onNodeWithText("Send using QR").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("files",model.manager.state.value.page) }
        home()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Appearance.Dark,model.settings.state.value.appearance) }
        screenshot("settings-en-dark")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onAllNodesWithText("Settings").onFirst().assertExists()
        home()
        compose.runOnUiThread { compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        screenshot("home-en-landscape")
        compose.onNodeWithText("Storage").performScrollTo().performClick()
        compose.onNodeWithText("Choose files").assertExists()
        screenshot("storage-en-landscape")
    }

    @Test fun musicContinuesAcrossNavigationRecreationAndBackgroundWithMediaSession() {
        home()
        val context=compose.activity.applicationContext
        val track=File(context.cacheDir,"swiftdrop-ui-qa.wav")
        // A finite local PCM fixture belongs only to this instrumented test.
        val samples=8000*300
        val wave=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN)
        wave.put("RIFF".toByteArray()).putInt(36+samples*2).put("WAVEfmt ".toByteArray())
        wave.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        wave.put("data".toByteArray()).putInt(samples*2)
        repeat(samples) { wave.putShort(0) }
        track.writeBytes(wave.array())
        val secondTrack=File(context.cacheDir,"swiftdrop-ui-qa-two.wav")
        secondTrack.writeBytes(wave.array())
        val future=MediaController.Builder(context,
            SessionToken(context,ComponentName(context,SwiftDropMusicService::class.java))).buildAsync()
        val controller=future.get(20,TimeUnit.SECONDS)
        try {
            compose.runOnUiThread {
                controller.setMediaItems(listOf(track to "QA track one",secondTrack to "QA track two").map { (file,title) ->
                    val uri=Uri.fromFile(file)
                    MediaItem.Builder().setUri(uri).setMediaId(uri.toString())
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("SwiftDrop QA").build()).build()
                })
                controller.prepare();controller.play()
            }
            compose.waitUntil(20_000) { musicState { controller.isPlaying } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("QA track one").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("QA track one").performClick()
            compose.onNodeWithText("Queue").performScrollTo().performClick()
            compose.onNodeWithText("QA track two").performClick()
            compose.waitUntil(10_000) { musicState { controller.currentMediaItem?.mediaId==Uri.fromFile(secondTrack).toString() } }
            screenshot("music-now-playing")
            compose.runOnUiThread { compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitForIdle()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Close").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Queue").performScrollTo().assertExists()
            screenshot("music-now-playing-landscape")
            compose.onNodeWithText("Close").performClick()
            compose.runOnUiThread { compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitForIdle()
            compose.onNodeWithText("Settings").performClick()
            compose.runOnIdle { assertTrue(controller.isPlaying) }
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.runOnIdle { assertTrue(controller.isPlaying) }
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            Thread.sleep(600)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue("Playback belongs to the service while Activity is stopped",controller.isPlaying)
            }
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Pause").performClick()
            compose.waitUntil(5_000) { musicState { !controller.isPlaying } }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                controller.stop();controller.clearMediaItems()
                MediaController.releaseFuture(future)
            }
            track.delete();secondTrack.delete()
        }
    }
}
