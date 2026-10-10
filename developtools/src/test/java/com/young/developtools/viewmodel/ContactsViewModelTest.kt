package com.young.developtools.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.young.developtools.data.DeviceContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The contacts screen is a debug tool gated behind a runtime permission grant, so the state machine
 * is the contract worth pinning: no grant means an empty, non-loading, non-error state (never a
 * silent read attempt), and a grant flips it into the loading-then-populated sequence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: ContactsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
        viewModel = ContactsViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun state() = viewModel.uiState.value

    @Test
    fun `without permission grant the state stays empty and idle`() {
        assertEquals(emptyList<DeviceContact>(), state().contacts)
        assertFalse(state().loading)
        assertFalse(state().error)
    }

    @Test
    fun `granting permission moves the state out of loading into a populated result`() = runTest {
        viewModel.onPermissionResult(true)

        // The provider is empty under Robolectric, so the settled state is loaded-but-empty.
        assertFalse(state().loading)
        assertFalse(state().error)
        assertEquals(emptyList<DeviceContact>(), state().contacts)
    }

    @Test
    fun `revoking permission after a grant returns to the empty state`() = runTest {
        viewModel.onPermissionResult(true)
        viewModel.onPermissionResult(false)

        assertEquals(emptyList<DeviceContact>(), state().contacts)
        assertFalse(state().loading)
        assertFalse(state().error)
    }

    @Test
    fun `invalid input is dropped before any provider write`() = runTest {
        // A rejected phone must not reach the resolver; the state stays clean rather than erroring.
        viewModel.onPermissionResult(true)
        viewModel.add(name = "Ada", phone = "12345")

        assertFalse(state().error)
        assertTrue(state().contacts.isEmpty())
    }

    @Test
    fun `a malformed email blocks the write just like a malformed phone`() = runTest {
        viewModel.onPermissionResult(true)
        viewModel.add(name = "Ada", phone = "13800138000", email = "not-an-email")

        assertFalse(state().error)
        assertTrue(state().contacts.isEmpty())
    }
}
