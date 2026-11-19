@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.fm.com

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.launch
import org.fm.com.data.ios.DokumenPenerimaLokasi
import org.fm.com.di.buatKontainerIos
import org.fm.com.domain.model.HasilOperasi
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

fun MainViewController() = ComposeUIViewController {
    val scope = rememberCoroutineScope()
    val kontainerIos = remember { buatKontainerIos() }
    val kontainerAplikasi = remember(kontainerIos) { kontainerIos.keKontainerAplikasi() }
    val penerimaLokasi = remember { DokumenPenerimaLokasi() }

    var permintaanBuka by remember { mutableStateOf<PermintaanBukaLokasi?>(null) }
    var pesanEksternal by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var urutanEvent by remember { mutableLongStateOf(0L) }

    penerimaLokasi.onLokasiDenganBookmarkDipilih = { hasil ->
        when (hasil) {
            is HasilOperasi.Sukses -> {
                scope.launch {
                    kontainerIos.simpanBookmarkLokasi(hasil.nilai.lokasi, hasil.nilai.bookmarkBase64)
                    urutanEvent++
                    permintaanBuka = PermintaanBukaLokasi(hasil.nilai.lokasi, urutanEvent)
                }
            }
            is HasilOperasi.Gagal -> {
                urutanEvent++
                pesanEksternal = urutanEvent to hasil.pesan
            }
        }
    }

    App(
        kontainer = kontainerAplikasi,
        permintaanBukaEksternal = permintaanBuka,
        pesanEksternal = pesanEksternal,
        onPilihFolder = {
            val topVc = cariTopViewController()
            if (topVc != null) {
                val picker = penerimaLokasi.buatPickerFolder()
                topVc.presentViewController(picker, animated = true, completion = null)
            }
        },
        onSiapkanAksesLokasi = { lokasi ->
            kontainerIos.siapkanAksesLokasi(lokasi)
        }
    )
}

private fun cariTopViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes
    val windowScene = scenes.firstOrNull {
        it is UIWindowScene && it.activationState == UISceneActivationStateForegroundActive
    } as? UIWindowScene ?: scenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
    val keyWindow = windowScene?.windows?.firstOrNull {
        (it as? UIWindow)?.isKeyWindow() == true
    } as? UIWindow ?: windowScene?.windows?.firstOrNull() as? UIWindow
    var top = keyWindow?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}