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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

data class DeviceContact(
    val dataId: Long,
    val contactId: Long,
    val rawContactId: Long,
    val name: String,
    val phone: String
)

/** Reads and writes contacts through the system provider. */
class ContactsRepository(private val resolver: ContentResolver) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeContacts(): Flow<List<DeviceContact>> = resolver.contactsChanges()
        .conflate()
        .transformLatest { isInitial ->
            if (!isInitial) delay(300)
            emit(readContacts())
        }
        .flowOn(Dispatchers.IO)

    suspend fun add(name: String, phone: String) = withContext(Dispatchers.IO) {
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
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    suspend fun update(contact: DeviceContact, name: String, phone: String) = withContext(Dispatchers.IO) {
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
    }

    suspend fun delete(contactId: Long) = withContext(Dispatchers.IO) {
        resolver.delete(
            ContactsContract.RawContacts.CONTENT_URI,
            "${ContactsContract.RawContacts.CONTACT_ID}=?",
            arrayOf(contactId.toString())
        )
    }

    private suspend fun readContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val result = mutableListOf<DeviceContact>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val dataId = cursor.getColumnIndexOrThrow(projection[0])
            val contactId = cursor.getColumnIndexOrThrow(projection[1])
            val rawContactId = cursor.getColumnIndexOrThrow(projection[2])
            val name = cursor.getColumnIndexOrThrow(projection[3])
            val phone = cursor.getColumnIndexOrThrow(projection[4])
            while (cursor.moveToNext()) {
                result += DeviceContact(
                    dataId = cursor.getLong(dataId),
                    contactId = cursor.getLong(contactId),
                    rawContactId = cursor.getLong(rawContactId),
                    name = cursor.getString(name).orEmpty(),
                    phone = cursor.getString(phone).orEmpty()
                )
            }
        }
        result
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
