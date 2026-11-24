package org.fm.com

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch
import org.fm.com.di.KontainerAndroid
import org.fm.com.di.KontainerAplikasi
import org.fm.com.di.buatKontainerAndroid
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas

/**
 * Pemegang tingkat-proses agar [KontainerAplikasi] tidak dibuat ulang saat Activity di-recreate
 * (rotasi, multi-window, perubahan tema). Kunci ViewModel di `Aplikasi` diturunkan dari hash kontainer,
 * jadi kontainer baru berarti seluruh ViewModel (termasuk tab editor yang belum disimpan) hilang.
 * Hanya diakses dari thread utama (komposisi/Activity).
 */
private object PemegangKontainer {
    private var kontainerAndroid: KontainerAndroid? = null
    var aplikasi: KontainerAplikasi? = null
    var pintasan: List<LokasiBerkas>? = null

    fun ambilAndroid(context: Context): KontainerAndroid =
        kontainerAndroid ?: buatKontainerAndroid(context.applicationContext).also { kontainerAndroid = it }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            val kontainerAndroid = remember { PemegangKontainer.ambilAndroid(applicationContext) }
            var kontainerAplikasi by remember { mutableStateOf(PemegangKontainer.aplikasi) }
            var daftarPintasan by remember {
                mutableStateOf(PemegangKontainer.pintasan ?: listOf(kontainerAndroid.lokasiRuangKerja))
            }
            var permintaanBuka by remember { mutableStateOf<PermintaanBukaLokasi?>(null) }
            var pesanEksternal by remember { mutableStateOf<Pair<Long, String>?>(null) }
            var urutanEvent by remember { mutableLongStateOf(0L) }

            LaunchedEffect(kontainerAndroid) {
                if (PemegangKontainer.aplikasi != null) return@LaunchedEffect
                val (lokasiAwal, pintasan) = kontainerAndroid.muatLokasiAwalDanPintasan()
                val kontainerBaru = kontainerAndroid.keKontainerAplikasi(lokasiAwal, pintasan)
                PemegangKontainer.pintasan = pintasan
                PemegangKontainer.aplikasi = kontainerBaru
                daftarPintasan = pintasan
                kontainerAplikasi = kontainerBaru
            }

            val launcherPilihTree = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    scope.launch {
                        when (val hasil = kontainerAndroid.safPenyediaLokasi.prosesHasilIntent(result.data)) {
                            is HasilOperasi.Sukses -> {
                                val lokBaru = hasil.nilai
                                if (lokBaru != null) {
                                    daftarPintasan = kontainerAndroid.daftarLokasiPintasan()
                                    PemegangKontainer.pintasan = daftarPintasan
                                    urutanEvent++
                                    permintaanBuka = PermintaanBukaLokasi(lokBaru, urutanEvent)
                                }
                            }
                            is HasilOperasi.Gagal -> {
                                urutanEvent++
                                pesanEksternal = urutanEvent to hasil.pesan
                            }
                        }
                    }
                }
            }

            val kontainerSiap = kontainerAplikasi
            if (kontainerSiap == null) {
                LayarMemuatAplikasi()
            } else {
                App(
                    kontainer = kontainerSiap,
                    permintaanBukaEksternal = permintaanBuka,
                    pesanEksternal = pesanEksternal,
                    lokasiPintasan = daftarPintasan,
                    onPilihFolder = {
                        val intent = kontainerAndroid.safPenyediaLokasi.buatIntentPilihTree(
                            permintaanBuka?.lokasi
                        )
                        launcherPilihTree.launch(intent)
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}