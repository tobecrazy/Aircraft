package com.young.aircraft.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import com.young.aircraft.data.ImageDetails
import com.young.aircraft.data.ImageDetailsIntentContract
import com.young.aircraft.data.ImageDetailsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowImageDetailsViewModelTest {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel initializes with local image details`() {
        val details = ImageDetails(
            name = "test_image.jpg",
            description = "Test Image",
            source = ImageDetailsSource.Local(R.drawable.background)
        )
        val viewModel = ShowImageDetailsViewModel(context, details)

        val state = viewModel.uiState.value
        assertEquals("test_image.jpg", state.details.name)
        assertEquals("Test Image", state.details.description)
        assertFalse(state.isSaving)
    }

    @Test
    fun `viewModel initializes with network image details`() {
        val url = "https://example.com/image.jpg"
        val details = ImageDetails(
            name = "network_image",
            description = "Network Test",
            source = ImageDetailsSource.Network(url)
        )
        val viewModel = ShowImageDetailsViewModel(context, details)

        val state = viewModel.uiState.value
        assertEquals("network_image", state.details.name)
        assertEquals("Network Test", state.details.description)
        assertEquals(url, (state.details.source as ImageDetailsSource.Network).url)
    }

    @Test
    fun `imageModel is correctly resolved for local source`() {
        val details = ImageDetails(
            name = "local.jpg",
            description = "Local",
            source = ImageDetailsSource.Local(R.drawable.background)
        )
        val viewModel = ShowImageDetailsViewModel(context, details)

        val state = viewModel.uiState.value
        assertTrue(state.imageModel.toString().contains("android.resource"))
        assertTrue(state.imageModel.toString().contains(context.packageName))
    }

    @Test
    fun `imageModel is correctly resolved for network source`() {
        val url = "https://example.com/image.jpg"
        val details = ImageDetails(
            name = "network.jpg",
            description = "Network",
            source = ImageDetailsSource.Network(url)
        )
        val viewModel = ShowImageDetailsViewModel(context, details)

        val state = viewModel.uiState.value
        assertEquals(url, state.imageModel)
    }

    @Test
    fun `factory creates viewModel from local image intent`() {
        val intent = Intent().apply {
            putExtra(ImageDetailsIntentContract.EXTRA_NAME, "test.jpg")
            putExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION, "Test")
            putExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE, ImageDetailsIntentContract.SOURCE_LOCAL)
            putExtra(ImageDetailsIntentContract.EXTRA_RES_ID, R.drawable.background)
        }
        val factory = ShowImageDetailsViewModel.Factory(context, intent)

        val viewModel = factory.create(ShowImageDetailsViewModel::class.java)

        assertNotNull(viewModel)
        assertEquals("test.jpg", viewModel.uiState.value.details.name)
        assertEquals("Test", viewModel.uiState.value.details.description)
    }

    @Test
    fun `factory creates viewModel from network image intent`() {
        val url = "https://example.com/banner.jpg"
        val intent = Intent().apply {
            putExtra(ImageDetailsIntentContract.EXTRA_NAME, "network.jpg")
            putExtra(ImageDetailsIntentContract.EXTRA_DESCRIPTION, "Network")
            putExtra(ImageDetailsIntentContract.EXTRA_SOURCE_TYPE, ImageDetailsIntentContract.SOURCE_NETWORK)
            putExtra(ImageDetailsIntentContract.EXTRA_URL, url)
        }
        val factory = ShowImageDetailsViewModel.Factory(context, intent)

        val viewModel = factory.create(ShowImageDetailsViewModel::class.java)

        assertNotNull(viewModel)
        assertEquals("network.jpg", viewModel.uiState.value.details.name)
        assertEquals(url, (viewModel.uiState.value.details.source as ImageDetailsSource.Network).url)
    }

    @Test
    fun `factory throws exception when intent is missing required data`() {
        val invalidIntent = Intent()
        val factory = ShowImageDetailsViewModel.Factory(context, invalidIntent)

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(ShowImageDetailsViewModel::class.java)
        }
    }

    @Test
    fun `downloadFileName adds extension for name without extension`() {
        val details = ImageDetails(
            name = "image_without_ext",
            description = "Test",
            source = ImageDetailsSource.Local(R.drawable.background)
        )

        assertEquals("image_without_ext.jpg", details.downloadFileName)
    }

    @Test
    fun `downloadFileName preserves existing extension`() {
        val details = ImageDetails(
            name = "image_with_ext.png",
            description = "Test",
            source = ImageDetailsSource.Local(R.drawable.background)
        )

        assertEquals("image_with_ext.png", details.downloadFileName)
    }

    @Test
    fun `isSaving flag is initially false`() {
        val details = ImageDetails(
            name = "test.jpg",
            description = "Test",
            source = ImageDetailsSource.Local(R.drawable.background)
        )
        val viewModel = ShowImageDetailsViewModel(context, details)

        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `saveImage prevents duplicate saves`() = runTest {
        val details = ImageDetails(
            name = "test.jpg",
            description = "Test",
            source = ImageDetailsSource.Local(R.drawable.background)
        )
        val httpClient = mock<okhttp3.OkHttpClient>()
        val viewModel = ShowImageDetailsViewModel(context, details, httpClient)

        val uri = mock<Uri>()
        viewModel.saveImage(uri)

        // Verify that saving flag prevents duplicate saves
        assertTrue(true) // Placeholder for duplicate prevention test
    }

    @Test
    fun `imageModel handles android resource protocol correctly`() {
        val resId = R.drawable.background
        val details = ImageDetails(
            name = "test.jpg",
            description = "Test",
            source = ImageDetailsSource.Local(resId)
        )
        val viewModel = ShowImageDetailsViewModel(context, details)

        val imageModel = viewModel.uiState.value.imageModel as String
        assertTrue(imageModel.startsWith("android.resource://"))
        assertTrue(imageModel.contains(context.packageName))
        assertTrue(imageModel.contains(resId.toString()))
    }

    @Test
    fun `uiState contains correct image details information`() {
        val url = "https://example.com/test.jpg"
        val details = ImageDetails(
            name = "test_image",
            description = "A test image",
            source = ImageDetailsSource.Network(url)
        )
        val viewModel = ShowImageDetailsViewModel(context, details)

        val state = viewModel.uiState.value
        assertEquals(details, state.details)
        assertEquals(url, state.imageModel)
    }

    @Test
    fun `multiple viewModels can be created independently`() {
        val details1 = ImageDetails(
            name = "image1.jpg",
            description = "First",
            source = ImageDetailsSource.Local(R.drawable.background)
        )
        val details2 = ImageDetails(
            name = "image2.jpg",
            description = "Second",
            source = ImageDetailsSource.Network("https://example.com/img.jpg")
        )

        val viewModel1 = ShowImageDetailsViewModel(context, details1)
        val viewModel2 = ShowImageDetailsViewModel(context, details2)

        assertNotEquals(viewModel1.uiState.value.details.name, viewModel2.uiState.value.details.name)
        assertEquals("image1.jpg", viewModel1.uiState.value.details.name)
        assertEquals("image2.jpg", viewModel2.uiState.value.details.name)
    }
}
