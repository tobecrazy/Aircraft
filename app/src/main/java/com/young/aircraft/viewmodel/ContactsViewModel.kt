package com.young.aircraft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.young.aircraft.data.ContactsRepository
import com.young.aircraft.data.DeviceContact
import com.young.aircraft.data.isValidOptionalEmail
import com.young.aircraft.data.isValidChinaPhoneNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactsUiState(
    val contacts: List<DeviceContact> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ContactsRepository(application.contentResolver)
    private val permissionGranted = MutableStateFlow(false)
    private val operationError = MutableStateFlow(false)

    val uiState = permissionGranted.flatMapLatest { granted ->
        if (!granted) {
            flowOf(ContactsUiState())
        } else {
            repository.observeContacts()
                .map { ContactsUiState(contacts = it) }
                .onStart { emit(ContactsUiState(loading = true)) }
                .catch { emit(ContactsUiState(error = true)) }
        }
    }.combine(operationError) { state, failed ->
        state.copy(error = state.error || failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun onPermissionResult(granted: Boolean) {
        permissionGranted.value = granted
        if (granted) operationError.value = false
    }

    fun add(name: String, phone: String, email: String = "", address: String = "") {
        if (!isValidChinaPhoneNumber(phone) || !isValidOptionalEmail(email)) return
        mutate { repository.add(name, phone, email, address) }
    }

    fun update(contact: DeviceContact, name: String, phone: String, email: String = "", address: String = "") {
        if (!isValidChinaPhoneNumber(phone) || !isValidOptionalEmail(email)) return
        mutate { repository.update(contact, name, phone, email, address) }
    }

    fun delete(contactId: Long) = mutate {
        repository.delete(contactId)
    }

    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            operationError.value = false
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operationError.value = true
            }
        }
    }
}
