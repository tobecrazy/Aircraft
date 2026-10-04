package com.young.aircraft.gui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.young.aircraft.R
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.utils.DebugTools
import com.young.aircraft.data.DeviceContact
import com.young.aircraft.data.isValidChinaPhoneNumber
import com.young.aircraft.data.isValidOptionalEmail
import com.young.aircraft.viewmodel.ContactsViewModel

class ContactsActivity : AppCompatActivity() {
    private lateinit var viewModel: ContactsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DebugTools.isEnabled) {
            finish()
            return
        }
        viewModel = ViewModelProvider(this)[ContactsViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            AircraftTheme {
                ContactsScreen(
                    viewModel = viewModel,
                    hasReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
                    hasWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ContactsScreen(
    viewModel: ContactsViewModel,
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    onBack: () -> Unit
) {
    var readGranted by remember { mutableStateOf(hasReadPermission) }
    var writeGranted by remember { mutableStateOf(hasWritePermission) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        readGranted = result[Manifest.permission.READ_CONTACTS] == true || hasReadPermission
        writeGranted = result[Manifest.permission.WRITE_CONTACTS] == true || hasWritePermission
        viewModel.onPermissionResult(readGranted && writeGranted)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<DeviceContact?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DeviceContact?>(null) }

    LaunchedEffect(Unit) {
        if (!readGranted || !writeGranted) {
            permissions.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
        } else {
            viewModel.onPermissionResult(true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundDark)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(HeaderBackground).statusBarsPadding().heightIn(min = 52.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart).size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_header_back),
                    contentDescription = stringResource(R.string.contacts_back),
                    tint = AccentGreen
                )
            }
            Text(
                text = stringResource(R.string.develop_settings_assistant_module_contacts),
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                color = AccentGreen,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
        NeonDivider()

        if (!readGranted || !writeGranted) {
            Surface(
                modifier = Modifier.maxContentWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.contacts_permission_message),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        permissions.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
                    }) { Text(stringResource(R.string.contacts_grant_permission)) }
                }
            }
        } else {
            Row(
                Modifier.maxContentWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.contacts_count, state.contacts.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace
                )
                TextButton(onClick = { adding = true }, modifier = Modifier.testTag("contacts_add")) {
                    Text(stringResource(R.string.contacts_add), color = AccentGreen)
                }
            }
            if (state.error) {
                Text(
                    stringResource(R.string.contacts_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.maxContentWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (state.loading) Text(
                stringResource(R.string.contacts_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.maxContentWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                items(state.contacts, key = { it.dataId }) { contact ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                contact.name.ifBlank { stringResource(R.string.contacts_unnamed) },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                contact.phone,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (contact.email.isNotBlank()) {
                                Text(
                                    stringResource(R.string.contacts_email_display, contact.email),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (contact.address.isNotBlank()) {
                                Text(
                                    stringResource(R.string.contacts_address_display, contact.address),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                                OutlinedButton(onClick = { editing = contact }) { Text(stringResource(R.string.contacts_edit)) }
                                OutlinedButton(onClick = { deleting = contact }) { Text(stringResource(R.string.contacts_delete)) }
                            }
                        }
                    }
                }
                if (state.contacts.isEmpty() && !state.loading && !state.error) {
                    item {
                        Text(
                            stringResource(R.string.contacts_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.maxContentWidth().padding(24.dp)
                        )
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val contact = editing
        ContactEditorDialog(
            title = stringResource(if (contact == null) R.string.contacts_add_title else R.string.contacts_edit_title),
            initialName = contact?.name.orEmpty(),
            initialPhone = contact?.phone.orEmpty(),
            initialEmail = contact?.email.orEmpty(),
            initialAddress = contact?.address.orEmpty(),
            onDismiss = { adding = false; editing = null },
            onSave = { name, phone, email, address ->
                if (contact == null) viewModel.add(name, phone, email, address)
                else viewModel.update(contact, name, phone, email, address)
                adding = false
                editing = null
            }
        )
    }
    deleting?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.contacts_delete_title)) },
            text = {
                Text(stringResource(
                    R.string.contacts_delete_message,
                    contact.name.ifBlank { stringResource(R.string.contacts_unnamed) }
                ))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(contact.contactId); deleting = null }) {
                    Text(stringResource(R.string.contacts_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.contacts_cancel)) } }
        )
    }
}

@Composable
private fun ContactEditorDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    initialEmail: String,
    initialAddress: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember(initialName, initialPhone, initialEmail, initialAddress) { mutableStateOf(initialName) }
    var phone by remember(initialName, initialPhone, initialEmail, initialAddress) { mutableStateOf(initialPhone) }
    var email by remember(initialName, initialPhone, initialEmail, initialAddress) { mutableStateOf(initialEmail) }
    var address by remember(initialName, initialPhone, initialEmail, initialAddress) { mutableStateOf(initialAddress) }
    val validPhone = isValidChinaPhoneNumber(phone)
    val invalidPhone = phone.isNotBlank() && !validPhone
    val validEmail = isValidOptionalEmail(email)
    val invalidEmail = email.isNotBlank() && !validEmail
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.contacts_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.contacts_phone)) },
                    singleLine = true,
                    isError = invalidPhone,
                    supportingText = {
                        if (invalidPhone) Text(stringResource(R.string.contacts_phone_invalid))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.contacts_email)) },
                    singleLine = true,
                    isError = invalidEmail,
                    supportingText = {
                        if (invalidEmail) Text(stringResource(R.string.contacts_email_invalid))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.contacts_address)) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && validPhone && validEmail) {
                        onSave(name.trim(), phone.trim(), email.trim(), address.trim())
                    }
                },
                enabled = name.isNotBlank() && validPhone && validEmail
            ) {
                Text(stringResource(R.string.contacts_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.contacts_cancel)) } }
    )
}
