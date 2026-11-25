package org.fm.com.di

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

object PengelolaIzinPenyimpanan {

    fun apakahManifestDeklarasiIzinSemuaBerkas(context: Context): Boolean {
        val info = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }.getOrNull() ?: return false
        return info.requestedPermissions?.contains(Manifest.permission.MANAGE_EXTERNAL_STORAGE) == true
    }

    fun sudahPunyaIzinSemuaBerkas(): Boolean =
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)

    fun cariDirektoriMemoriInternal(context: Context? = null): File? {
        val statusMount = runCatching { Environment.getExternalStorageState() }.getOrNull()
        if (statusMount != Environment.MEDIA_MOUNTED &&
            statusMount != Environment.MEDIA_MOUNTED_READ_ONLY
        ) {
            return null
        }

        val dirDariVolume = if (context != null) {
            runCatching {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                storageManager?.primaryStorageVolume?.directory
            }.getOrNull()
        } else {
            null
        }

        @Suppress("DEPRECATION")
        val kandidat = dirDariVolume ?: runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        return kandidat?.takeIf { it.exists() && it.isDirectory }
    }

    fun buatLokasiMemoriInternalJikaDiizinkan(context: Context): LokasiBerkas? {
        if (!sudahPunyaIzinSemuaBerkas()) return null
        val dir = cariDirektoriMemoriInternal(context) ?: return null
        return LokasiBerkas(
            pengenal = dir.absolutePath,
            jenis = JenisLokasi.POSIX,
            label = "Memori Internal"
        )
    }

    /**
     * Meluncurkan halaman pengaturan "All Files Access".
     * Tidak memakai FLAG_ACTIVITY_NEW_TASK agar callback ActivityResultLauncher menunggu
     * sampai pengguna benar-benar kembali dari layar Settings.
     */
    fun luncurkanPengaturanIzinSemuaBerkas(
        context: Context,
        launcher: ActivityResultLauncher<Intent>
    ): Boolean {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        val daftarIntent = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        )
        for (intent in daftarIntent) {
            try {
                launcher.launch(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
        return false
    }
}