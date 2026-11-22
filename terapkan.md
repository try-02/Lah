4. shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/DaftarBerkasLayar.kt

ganti kode ini:

    onPilihLokasiPintasan: ((LokasiBerkas) -> Unit)? = null,
    onBandingkanBerkas: ((ItemBerkasUi, ItemBerkasUi) -> Unit)? = null,
    onBukaArsip: ((ItemBerkasUi) -> Unit)? = null
) {

dengan kode ini:

    onPilihLokasiPintasan: ((LokasiBerkas) -> Unit)? = null,
    onBandingkanBerkas: ((ItemBerkasUi, ItemBerkasUi) -> Unit)? = null,
    onBukaArsip: ((ItemBerkasUi) -> Unit)? = null,
    onBookmarkItem: ((LokasiBerkas, JenisBerkas, Boolean) -> Unit)? = null,
    onJadikanBeranda: ((LokasiBerkas) -> Unit)? = null
) {

ganti kode ini:

                        IconButton(onClick = { viewModel.prosesIntent(IntentFilemanager.Segarkan) }) {
                            Icon(
                                imageVector = IkonAplikasi.Segarkan,
                                contentDescription = "Segarkan daftar",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

dengan kode ini:

                        if (onBookmarkItem != null && lokasiAktif != null && state.kemampuanUi.dapatBookmark) {
                            IconButton(onClick = {
                                onBookmarkItem(lokasiAktif, JenisBerkas.FOLDER, true)
                            }) {
                                Icon(
                                    imageVector = IkonAplikasi.Bookmark,
                                    contentDescription = "Bookmark folder ini",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.prosesIntent(IntentFilemanager.Segarkan) }) {
                            Icon(
                                imageVector = IkonAplikasi.Segarkan,
                                contentDescription = "Segarkan daftar",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

ganti kode ini:

                if (onPilihFolder != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                tampilSwitcherLokasi = false
                                onPilihFolder()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = IkonAplikasi.PilihFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Tambah / Pilih Folder Lain...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

dengan kode ini:

                if (onJadikanBeranda != null && lokasiAktif != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                tampilSwitcherLokasi = false
                                onJadikanBeranda(lokasiAktif)
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = IkonAplikasi.Beranda,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Jadikan Folder Ini sebagai Beranda",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (onPilihFolder != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                tampilSwitcherLokasi = false
                                onPilihFolder()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = IkonAplikasi.PilihFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Tambah / Pilih Folder Lain...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

ganti kedua pemanggilan MenuKonteksItemBerkas (di LazyColumn &
LazyVerticalGrid): (di blok LazyColumn) ganti kode ini:

                                        onBagikan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBagikan(item)) },
                                        onGantiNama = { viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(item)) },
                                        onProperti = { viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(item)) },
                                        onHapus = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(item.lokasi))) }
                                    )
                                }
                            }
                        }
                    }
                } else {

dengan kode ini:

                                        onBookmark = if (onBookmarkItem != null) {
                                            { onBookmarkItem(item.lokasi, item.jenis, item.adalahFolder) }
                                        } else {
                                            null
                                        },
                                        onBagikan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBagikan(item)) },
                                        onGantiNama = { viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(item)) },
                                        onProperti = { viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(item)) },
                                        onHapus = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(item.lokasi))) }
                                    )
                                }
                            }
                        }
                    }
                } else {

(di blok LazyVerticalGrid) ganti kode ini:

                                        onBagikan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBagikan(item)) },
                                        onGantiNama = { viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(item)) },
                                        onProperti = { viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(item)) },
                                        onHapus = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(item.lokasi))) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

dengan kode ini:

                                        onBookmark = if (onBookmarkItem != null) {
                                            { onBookmarkItem(item.lokasi, item.jenis, item.adalahFolder) }
                                        } else {
                                            null
                                        },
                                        onBagikan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBagikan(item)) },
                                        onGantiNama = { viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(item)) },
                                        onProperti = { viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(item)) },
                                        onHapus = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(item.lokasi))) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

ganti kode ini:

    onBukaArsip: (() -> Unit)? = null,
    onEkstrak: (() -> Unit)? = null,
    onKompres: (() -> Unit)? = null
) {

dengan kode ini:

    onBukaArsip: (() -> Unit)? = null,
    onEkstrak: (() -> Unit)? = null,
    onKompres: (() -> Unit)? = null,
    onBookmark: (() -> Unit)? = null
) {

ganti kode ini:

        if (onKompres != null) {
            MenuItemDenganAlasan(
                judul = "Kompres...",
                ikon = IkonAplikasi.Arsip,
                didukung = kemampuan.dapatKompres,
                alasan = kemampuan.alasan(OperasiBerkas.MENGKOMPRES),
                onKlik = { onTutup(); onKompres() }
            )
        }

dengan kode ini:

        if (onKompres != null) {
            MenuItemDenganAlasan(
                judul = "Kompres...",
                ikon = IkonAplikasi.Arsip,
                didukung = kemampuan.dapatKompres,
                alasan = kemampuan.alasan(OperasiBerkas.MENGKOMPRES),
                onKlik = { onTutup(); onKompres() }
            )
        }
        if (onBookmark != null) {
            MenuItemDenganAlasan(
                judul = "Bookmark",
                ikon = IkonAplikasi.Bookmark,
                didukung = kemampuan.dapatBookmark,
                alasan = kemampuan.alasan(OperasiBerkas.MEMBOOKMARK),
                onKlik = { onTutup(); onBookmark() }
            )
        }

5. shared/src/commonMain/kotlin/org/fm/com/di/KontainerAplikasi.kt

ganti kode ini:

import org.fm.com.data.persistensi.MemoriRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatPencarian

dengan kode ini:

import org.fm.com.data.persistensi.MemoriRepositoriBeranda
import org.fm.com.data.persistensi.MemoriRepositoriBookmark
import org.fm.com.data.persistensi.MemoriRepositoriPreferensi
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatPencarian

ganti kode ini:

import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.domain.repository.RepositoriRiwayatPencarian
import org.fm.com.domain.usecase.BandingkanBerkasUseCase
import org.fm.com.domain.usecase.CariIsiBerkasUseCase

dengan kode ini:

import org.fm.com.domain.repository.RepositoriBeranda
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriBookmark
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriPreferensi
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.domain.repository.RepositoriRiwayatPencarian
import org.fm.com.domain.usecase.BandingkanBerkasUseCase
import org.fm.com.domain.usecase.BerandaUseCase
import org.fm.com.domain.usecase.BookmarkUseCase
import org.fm.com.domain.usecase.CariIsiBerkasUseCase

ganti kode ini:

import org.fm.com.domain.usecase.PelaksanaRenameMassal
import org.fm.com.domain.usecase.PelaksanaSimpanTransaksional
import org.fm.com.domain.usecase.RiwayatLokasiUseCase

dengan kode ini:

import org.fm.com.domain.usecase.PelaksanaRenameMassal
import org.fm.com.domain.usecase.PelaksanaSimpanTransaksional
import org.fm.com.domain.usecase.PreferensiUseCase
import org.fm.com.domain.usecase.RiwayatLokasiUseCase

ganti kode ini:

    val ekstrakArsipUseCase: EkstrakArsipUseCase,
    val kompresArsipUseCase: KompresArsipUseCase,
    val lokasiAwal: LokasiBerkas,

dengan kode ini:

    val ekstrakArsipUseCase: EkstrakArsipUseCase,
    val kompresArsipUseCase: KompresArsipUseCase,
    val bookmarkUseCase: BookmarkUseCase,
    val berandaUseCase: BerandaUseCase,
    val preferensiUseCase: PreferensiUseCase,
    val lokasiAwal: LokasiBerkas,

ganti kode ini:

                single { EkstrakArsipUseCase(get()) }
                single { KompresArsipUseCase(get()) }
            }
        )
    }
    val koin = koinApp.koin
    return KontainerAplikasi(
        repositoriBerkas = koin.get(),
        repositoriOperasi = koin.get(),
        repositoriRiwayat = koin.get(),
        riwayatLokasiUseCase = koin.get(),
        repositoriRiwayatPencarian = koin.get(),
        riwayatPencarianUseCase = koin.get(),
        simpanBerkasUseCase = koin.get(),
        cariNamaBerkasUseCase = koin.get(),
        cariIsiBerkasUseCase = koin.get(),
        bandingkanBerkasUseCase = koin.get(),
        jalankanRenameMassalUseCase = koin.get(),
        registriArsipProvider = koin.get(),
        ekstrakArsipUseCase = koin.get(),
        kompresArsipUseCase = koin.get(),
        lokasiAwal = lokasiAwal,

dengan kode ini:

                single { EkstrakArsipUseCase(get()) }
                single { KompresArsipUseCase(get()) }
                single { BookmarkUseCase(get(), get()) }
                single { BerandaUseCase(get(), get(), lokasiAwal) }
                single { PreferensiUseCase(get()) }
            }
        )
    }
    val koin = koinApp.koin
    return KontainerAplikasi(
        repositoriBerkas = koin.get(),
        repositoriOperasi = koin.get(),
        repositoriRiwayat = koin.get(),
        riwayatLokasiUseCase = koin.get(),
        repositoriRiwayatPencarian = koin.get(),
        riwayatPencarianUseCase = koin.get(),
        simpanBerkasUseCase = koin.get(),
        cariNamaBerkasUseCase = koin.get(),
        cariIsiBerkasUseCase = koin.get(),
        bandingkanBerkasUseCase = koin.get(),
        jalankanRenameMassalUseCase = koin.get(),
        registriArsipProvider = koin.get(),
        ekstrakArsipUseCase = koin.get(),
        kompresArsipUseCase = koin.get(),
        bookmarkUseCase = koin.get(),
        berandaUseCase = koin.get(),
        preferensiUseCase = koin.get(),
        lokasiAwal = lokasiAwal,

ganti kode ini:

            single<RepositoriRiwayatLokasi> { MemoriRepositoriRiwayatLokasi() }
            single<RepositoriRiwayatPencarian> { MemoriRepositoriRiwayatPencarian() }
        }
    )
}

dengan kode ini:

            single<RepositoriRiwayatLokasi> { MemoriRepositoriRiwayatLokasi() }
            single<RepositoriRiwayatPencarian> { MemoriRepositoriRiwayatPencarian() }
            single<RepositoriBookmark> { MemoriRepositoriBookmark() }
            single<RepositoriBeranda> { MemoriRepositoriBeranda() }
            single<RepositoriPreferensi> { MemoriRepositoriPreferensi() }
        }
    )
}

6. shared/src/androidMain/kotlin/org/fm/com/di/KontainerAndroid.kt

ganti kode ini:

import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.PenyediaDriverBasisDataAndroid
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatPencarian
import org.fm.com.data.persistensi.buatDatabaseAplikasi

dengan kode ini:

import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.PenyediaDriverBasisDataAndroid
import org.fm.com.data.persistensi.SqlDelightRepositoriBeranda
import org.fm.com.data.persistensi.SqlDelightRepositoriBookmark
import org.fm.com.data.persistensi.SqlDelightRepositoriPreferensi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatPencarian
import org.fm.com.data.persistensi.buatDatabaseAplikasi

ganti kode ini:

import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.domain.repository.RepositoriRiwayatPencarian

dengan kode ini:

import org.fm.com.domain.repository.RepositoriBeranda
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriBookmark
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriPreferensi
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.domain.repository.RepositoriRiwayatPencarian

ganti kode ini:

            single<RepositoriRiwayatLokasi> { repositoriRiwayat }
            single<RepositoriRiwayatPencarian> { repositoriRiwayatPencarian }
        }
    )
}

dengan kode ini:

            single<RepositoriRiwayatLokasi> { repositoriRiwayat }
            single<RepositoriRiwayatPencarian> { repositoriRiwayatPencarian }
            single<RepositoriBookmark> { SqlDelightRepositoriBookmark(database) }
            single<RepositoriBeranda> { SqlDelightRepositoriBeranda(database) }
            single<RepositoriPreferensi> { SqlDelightRepositoriPreferensi(database) }
        }
    )
}

7. shared/src/iosMain/kotlin/org/fm/com/di/KontainerIos.kt

ganti kode ini:

import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.PenyediaDriverBasisDataIos
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatPencarian
import org.fm.com.data.persistensi.buatDatabaseAplikasi

dengan kode ini:

import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.PenyediaDriverBasisDataIos
import org.fm.com.data.persistensi.SqlDelightRepositoriBeranda
import org.fm.com.data.persistensi.SqlDelightRepositoriBookmark
import org.fm.com.data.persistensi.SqlDelightRepositoriPreferensi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatPencarian
import org.fm.com.data.persistensi.buatDatabaseAplikasi

ganti kode ini:

import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.domain.repository.RepositoriRiwayatPencarian

dengan kode ini:

import org.fm.com.domain.repository.RepositoriBeranda
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriBookmark
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriPreferensi
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.domain.repository.RepositoriRiwayatPencarian

ganti kode ini:

            single<RepositoriRiwayatLokasi> { repositoriRiwayat }
            single<RepositoriRiwayatPencarian> { repositoriRiwayatPencarian }
        }
    )
}

dengan kode ini:

            single<RepositoriRiwayatLokasi> { repositoriRiwayat }
            single<RepositoriRiwayatPencarian> { repositoriRiwayatPencarian }
            single<RepositoriBookmark> { SqlDelightRepositoriBookmark(database) }
            single<RepositoriBeranda> { SqlDelightRepositoriBeranda(database) }
            single<RepositoriPreferensi> { SqlDelightRepositoriPreferensi(database) }
        }
    )
}

8. shared/src/commonMain/kotlin/org/fm/com/App.kt

(Tetap menjaga entry point App(...) yang dipanggil MainActivity.kt &
MainViewController.kt, didelegasikan ke Aplikasi(...) di presentation/).

ganti kode ini:

package org.fm.com

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.fm.com.di.KontainerAplikasi
import org.fm.com.di.buatKontainerPreview
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.arsip.IntentArsip
import org.fm.com.presentation.arsip.LayarArsip
import org.fm.com.presentation.arsip.ViewModelArsip
import org.fm.com.presentation.diff.IntentDiff
import org.fm.com.presentation.diff.LayarDiff
import org.fm.com.presentation.diff.ViewModelDiff
import org.fm.com.presentation.editor.IntentEditor
import org.fm.com.presentation.editor.LayarEditor
import org.fm.com.presentation.editor.ViewModelEditor
import org.fm.com.presentation.filemanager.DaftarBerkasLayar
import org.fm.com.presentation.filemanager.IntentFilemanager
import org.fm.com.presentation.filemanager.ViewModelFilemanager
import org.fm.com.presentation.filemanager.ViewModelOperasiMassal
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.pencarian.IntentPencarian
import org.fm.com.presentation.pencarian.LayarPencarian
import org.fm.com.presentation.pencarian.ViewModelPencarian
import org.fm.com.presentation.rename.ViewModelBulkRename
import org.fm.com.presentation.tema.TemaAplikasi
import org.fm.com.presentation.terbaru.LayarTerbaru
import org.fm.com.presentation.terbaru.ViewModelTerbaru

data class PermintaanBukaLokasi(
    val lokasi: LokasiBerkas,
    val id: Long
)

@Composable
fun App(
    kontainer: KontainerAplikasi = remember { buatKontainerPreview() },
    permintaanBukaEksternal: PermintaanBukaLokasi? = null,
    pesanEksternal: Pair<Long, String>? = null,
    lokasiPintasan: List<LokasiBerkas> = kontainer.lokasiPintasan,
    onPilihFolder: (() -> Unit)? = null,
    onSiapkanAksesLokasi: (suspend (LokasiBerkas) -> LokasiBerkas)? = null
) {

dengan kode ini:

package org.fm.com

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fm.com.di.KontainerAplikasi
import org.fm.com.di.buatKontainerPreview
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.Aplikasi
import org.fm.com.presentation.tema.TemaAplikasi

data class PermintaanBukaLokasi(
    val lokasi: LokasiBerkas,
    val id: Long
)

@Composable
fun App(
    kontainer: KontainerAplikasi = remember { buatKontainerPreview() },
    permintaanBukaEksternal: PermintaanBukaLokasi? = null,
    pesanEksternal: Pair<Long, String>? = null,
    lokasiPintasan: List<LokasiBerkas> = kontainer.lokasiPintasan,
    onPilihFolder: (() -> Unit)? = null,
    onSiapkanAksesLokasi: (suspend (LokasiBerkas) -> LokasiBerkas)? = null
) {
    Aplikasi(
        kontainer = kontainer,
        permintaanBukaEksternal = permintaanBukaEksternal,
        pesanEksternal = pesanEksternal,
        lokasiPintasan = lokasiPintasan,
        onPilihFolder = onPilihFolder,
        onSiapkanAksesLokasi = onSiapkanAksesLokasi
    )
}

ganti kode ini:

    TemaAplikasi {
        val scope = rememberCoroutineScope()
        val kunciKontainer = remember(kontainer) { kontainer.hashCode().toString() }
...
                        else -> LayarPencarian(
                            viewModel = vmPencarian,
                            onBukaFolder = { lokasi ->
                                scope.launch {
                                    val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
                                    tabTerpilih = 0
                                    vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
                                }
                            },
                            onBukaBerkasDiEditor = { entitas, nomorBaris ->
                                vmEditor.prosesIntent(
                                    IntentEditor.BukaBerkas(entitas = entitas, barisTujuan = nomorBaris)
                                )
                                ruangEditorTerbuka = true
                            }
                        )
                    }
                }
            }
        }
    }
}

(hapus seluruh badan lama App mulai dari TemaAplikasi { sampai penutup fungsi
App tepat sebelum @Composable fun LayarMemuatAplikasi() — sehingga App.kt hanya
berisi PermintaanBukaLokasi, App(...) yang memanggil Aplikasi(...), dan
LayarMemuatAplikasi()).
