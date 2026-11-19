package org.fm.com

import android.app.Activity
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
import org.fm.com.di.KontainerAplikasi
import org.fm.com.di.buatKontainerAndroid
import org.fm.com.domain.model.HasilOperasi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            val kontainerAndroid = remember { buatKontainerAndroid(applicationContext) }
            var kontainerAplikasi by remember { mutableStateOf<KontainerAplikasi?>(null) }
            var daftarPintasan by remember { mutableStateOf(listOf(kontainerAndroid.lokasiRuangKerja)) }
            var permintaanBuka by remember { mutableStateOf<PermintaanBukaLokasi?>(null) }
            var pesanEksternal by remember { mutableStateOf<Pair<Long, String>?>(null) }
            var urutanEvent by remember { mutableLongStateOf(0L) }

            LaunchedEffect(kontainerAndroid) {
                val lokasiAwal = kontainerAndroid.muatLokasiAwal()
                val pintasan = kontainerAndroid.daftarLokasiPintasan()
                daftarPintasan = pintasan
                kontainerAplikasi = kontainerAndroid.keKontainerAplikasi(lokasiAwal, pintasan)
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