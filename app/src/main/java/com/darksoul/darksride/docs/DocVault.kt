package com.darksoul.darksride.docs

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class DocType(val label: String) {
    LICENCE("Driving licence"),
    RC("Registration (RC)"),
    INSURANCE("Insurance"),
    PUC("PUC certificate"),
    SERVICE("Service record"),
    OTHER("Other")
}

/** One stored file (a PDF or a photo), encrypted in the vault folder. */
data class DocFile(val name: String, val mime: String)

data class DocItem(
    val id: String,
    val type: DocType,
    val title: String,
    val number: String,
    /** UTC-midnight millis (see garage.Days), or null. */
    val expiry: Long?,
    val files: List<DocFile>,
    val added: Long
)

/** Just a title and a date: used for "Due soon" without unlocking the vault. */
data class DocExpiry(val title: String, val expiry: Long)

/**
 * Encryption for the vault: AES-256-GCM with a key that lives in the phone's secure
 * hardware (Android Keystore) and can never be copied off it.
 */
internal object VaultCrypto {
    private const val ALIAS = "darksride_vault"
    private const val IV_BYTES = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }
}

/**
 * The documents folder. Files and the list of documents are encrypted on this phone only;
 * nothing is uploaded. Only titles and expiry dates are kept unencrypted, for reminders.
 */
object DocVault {
    private const val DIR = "vault"
    private const val INDEX = "index.bin"
    private const val EXPIRIES = "expiries.json"

    private val _items = MutableStateFlow<List<DocItem>>(emptyList())
    val items: StateFlow<List<DocItem>> = _items.asStateFlow()
    private var loaded = false

    private fun dir(c: Context) = File(c.filesDir, DIR).apply { mkdirs() }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val f = File(dir(c), INDEX)
        _items.value = if (f.exists()) {
            runCatching { parse(String(VaultCrypto.decrypt(f.readBytes()))) }.getOrDefault(emptyList())
        } else emptyList()
    }

    /** Hides the list from memory (the screen locks). */
    fun forget() {
        loaded = false
        _items.value = emptyList()
    }

    fun save(c: Context, item: DocItem) {
        load(c)
        val others = _items.value.filterNot { it.id == item.id }
        write(c, (others + item).sortedBy { it.type.ordinal })
    }

    fun delete(c: Context, item: DocItem) {
        load(c)
        item.files.forEach { File(dir(c), it.name).delete() }
        write(c, _items.value.filterNot { it.id == item.id })
    }

    /** Copies a picked or scanned file into the vault, encrypted. */
    fun importFile(c: Context, uri: Uri, mimeHint: String? = null): DocFile? = runCatching {
        val bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val mime = mimeHint ?: c.contentResolver.getType(uri) ?: "application/octet-stream"
        storeBytes(c, bytes, mime)
    }.getOrNull()

    internal fun storeBytes(c: Context, bytes: ByteArray, mime: String): DocFile {
        val name = UUID.randomUUID().toString() + ".bin"
        File(dir(c), name).writeBytes(VaultCrypto.encrypt(bytes))
        return DocFile(name, mime)
    }

    fun read(c: Context, file: DocFile): ByteArray =
        VaultCrypto.decrypt(File(dir(c), file.name).readBytes())

    fun newItem(type: DocType, title: String, number: String, expiry: Long?, files: List<DocFile>) =
        DocItem(UUID.randomUUID().toString(), type, title, number, expiry, files, System.currentTimeMillis())

    /** Expiry dates for "Due soon" (readable without unlocking). */
    fun expiries(c: Context): List<DocExpiry> = runCatching {
        val f = File(dir(c), EXPIRIES)
        if (!f.exists()) return emptyList()
        val a = JSONArray(f.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            DocExpiry(o.getString("title"), o.getLong("expiry"))
        }
    }.getOrDefault(emptyList())

    // ---------- Backup ----------

    /** The list as plain JSON plus every file decrypted, for the password-protected backup. */
    internal fun exportAll(c: Context): Pair<String, Map<String, ByteArray>> {
        load(c)
        val files = _items.value.flatMap { it.files }.associate { it.name to read(c, it) }
        return toJson(_items.value).toString() to files
    }

    internal fun importAll(c: Context, indexJson: String, files: Map<String, ByteArray>) {
        dir(c).listFiles()?.forEach { it.delete() }
        files.forEach { (name, bytes) -> File(dir(c), name).writeBytes(VaultCrypto.encrypt(bytes)) }
        loaded = true
        write(c, parse(indexJson))
    }

    // ---------- Storage ----------

    private fun write(c: Context, list: List<DocItem>) {
        _items.value = list
        File(dir(c), INDEX).writeBytes(VaultCrypto.encrypt(toJson(list).toString().toByteArray()))
        val exp = JSONArray(list.filter { it.expiry != null }.map {
            JSONObject().put("title", it.title).put("expiry", it.expiry)
        })
        File(dir(c), EXPIRIES).writeText(exp.toString())
    }

    private fun toJson(list: List<DocItem>) = JSONArray(list.map { d ->
        JSONObject().put("id", d.id).put("type", d.type.name).put("title", d.title)
            .put("number", d.number).put("expiry", d.expiry ?: JSONObject.NULL).put("added", d.added)
            .put("files", JSONArray(d.files.map { JSONObject().put("name", it.name).put("mime", it.mime) }))
    })

    private fun parse(json: String): List<DocItem> {
        val a = JSONArray(json)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val files = o.optJSONArray("files") ?: JSONArray()
            DocItem(
                id = o.getString("id"),
                type = DocType.entries.firstOrNull { it.name == o.optString("type") } ?: DocType.OTHER,
                title = o.optString("title"),
                number = o.optString("number"),
                expiry = if (o.isNull("expiry")) null else o.getLong("expiry"),
                files = (0 until files.length()).map { j ->
                    files.getJSONObject(j).let { DocFile(it.getString("name"), it.optString("mime")) }
                },
                added = o.optLong("added")
            )
        }
    }
}
