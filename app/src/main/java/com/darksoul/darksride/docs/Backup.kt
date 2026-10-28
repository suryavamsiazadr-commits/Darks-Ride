package com.darksoul.darksride.docs

import android.content.Context
import com.darksoul.darksride.garage.GarageStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * One backup file with the Garage and all Documents, locked with a password you choose.
 * Save it anywhere (e.g. your own Google Drive); restore it on a new phone with the same password.
 *
 * Format: "DRB1" + salt(16) + iv(12) + AES-256-GCM( zip ), key from PBKDF2-SHA256(password, salt).
 */
object Backup {
    private val MAGIC = "DRB1".toByteArray()
    private const val ITERATIONS = 150_000

    fun export(c: Context, password: CharArray, out: OutputStream) {
        val zipBytes = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                fun put(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                put("garage.json", GarageStore.exportJson(c).toByteArray())
                val (index, files) = DocVault.exportAll(c)
                put("docs/index.json", index.toByteArray())
                files.forEach { (name, bytes) -> put("docs/files/$name", bytes) }
            }
        }.toByteArray()

        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyFrom(password, salt), GCMParameterSpec(128, iv))
        out.write(MAGIC)
        out.write(salt)
        out.write(iv)
        out.write(cipher.doFinal(zipBytes))
    }

    /** Replaces the Garage and Documents on this phone. Throws if the password is wrong. */
    fun import(c: Context, password: CharArray, input: InputStream) {
        val all = input.readBytes()
        require(all.size > 32 && all.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a Dark's Ride backup" }
        val salt = all.copyOfRange(4, 20)
        val iv = all.copyOfRange(20, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyFrom(password, salt), GCMParameterSpec(128, iv))
        val zipBytes = cipher.doFinal(all, 32, all.size - 32) // fails on a wrong password

        var garage: String? = null
        var index: String? = null
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                when {
                    entry.name == "garage.json" -> garage = String(bytes)
                    entry.name == "docs/index.json" -> index = String(bytes)
                    entry.name.startsWith("docs/files/") -> files[entry.name.removePrefix("docs/files/")] = bytes
                }
            }
        }
        garage?.let { GarageStore.importJson(c, it) }
        index?.let { DocVault.importAll(c, it, files) }
    }

    private fun keyFrom(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
