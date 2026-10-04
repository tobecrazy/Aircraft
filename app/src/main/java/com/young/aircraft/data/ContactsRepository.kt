package com.young.aircraft.data

import android.content.ContentResolver
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class DeviceContact(
    val dataId: Long,
    val contactId: Long,
    val rawContactId: Long,
    val name: String,
    val phone: String,
    val email: String = "",
    val address: String = "",
    val emailDataId: Long? = null,
    val addressDataId: Long? = null
)

/** Reads and writes contacts through the system provider. */
class ContactsRepository(private val resolver: ContentResolver) {
    private val refreshVersion = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeContacts(): Flow<List<DeviceContact>> = merge(
        resolver.contactsChanges(),
        refreshVersion.drop(1).map { false }
    )
        .conflate()
        .transformLatest { isInitial ->
            if (!isInitial) delay(300)
            emit(readContacts())
        }
        .flowOn(Dispatchers.IO)

    suspend fun add(name: String, phone: String, email: String = "", address: String = "") = withContext(Dispatchers.IO) {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )
        if (email.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.trim())
                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                .build()
        }
        if (address.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address.trim())
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME)
                .build()
        }
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        requestRefresh()
    }

    suspend fun update(
        contact: DeviceContact,
        name: String,
        phone: String,
        email: String = "",
        address: String = ""
    ) = withContext(Dispatchers.IO) {
        val phoneValues = ContentValues().apply {
            put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
        }
        resolver.update(
            ContactsContract.Data.CONTENT_URI,
            phoneValues,
            "${ContactsContract.Data._ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(contact.dataId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        )
        val nameValues = ContentValues().apply {
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
        }
        resolver.update(
            ContactsContract.Data.CONTENT_URI,
            nameValues,
            "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(contact.rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        )
        updateOptionalData(
            contact.rawContactId,
            contact.emailDataId,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.TYPE_HOME,
            email
        )
        updateOptionalData(
            contact.rawContactId,
            contact.addressDataId,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
            ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME,
            address
        )
        requestRefresh()
    }

    private fun updateOptionalData(
        rawContactId: Long,
        dataId: Long?,
        mimeType: String,
        valueColumn: String,
        homeType: Int,
        value: String
    ) {
        if (dataId != null) {
            if (value.isBlank()) {
                resolver.delete(ContactsContract.Data.CONTENT_URI, "${ContactsContract.Data._ID}=?", arrayOf(dataId.toString()))
            } else {
                resolver.update(
                    ContactsContract.Data.CONTENT_URI,
                    ContentValues().apply { put(valueColumn, value.trim()) },
                    "${ContactsContract.Data._ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                    arrayOf(dataId.toString(), mimeType)
                )
            }
        } else if (value.isNotBlank()) {
            resolver.insert(
                ContactsContract.Data.CONTENT_URI,
                ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, mimeType)
                    put(valueColumn, value.trim())
                    put(ContactsContract.Data.DATA2, homeType)
                }
            )
        }
    }

    suspend fun delete(contactId: Long) = withContext(Dispatchers.IO) {
        resolver.delete(
            ContactsContract.RawContacts.CONTENT_URI,
            "${ContactsContract.RawContacts.CONTACT_ID}=?",
            arrayOf(contactId.toString())
        )
        requestRefresh()
    }

    private fun requestRefresh() {
        refreshVersion.update { it + 1 }
    }

    private suspend fun readContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Contacts.DISPLAY_NAME
        )
        data class ContactData(
            val contactId: Long,
            val rawContactId: Long,
            val name: String,
            val phones: MutableList<Pair<Long, String>> = mutableListOf(),
            var email: String = "",
            var emailDataId: Long? = null,
            var emailIsHome: Boolean = false,
            var address: String = "",
            var addressDataId: Long? = null,
            var addressIsHome: Boolean = false
        )
        val contactsByRawId = linkedMapOf<Long, ContactData>()
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?)",
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            ),
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data._ID)
            val contactIdIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val rawContactIdIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.RAW_CONTACT_ID)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val valueIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
            val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val rawContactId = cursor.getLong(rawContactIdIndex)
                val contact = contactsByRawId.getOrPut(rawContactId) {
                    ContactData(
                        contactId = cursor.getLong(contactIdIndex),
                        rawContactId = rawContactId,
                        name = cursor.getString(nameIndex).orEmpty()
                    )
                }
                val id = cursor.getLong(idIndex)
                val value = cursor.getString(valueIndex).orEmpty()
                when (cursor.getString(mimeTypeIndex)) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> contact.phones += id to value
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        val isHome = cursor.getInt(typeIndex) == ContactsContract.CommonDataKinds.Email.TYPE_HOME
                        if (contact.emailDataId == null || (isHome && !contact.emailIsHome)) {
                            contact.email = value
                            contact.emailDataId = id
                            contact.emailIsHome = isHome
                        }
                    }
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        val isHome = cursor.getInt(typeIndex) == ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
                        if (isHome && (contact.addressDataId == null || !contact.addressIsHome)) {
                            contact.address = value
                            contact.addressDataId = id
                            contact.addressIsHome = isHome
                        }
                    }
                }
            }
        }
        contactsByRawId.values.flatMap { contact ->
            contact.phones.map { (dataId, phone) ->
                DeviceContact(
                    dataId = dataId,
                    contactId = contact.contactId,
                    rawContactId = contact.rawContactId,
                    name = contact.name,
                    phone = phone,
                    email = contact.email,
                    address = contact.address,
                    emailDataId = contact.emailDataId,
                    addressDataId = contact.addressDataId
                )
            }
        }
    }
}

private fun ContentResolver.contactsChanges() = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            trySend(false)
        }
    }
    registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
    trySend(true)
    awaitClose { unregisterContentObserver(observer) }
}
