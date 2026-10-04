package com.young.aircraft.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.database.MatrixCursor
import android.provider.ContactsContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the row-shaping rules in [ContactsRepository.readContacts] — the part that silently
 * decides what the user sees: one entry per phone, and HOME-typed email/address winning over
 * other types. Writes are asserted on the provider calls the repository actually makes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactsRepositoryTest {

    private val resolver: ContentResolver = mock(ContentResolver::class.java)

    private val repository = ContactsRepository(resolver)

    private fun cursorWithRows(vararg rows: Array<Any?>) = MatrixCursor(
        arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Contacts.DISPLAY_NAME
        )
    ).apply { rows.forEach { addRow(it) } }

    private fun row(
        id: Long,
        contactId: Long,
        rawId: Long,
        mimeType: String,
        value: String,
        type: Int,
        displayName: String
    ) = arrayOf<Any?>(id, contactId, rawId, mimeType, value, type, displayName)

    private fun stubQuery(cursor: MatrixCursor) {
        `when`(
            resolver.query(
                any(android.net.Uri::class.java),
                any(Array<String>::class.java),
                any(String::class.java),
                any(Array<String>::class.java),
                any(String::class.java)
            )
        ).thenReturn(cursor)
    }

    @Test
    fun `one contact with two phones becomes two rows sharing the name`() = runTest {
        stubQuery(
            cursorWithRows(
                row(1, 7, 70, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, "Ada", 0, "Ada"),
                row(2, 7, 70, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "13800138000", 2, "Ada"),
                row(3, 7, 70, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "13900139000", 2, "Ada")
            )
        )

        val contacts = repository.observeContacts().first()

        assertEquals(2, contacts.size)
        assertTrue(contacts.all { it.contactId == 7L && it.rawContactId == 70L && it.name == "Ada" })
        assertEquals(listOf("13800138000", "13900139000"), contacts.map { it.phone })
        assertEquals(listOf(2L, 3L), contacts.map { it.dataId })
    }

    @Test
    fun `home email wins over work and is carried onto every phone row`() = runTest {
        stubQuery(
            cursorWithRows(
                row(1, 7, 70, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "13800138000", 2, "Ada"),
                row(4, 7, 70, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, "work@example.com", ContactsContract.CommonDataKinds.Email.TYPE_WORK, "Ada"),
                row(5, 7, 70, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, "home@example.com", ContactsContract.CommonDataKinds.Email.TYPE_HOME, "Ada"),
                row(2, 7, 70, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "13900139000", 2, "Ada")
            )
        )

        val contacts = repository.observeContacts().first()

        assertEquals(2, contacts.size)
        assertTrue(contacts.all { it.email == "home@example.com" && it.emailDataId == 5L })
    }

    @Test
    fun `work email is kept when no home email exists`() = runTest {
        stubQuery(
            cursorWithRows(
                row(1, 7, 70, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "13800138000", 2, "Ada"),
                row(4, 7, 70, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, "work@example.com", ContactsContract.CommonDataKinds.Email.TYPE_WORK, "Ada")
            )
        )

        assertEquals("work@example.com", repository.observeContacts().first().single().email)
    }

    @Test
    fun `only home addresses are surfaced`() = runTest {
        stubQuery(
            cursorWithRows(
                row(1, 7, 70, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "13800138000", 2, "Ada"),
                row(6, 7, 70, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, "Office", ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK, "Ada"),
                row(7, 7, 70, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, "Home", ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME, "Ada")
            )
        )

        val contact = repository.observeContacts().first().single()

        assertEquals("Home", contact.address)
        assertEquals(7L, contact.addressDataId)
    }

    @Test
    fun `a contact with no phone row is not listed`() = runTest {
        stubQuery(
            cursorWithRows(
                row(1, 7, 70, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, "Ada", 0, "Ada"),
                row(4, 7, 70, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, "home@example.com", 1, "Ada")
            )
        )

        assertTrue(repository.observeContacts().first().isEmpty())
    }

    @Test
    fun `an empty provider result yields an empty list`() = runTest {
        stubQuery(cursorWithRows())

        assertTrue(repository.observeContacts().first().isEmpty())
    }

    @Suppress("UNCHECKED_CAST")
    private fun capturedBatch(): List<ContentProviderOperation> {
        val captor = ArgumentCaptor.forClass(ArrayList::class.java)
            as ArgumentCaptor<ArrayList<ContentProviderOperation>>
        verify(resolver).applyBatch(eq(ContactsContract.AUTHORITY), captor.capture())
        return captor.value
    }

    @Test
    fun `add builds one batched operation per field and omits blank optional ones`() = runTest {
        repository.add(name = "Ada", phone = "13800138000")

        // raw contact + structured name + phone; no email/address rows when both are blank.
        assertEquals(3, capturedBatch().size)
    }

    @Test
    fun `add includes email and address operations when both are supplied`() = runTest {
        repository.add(name = "Ada", phone = "13800138000", email = "a@b.com", address = "Home")

        assertEquals(5, capturedBatch().size)
    }

    @Test
    fun `update rewrites phone and name and leaves absent optional rows alone`() = runTest {
        val contact = DeviceContact(
            dataId = 2L,
            contactId = 7L,
            rawContactId = 70L,
            name = "Ada",
            phone = "13800138000"
        )

        repository.update(contact = contact, name = "Ada L", phone = "13900139000")

        // Once for the phone data row (by _ID), once for the structured name (by RAW_CONTACT_ID).
        verify(resolver, times(2)).update(
            eq(ContactsContract.Data.CONTENT_URI),
            any(ContentValues::class.java),
            any(String::class.java),
            any(Array<String>::class.java)
        )
        verify(resolver, org.mockito.Mockito.never()).insert(
            eq(ContactsContract.Data.CONTENT_URI),
            any(ContentValues::class.java)
        )
        verify(resolver, org.mockito.Mockito.never()).delete(
            eq(ContactsContract.Data.CONTENT_URI),
            any(String::class.java),
            any(Array<String>::class.java)
        )
    }

    @Test
    fun `update deletes an existing optional row that was cleared`() = runTest {
        val contact = DeviceContact(
            dataId = 2L,
            contactId = 7L,
            rawContactId = 70L,
            name = "Ada",
            phone = "13800138000",
            email = "old@example.com",
            emailDataId = 4L
        )

        repository.update(contact = contact, name = "Ada", phone = "13800138000", email = "")

        verify(resolver).delete(
            eq(ContactsContract.Data.CONTENT_URI),
            eq("${ContactsContract.Data._ID}=?"),
            eq(arrayOf("4"))
        )
    }

    @Test
    fun `delete removes every raw row for the contact id`() = runTest {
        repository.delete(contactId = 7L)

        verify(resolver).delete(
            eq(ContactsContract.RawContacts.CONTENT_URI),
            eq("${ContactsContract.RawContacts.CONTACT_ID}=?"),
            eq(arrayOf("7"))
        )
    }

    @Test
    fun `a null cursor is treated as no contacts rather than a crash`() = runTest {
        `when`(
            resolver.query(
                any(android.net.Uri::class.java),
                any(Array<String>::class.java),
                any(String::class.java),
                any(Array<String>::class.java),
                any(String::class.java)
            )
        ).thenReturn(null)

        assertTrue(repository.observeContacts().first().isEmpty())
    }
}
