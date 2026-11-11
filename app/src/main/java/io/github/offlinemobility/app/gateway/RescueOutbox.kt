package io.github.offlinemobility.app.gateway

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.offlinemobility.app.core.gateway.OutboxCodec
import io.github.offlinemobility.app.core.gateway.RescueReport
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Rescue messages waiting for a network, kept across app restarts.
 *
 * Stored in noBackupFilesDir (never backed up or transferred), encrypted with AES-256-GCM under a
 * non-exportable Android Keystore key. Entries older than [MAX_AGE_MS] are discarded.
 */
class RescueOutbox(context: Context) {
    private val file = File(context.noBackupFilesDir, "outbox.bin")
    private val lock = Any()

    /** Keeps one entry per original message: the one heard with the fewest relays. */
    fun add(report: RescueReport) = synchronized(lock) {
        val all = readAll()
        val existing = all.firstOrNull { it.key == report.key }
        if (existing != null && existing.hop <= report.hop) return@synchronized
        writeAll(all.filterNot { it.key == report.key } + report)
    }

    fun pending(now: Long = System.currentTimeMillis()): List<RescueReport> = synchronized(lock) {
        readAll().filter { now - it.heardAtMs <= MAX_AGE_MS }
    }

    fun remove(keys: Set<String>) = synchronized(lock) {
        writeAll(readAll().filterNot { it.key in keys })
    }

    private fun readAll(): List<RescueReport> {
        if (!file.exists()) return emptyList()
        val bytes = runCatching { decrypt(file.readBytes()) }.getOrNull() ?: return emptyList()
        return OutboxCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    private fun writeAll(reports: List<RescueReport>) {
        val now = System.currentTimeMillis()
        val kept = reports.filter { now - it.heardAtMs <= MAX_AGE_MS }.takeLast(MAX_ENTRIES)
        if (kept.isEmpty()) {
            file.delete()
            return
        }
        val tmp = File(file.parentFile, "outbox.tmp")
        tmp.writeBytes(encrypt(OutboxCodec.encode(kept).toByteArray(Charsets.UTF_8)))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val ivSize = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + ivSize)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return cipher.doFinal(data, 1 + ivSize, data.size - 1 - ivSize)
    }

    private companion object {
        const val KEY_ALIAS = "rescue_outbox"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAX_AGE_MS = 7 * 24 * 60 * 60_000L
        const val MAX_ENTRIES = 500
    }
}
