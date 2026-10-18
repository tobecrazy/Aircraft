package com.young.aircraft.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.utils.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Covers the sealed image load state: cache-first hits, safe cache installation,
 * safe failure categories, and suppression of superseded requests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PuzzleImageViewModelTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val cachePrefs get() = context.getSharedPreferences("puzzle_image_cache", Context.MODE_PRIVATE)
    private val cacheFile: File get() = File(context.cacheDir, "puzzle_cached_image_level_1.jpg")
    private val tempFile: File get() = File(context.cacheDir, "puzzle_download_level_1.tmp")

    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher

    private val feedJson = "[{\"thumbUrl\":\"https://x/thumb.jpg\",\"imageUrl\":\"https://x/image.jpg\",\"fullUrl\":\"https://x/full.jpg\"}]"
    private val jpegBytes = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte()
    ) + ByteArray(12)

    @Before
    fun setUp() {
        AppLog.enabled = false
        scheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(testDispatcher)
        cachePrefs.edit().clear().commit()
        cacheFile.delete()
        tempFile.delete()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        cachePrefs.edit().clear().commit()
        cacheFile.delete()
        tempFile.delete()
    }

    private fun newViewModel(
        initialLevel: Int = 1,
        feedLoader: suspend () -> String,
        downloader: suspend (String) -> PuzzleImageDownload?
    ): PuzzleImageViewModel = PuzzleImageViewModel(context, initialLevel, testDispatcher, feedLoader, downloader)

    private fun installValidCache() {
        cacheFile.writeBytes(jpegBytes)
        cachePrefs.edit().putString("cached_image_file_1", cacheFile.name).commit()
    }

    @Test
    fun `cached level opens ready without touching the feed`() = runTest(testDispatcher) {
        installValidCache()
        val neverNetwork: suspend () -> String = { throw AssertionError("cache hit must not fetch the feed") }

        val viewModel = newViewModel(feedLoader = neverNetwork, downloader = { null })
        advanceUntilIdle()

        val ready = viewModel.uiState.value.load as PuzzleImageLoadState.Ready
        assertEquals(1, ready.level)
        assertEquals(Uri.fromFile(cacheFile).toString(), ready.uri.toString())
        assertTrue(ready.fromCache)
        assertTrue(viewModel.uiState.value.sessionStarted)
    }

    @Test
    fun `failed refresh retains the cached image and raises the stale notice`() = runTest(testDispatcher) {
        installValidCache()
        val viewModel = newViewModel(
            feedLoader = { feedJson },
            downloader = { throw IOException("refresh failed") }
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.load is PuzzleImageLoadState.Ready)

        viewModel.refreshLevel()
        advanceUntilIdle()

        val ready = viewModel.uiState.value.load as PuzzleImageLoadState.Ready
        assertTrue("A valid cache must survive a failed refresh", ready.fromCache)
        assertTrue(viewModel.uiState.value.showStaleNotice)
        assertTrue(viewModel.uiState.value.sessionStarted)
    }

    @Test
    fun `successful refresh replaces the cache and clears the stale notice`() = runTest(testDispatcher) {
        installValidCache()
        val viewModel = newViewModel(
            feedLoader = { feedJson },
            downloader = { PuzzleImageDownload(jpegBytes, "image/jpeg") }
        )
        advanceUntilIdle()

        viewModel.refreshLevel()
        advanceUntilIdle()

        val ready = viewModel.uiState.value.load as PuzzleImageLoadState.Ready
        assertFalse("A successful refresh is not stale", ready.fromCache)
        assertFalse(viewModel.uiState.value.showStaleNotice)
    }

    @Test
    fun `invalid response bodies are rejected and never cached`() = runTest(testDispatcher) {
        val plainBytes = PuzzleImageDownload("definitely not an image".toByteArray(), "text/plain")

        val viewModel = newViewModel(
            feedLoader = { feedJson },
            downloader = { plainBytes }
        )
        advanceUntilIdle()

        val failed = viewModel.uiState.value.load as PuzzleImageLoadState.Failed
        assertEquals(PuzzleImageFailure.DOWNLOAD_FAILED, failed.category)
        assertFalse(viewModel.uiState.value.sessionStarted)
        assertNull(cachePrefs.getString("cached_image_file_1", null))
        assertFalse(cacheFile.exists())
        assertFalse("The temporary download file must be cleaned up", tempFile.exists())
    }

    @Test
    fun `image magic bytes are accepted when the content type is missing`() = runTest(testDispatcher) {
        val viewModel = newViewModel(
            feedLoader = { feedJson },
            downloader = { PuzzleImageDownload(jpegBytes, null) }
        )
        advanceUntilIdle()

        val ready = viewModel.uiState.value.load as PuzzleImageLoadState.Ready
        assertFalse(ready.fromCache)
        assertTrue(cacheFile.exists())
        assertEquals(cacheFile.name, cachePrefs.getString("cached_image_file_1", null))
    }

    @Test
    fun `feed failures map to the network category`() = runTest(testDispatcher) {
        val viewModel = newViewModel(
            feedLoader = { throw IOException("no route to host") },
            downloader = { throw AssertionError("downloader must not run") }
        )
        advanceUntilIdle()

        assertEquals(
            PuzzleImageFailure.NETWORK,
            (viewModel.uiState.value.load as PuzzleImageLoadState.Failed).category
        )
    }

    @Test
    fun `feed without image urls maps to the feed category`() = runTest(testDispatcher) {
        val viewModel = newViewModel(
            feedLoader = { "{\"items\":[]}" },
            downloader = { throw AssertionError("downloader must not run") }
        )
        advanceUntilIdle()

        assertEquals(
            PuzzleImageFailure.FEED_EMPTY,
            (viewModel.uiState.value.load as PuzzleImageLoadState.Failed).category
        )
    }

    @Test
    fun `superseded requests cannot overwrite the active level state`() = runTest(testDispatcher) {
        val feedCalls = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val feedLoader: suspend () -> String = {
            if (feedCalls.incrementAndGet() == 1) {
                // The first (soon superseded) request hangs until the test lets it finish.
                withContext(NonCancellable) { gate.await() }
            }
            feedJson
        }
        // The delayed request resolves to a failure; the newer request succeeds.
        val firstRequestOwnsDownloader = AtomicBoolean(true)
        val downloader: suspend (String) -> PuzzleImageDownload? = {
            if (firstRequestOwnsDownloader.get()) throw IOException("slow request failed")
            PuzzleImageDownload(jpegBytes, "image/jpeg")
        }

        val viewModel = newViewModel(feedLoader = feedLoader, downloader = downloader)
        scheduler.runCurrent()

        firstRequestOwnsDownloader.set(false)
        viewModel.retryLevel()
        advanceUntilIdle()

        val ready = viewModel.uiState.value.load as PuzzleImageLoadState.Ready
        val readyUri = ready.uri

        // Let the superseded request complete on its own; its outcome must be ignored.
        gate.complete(Unit)
        advanceUntilIdle()

        val finalReady = viewModel.uiState.value.load as PuzzleImageLoadState.Ready
        assertEquals(readyUri.toString(), finalReady.uri.toString())
        assertFalse(viewModel.uiState.value.showStaleNotice)
    }

    @Test
    fun `downloads over the size ceiling are rejected and never cached`() = runTest(testDispatcher) {
        val oversized = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xE0.toByte(), 0x00.toByte()
        ) + ByteArray((PuzzleImageViewModel.MAX_IMAGE_BYTES + 1).toInt())

        val viewModel = newViewModel(
            feedLoader = { feedJson },
            downloader = { PuzzleImageDownload(oversized, "image/jpeg") }
        )
        advanceUntilIdle()

        assertEquals(
            PuzzleImageFailure.DOWNLOAD_FAILED,
            (viewModel.uiState.value.load as PuzzleImageLoadState.Failed).category
        )
        assertNull(cachePrefs.getString("cached_image_file_1", null))
        assertFalse(cacheFile.exists())
        assertFalse(tempFile.exists())
    }

    @Test
    fun `retry after an initial failure can reach ready`() = runTest(testDispatcher) {
        // All candidates of the first request fail; later requests succeed.
        val firstRequestActive = AtomicBoolean(true)
        val viewModel = newViewModel(
            feedLoader = { feedJson },
            downloader = {
                if (firstRequestActive.get()) throw IOException("first attempt fails")
                PuzzleImageDownload(jpegBytes, "image/jpeg")
            }
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.load is PuzzleImageLoadState.Failed)
        assertFalse(viewModel.uiState.value.sessionStarted)

        firstRequestActive.set(false)
        viewModel.retryLevel()
        advanceUntilIdle()

        val ready = viewModel.uiState.value.load as PuzzleImageLoadState.Ready
        assertFalse(ready.fromCache)
        assertTrue(viewModel.uiState.value.sessionStarted)
        assertTrue(cacheFile.exists())
    }

    @Test
    fun `loadLevel only reloads levels that are not ready`() = runTest(testDispatcher) {
        installValidCache()
        val feedCalls = AtomicInteger(0)
        val viewModel = newViewModel(
            feedLoader = { feedCalls.incrementAndGet(); feedJson },
            downloader = { PuzzleImageDownload(jpegBytes, "image/jpeg") }
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.load is PuzzleImageLoadState.Ready)

        // Same level again must not trigger another fetch.
        viewModel.loadLevel(1)
        advanceUntilIdle()
        assertEquals(0, feedCalls.get())

        // A different level switches the active state and starts a load.
        viewModel.loadLevel(2)
        advanceUntilIdle()
        assertEquals(1, feedCalls.get())
        assertEquals(2, viewModel.uiState.value.activeLevel)
    }

    @Test
    fun `loadLevel clamps out-of-range levels into the valid range`() = runTest(testDispatcher) {
        val viewModel = newViewModel(
            feedLoader = { feedJson },
            downloader = { PuzzleImageDownload(jpegBytes, "image/jpeg") }
        )
        advanceUntilIdle()

        viewModel.loadLevel(99)
        advanceUntilIdle()
        assertEquals(PuzzleImageViewModel.MAX_PUZZLE_LEVEL, viewModel.uiState.value.activeLevel)

        viewModel.loadLevel(0)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.activeLevel)
    }
}
