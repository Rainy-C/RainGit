package me.rain.git.auth

import android.content.Context
import android.os.Environment
import android.os.Build
import android.provider.DocumentsContract
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("rain_git_settings", Context.MODE_PRIVATE)
    private val keyAlias = "rain_git_token_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    fun token(): String? {
        return try {
            val encoded = prefs.getString("secret", null) ?: return null
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun saveToken(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("secret", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun logout() { prefs.edit().remove("secret").apply() }

    var favoriteRepos: Set<String>
        get() = prefs.getStringSet("favorite_repos", emptySet()).orEmpty().toSet()
        set(value) { prefs.edit().putStringSet("favorite_repos", value.toSet()).apply() }

    var oauthClientId: String
        get() = prefs.getString("oauth_client_id", "").orEmpty()
        set(v) { prefs.edit().putString("oauth_client_id", v.trim()).apply() }

    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", true)
        set(v) { prefs.edit().putBoolean("dynamic_color", v).apply() }

    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(v) { prefs.edit().putInt("theme_mode", v.coerceIn(0, 2)).apply() }

    var author: String
        get() = prefs.getString("author", "").orEmpty()
        set(v) { prefs.edit().putString("author", v).apply() }

    var email: String
        get() = prefs.getString("email", "").orEmpty()
        set(v) { prefs.edit().putString("email", v).apply() }

    private val defaultRoot: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "repositories")

    fun root(): File {
        val saved = prefs.getString("root", null)?.let(::File)
        val dir = if (saved != null && (saved.path.startsWith(context.filesDir.path) ||
                    (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager() && (saved.path == "/storage/emulated/0" || saved.path.startsWith("/storage/emulated/0/"))))) {
            saved
        } else defaultRoot
        require(dir.isDirectory || dir.mkdirs()) { "无法建立仓库目录" }
        return dir.canonicalFile
    }

    fun setRootFromTree(uri: Uri): File {
        require(Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) { "请先在系统设置授予所有文件访问权限" }
        require(uri.authority == "com.android.externalstorage.documents") { "仅支持设备主存储目录" }
        val id = DocumentsContract.getTreeDocumentId(uri)
        require(id == "primary:" || id.startsWith("primary:")) { "仅支持设备主存储目录" }
        val relative = id.removePrefix("primary:")
        val base = File("/storage/emulated/0").canonicalFile
        val dir = File(base, relative).canonicalFile
        require(dir.path == base.path || dir.path.startsWith(base.path + File.separator)) { "目录超出允许范围" }
        require(dir.isDirectory && dir.canWrite()) { "文件夹不可写" }
        prefs.edit().putString("root", dir.path).apply()
        return dir
    }

    fun resetRoot() { prefs.edit().remove("root").apply() }
}
