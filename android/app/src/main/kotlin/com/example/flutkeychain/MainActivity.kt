package com.example.flutkeychain

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.lifecycle.coroutineScope
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class MainActivity : FlutterActivity() {


    private val ANDROID_KEY_STORE = "AndroidKeyStore"
    val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    val SERVICE_NAME = "myservicename"
    val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    val FILE_NAME = "Keychain.txt"

    private val keystore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
        load(null)
    }
    private lateinit var encryptCipher: Cipher
    private lateinit var methodChannelResult: MethodChannel.Result

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "keychainPlatform"
        ).setMethodCallHandler { call, result ->
            methodChannelResult = result
            when (call.method) {
                "saveData" -> {
                    lifecycle.coroutineScope.launch {
                        val data = call.argument<String>("data")
                        storePassword(data!!)
                    }
                }
                "getData" -> {
                    lifecycle.coroutineScope.launch {
                        getStoredPassword()
                    }
                }
                "deleteData" -> {
                    lifecycle.coroutineScope.launch {
                        deleteFile()
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM).apply {
            init(
                KeyGenParameterSpec.Builder(
                    SERVICE_NAME,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true).build()
            )
        }.generateKey()
    }

    private fun getDecryptCipherForIv(iv: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
        }
    }

    private fun getKey(): SecretKey {
        val existingKey = keystore.getEntry(
            SERVICE_NAME,
            null
        ) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private suspend fun encrypt(bytes: ByteArray, outputStream: OutputStream): ByteArray =
        withContext(Dispatchers.Default) {
            val encryptedBytes = encryptCipher.doFinal(bytes)
            outputStream.use {
                it.write(encryptCipher.iv.size)
                it.write(encryptCipher.iv)
                it.write(encryptedBytes.size)
                it.write(encryptedBytes)
            }
            return@withContext encryptedBytes
        }

    private suspend fun decrypt(inputStream: InputStream): ByteArray = withContext(
        Dispatchers.Default
    ) {
        return@withContext inputStream.use {
            val ivSize = it.read()
            val iv = ByteArray(ivSize)
            it.read(iv)

            val encryptedBytesSize = it.read()
            val encryptedBytes = ByteArray(encryptedBytesSize)
            it.read(encryptedBytes)

            getDecryptCipherForIv(iv).doFinal(encryptedBytes)
        }
    }

    private suspend fun deleteFile() {
        withContext(Dispatchers.IO) {
            keystore.deleteEntry(SERVICE_NAME)
            val file = File(filesDir, FILE_NAME)

            if (file.exists()) file.delete()

            methodChannelResult.success(true)
        }
    }

    private suspend fun getStoredPassword() {
        withContext(Dispatchers.IO) {
            val file = File(filesDir, FILE_NAME)
            if (!file.exists()) {
                methodChannelResult.error(
                    "File does not exists",
                    "File does not exists",
                    "File does not exists"
                )
            } else {

                methodChannelResult.success(decrypt(inputStream = FileInputStream(file)).decodeToString())
            }
        }
    }

    private suspend fun storePassword(password: String) {
        withContext(Dispatchers.IO) {
            encryptCipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getKey())
            }
            val bytes = password.encodeToByteArray()
            val file = File(filesDir, FILE_NAME)

            if (!file.exists()) file.createNewFile()

            val fos = FileOutputStream(file)
            encrypt(bytes = bytes, outputStream = fos)
            methodChannelResult.success(true)
        }
    }

}
