package com.denser.june.presentation.screens.editor.components.people

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.denser.june.core.R
import kotlinx.coroutines.launch

// -------------------------------------------------------------------------------------------------
// Contacts helpers (no READ_CONTACTS permission: the system picker grants access to one contact)
// -------------------------------------------------------------------------------------------------

internal data class PickedContact(val name: String, val lookupKey: String?)

/**
 * Whether June may read contacts. It never needs to: picking and opening a contact work without.
 * With it, a linked contact's organisation or nickname becomes the person's labels.
 */
object ContactsAccess {
    private var asked by mutableStateOf(0)

    fun isGranted(context: Context): Boolean {
        asked // read, so everything showing contact details updates once access is given
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    /** Call after the permission dialog was answered. */
    fun answered() {
        asked++
    }
}

/**
 * What the contact itself says about who this is: "Acme · Engineer" from the organisation, or
 * else the nickname. Null without access to contacts, or when the contact has neither.
 */
fun contactContext(context: Context, lookupKey: String): String? {
    if (!ContactsAccess.isGranted(context)) return null
    return runCatching {
        val org = ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        val nick = ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
        var company: String? = null
        var title: String? = null
        var nickname: String? = null
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Organization.COMPANY,
                ContactsContract.CommonDataKinds.Organization.TITLE,
            ),
            "${ContactsContract.Data.LOOKUP_KEY} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?)",
            arrayOf(lookupKey, org, nick),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    org -> {
                        company = company ?: c.getString(1)?.trim()?.takeIf { it.isNotEmpty() }
                        title = title ?: c.getString(2)?.trim()?.takeIf { it.isNotEmpty() }
                    }
                    // a nickname is stored in the same column an organisation keeps its company in
                    nick -> nickname = nickname ?: c.getString(1)?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
        }
        listOfNotNull(company, title).joinToString(" · ").ifEmpty { nickname }?.take(80)
    }.getOrNull()
}

private fun readPickedContact(context: Context, uri: Uri): PickedContact? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.LOOKUP_KEY),
        null, null, null
    )?.use { c ->
        if (!c.moveToFirst()) return@use null
        val name = c.getString(0)?.trim().orEmpty()
        if (name.isEmpty()) null else PickedContact(name, c.getString(1))
    }
}.getOrNull()

fun openContact(context: Context, lookupKey: String): Boolean = try {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

@Composable
internal fun rememberContactPicker(onPicked: (PickedContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let { readPickedContact(context, it) }?.let(onPicked)
    }
    return {
        try {
            launcher.launch(null)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.people_contact_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
