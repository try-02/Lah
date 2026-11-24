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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import org.fm.com.di.KontainerAndroid
import org.fm.com.di.KontainerAplikasi
import org.fm.com.di.PengelolaIzinPenyimpanan
import org.fm.com.di.buatKontainerAndroid
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas

private object PemegangKontainer {
    private var kontainerAndroid: KontainerAndroid? = null
    var aplikasi: KontainerAplikasi? = null
    var pintasan: List<LokasiBerkas>? = null
    var sudahCekPromptAwal: Boolean = false

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
            var izinMemoriAktif by remember {
                mutableStateOf(PengelolaIzinPenyimpanan.sudahPunyaIzinSemuaBerkas())
            }
            var tampilkanDialogIzinMemori by remember { mutableStateOf(false) }
            var sedangProsesLauncherIzin by remember { mutableStateOf(false) }
            var permintaanBuka by remember { mutableStateOf<PermintaanBukaLokasi?>(null) }
            var pesanEksternal by remember { mutableStateOf<Pair<Long, String>?>(null) }
            var urutanEvent by remember { mutableLongStateOf(0L) }

            LaunchedEffect(kontainerAndroid) {
                val izinSaatIni = PengelolaIzinPenyimpanan.sudahPunyaIzinSemuaBerkas()
                izinMemoriAktif = izinSaatIni
                if (PemegangKontainer.aplikasi == null) {
                    val (lokasiAwal, pintasan) = kontainerAndroid.muatLokasiAwalDanPintasan()
                    val kontainerBaru = kontainerAndroid.keKontainerAplikasi(lokasiAwal, pintasan)
                    PemegangKontainer.pintasan = pintasan
                    PemegangKontainer.aplikasi = kontainerBaru
                    daftarPintasan = pintasan
                    kontainerAplikasi = kontainerBaru
                }
                if (!PemegangKontainer.sudahCekPromptAwal) {
                    PemegangKontainer.sudahCekPromptAwal = true
                    if (!izinSaatIni && kontainerAndroid.perluTampilkanPromptIzinMemori()) {
                        tampilkanDialogIzinMemori = true
                    }
                }
            }

            val launcherIzinSemuaBerkas = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                sedangProsesLauncherIzin = false
                val izinSekarang = PengelolaIzinPenyimpanan.sudahPunyaIzinSemuaBerkas()
                izinMemoriAktif = izinSekarang
                tampilkanDialogIzinMemori = false
                scope.launch {
                    val pintasanBaru = kontainerAndroid.daftarLokasiPintasan()
                    daftarPintasan = pintasanBaru
                    PemegangKontainer.pintasan = pintasanBaru
                    if (izinSekarang) {
                        kontainerAndroid.tandaiPromptIzinMemoriSelesai()
                        kontainerAndroid.lokasiMemoriInternalSaatIni?.let { lokInternal ->
                            urutanEvent++
                            permintaanBuka = PermintaanBukaLokasi(lokInternal, urutanEvent)
                        }
                        urutanEvent++
                        pesanEksternal = urutanEvent to "Akses Memori Internal (/storage/emulated/0) aktif"
                    } else {
                        urutanEvent++
                        pesanEksternal = urutanEvent to "Izin semua berkas belum aktif. Gunakan SAF untuk memilih folder."
                    }
                }
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

            DisposableEffect(this@MainActivity, kontainerAndroid) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && !sedangProsesLauncherIzin) {
                        val izinSekarang = PengelolaIzinPenyimpanan.sudahPunyaIzinSemuaBerkas()
                        if (izinSekarang != izinMemoriAktif) {
                            izinMemoriAktif = izinSekarang
                            scope.launch {
                                val pintasanBaru = kontainerAndroid.daftarLokasiPintasan()
                                daftarPintasan = pintasanBaru
                                PemegangKontainer.pintasan = pintasanBaru
                                if (izinSekarang) {
                                    kontainerAndroid.tandaiPromptIzinMemoriSelesai()
                                    tampilkanDialogIzinMemori = false
                                    kontainerAndroid.lokasiMemoriInternalSaatIni?.let { lokInternal ->
                                        urutanEvent++
                                        permintaanBuka = PermintaanBukaLokasi(lokInternal, urutanEvent)
                                    }
                                } else {
                                    urutanEvent++
                                    pesanEksternal = urutanEvent to "Izin akses semua berkas dicabut oleh sistem."
                                }
                            }
                        }
                    }
                }
                this@MainActivity.lifecycle.addObserver(observer)
                onDispose {
                    this@MainActivity.lifecycle.removeObserver(observer)
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
                    },
                    izinMemoriInternalAktif = izinMemoriAktif,
                    tampilkanDialogIzinMemori = tampilkanDialogIzinMemori,
                    onMintaIzinMemoriInternal = {
                        tampilkanDialogIzinMemori = false
                        sedangProsesLauncherIzin = true
                        val diluncurkan = PengelolaIzinPenyimpanan.luncurkanPengaturanIzinSemuaBerkas(
                            context = this@MainActivity,
                            launcher = launcherIzinSemuaBerkas
                        )
                        if (!diluncurkan) {
                            sedangProsesLauncherIzin = false
                            urutanEvent++
                            pesanEksternal = urutanEvent to "Pengaturan izin semua berkas tidak tersedia di perangkat ini. Gunakan SAF."
                        }
                    },
                    onTutupDialogIzinMemori = { janganTanyaLagi ->
                        tampilkanDialogIzinMemori = false
                        if (janganTanyaLagi) {
                            scope.launch { kontainerAndroid.tandaiPromptIzinMemoriSelesai() }
                        }
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