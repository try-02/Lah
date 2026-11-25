package org.fm.com.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.fm.com.PermintaanBukaLokasi
import org.fm.com.di.KontainerAplikasi
import org.fm.com.di.buatKontainerPreview
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisTargetBookmark
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.HasilResolusiBeranda
import org.fm.com.presentation.arsip.IntentArsip
import org.fm.com.presentation.arsip.LayarArsip
import org.fm.com.presentation.arsip.ViewModelArsip
import org.fm.com.presentation.beranda.IntentBeranda
import org.fm.com.presentation.beranda.ViewModelBeranda
import org.fm.com.presentation.bookmark.IntentBookmark
import org.fm.com.presentation.bookmark.LayarBookmark
import org.fm.com.presentation.bookmark.ViewModelBookmark
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
import org.fm.com.presentation.komponen.DialogIzinMemoriSistem
import org.fm.com.presentation.komponen.PenanganKembaliSistem
import org.fm.com.presentation.navigasi.NavigasiUtama
import org.fm.com.presentation.navigasi.TabUtama
import org.fm.com.presentation.pencarian.IntentPencarian
import org.fm.com.presentation.pencarian.LayarPencarian
import org.fm.com.presentation.pencarian.ViewModelPencarian
import org.fm.com.presentation.pengaturan.LayarPengaturan
import org.fm.com.presentation.pengaturan.ViewModelPengaturan
import org.fm.com.presentation.rename.ViewModelBulkRename
import org.fm.com.presentation.tema.TemaAplikasi
import org.fm.com.presentation.terbaru.LayarTerbaru
import org.fm.com.presentation.terbaru.ViewModelTerbaru

private enum class TujuanPemilihFolderSistem {
    FILE_MANAGER,
    EKSTRAK_ARSIP,
    BOOKMARK_PILIH_ULANG,
    BERANDA
}

@Composable
fun Aplikasi(
    modifier: Modifier = Modifier,
    kontainer: KontainerAplikasi = remember { buatKontainerPreview() },
    permintaanBukaEksternal: PermintaanBukaLokasi? = null,
    pesanEksternal: Pair<Long, String>? = null,
    lokasiPintasan: List<LokasiBerkas> = kontainer.lokasiPintasan,
    onPilihFolder: (() -> Unit)? = null,
    onSiapkanAksesLokasi: (suspend (LokasiBerkas) -> LokasiBerkas)? = null,
    izinMemoriInternalAktif: Boolean? = null,
    tampilkanDialogIzinMemori: Boolean = false,
    onMintaIzinMemoriInternal: (() -> Unit)? = null,
    onTutupDialogIzinMemori: ((Boolean) -> Unit)? = null
) {
    TemaAplikasi {
        val scope = rememberCoroutineScope()
        val kunciKontainer = remember(kontainer) { kontainer.hashCode().toString() }

        val vmFilemanager: ViewModelFilemanager = viewModel(key = "fm_$kunciKontainer") {
            ViewModelFilemanager(
                repositoriBerkas = kontainer.repositoriBerkas,
                repositoriOperasi = kontainer.repositoriOperasi,
                riwayatLokasiUseCase = kontainer.riwayatLokasiUseCase
            )
        }
        val vmOperasiMassal: ViewModelOperasiMassal = viewModel(key = "massal_$kunciKontainer") {
            ViewModelOperasiMassal(kontainer.repositoriBerkas, kontainer.repositoriOperasi)
        }
        val vmTerbaru: ViewModelTerbaru = viewModel(key = "terbaru_$kunciKontainer") {
            ViewModelTerbaru(kontainer.riwayatLokasiUseCase)
        }
        val vmBookmark: ViewModelBookmark = viewModel(key = "bookmark_$kunciKontainer") {
            ViewModelBookmark(
                bookmarkUseCase = kontainer.bookmarkUseCase,
                repositoriBerkas = kontainer.repositoriBerkas,
                lokasiPintasanAwal = lokasiPintasan,
                siapkanAksesLokasi = onSiapkanAksesLokasi
            )
        }
        val vmBeranda: ViewModelBeranda = viewModel(key = "beranda_$kunciKontainer") {
            ViewModelBeranda(
                berandaUseCase = kontainer.berandaUseCase,
                siapkanAksesLokasi = onSiapkanAksesLokasi
            )
        }
        val vmPengaturan: ViewModelPengaturan = viewModel(key = "pengaturan_$kunciKontainer") {
            ViewModelPengaturan(
                preferensiUseCase = kontainer.preferensiUseCase,
                riwayatPencarianUseCase = kontainer.riwayatPencarianUseCase,
                riwayatLokasiUseCase = kontainer.riwayatLokasiUseCase
            )
        }
        val vmPencarian: ViewModelPencarian = viewModel(key = "cari_$kunciKontainer") {
            ViewModelPencarian(
                cariNamaUseCase = kontainer.cariNamaBerkasUseCase,
                cariIsiUseCase = kontainer.cariIsiBerkasUseCase,
                riwayatPencarianUseCase = kontainer.riwayatPencarianUseCase
            )
        }
        val vmEditor: ViewModelEditor = viewModel(key = "editor_$kunciKontainer") {
            ViewModelEditor(
                repositoriBerkas = kontainer.repositoriBerkas,
                riwayatPencarianUseCase = kontainer.riwayatPencarianUseCase,
                simpanBerkasUseCase = kontainer.simpanBerkasUseCase,
                ekstrakArsipUseCase = kontainer.ekstrakArsipUseCase
            )
        }
        val vmDiff: ViewModelDiff = viewModel(key = "diff_$kunciKontainer") {
            ViewModelDiff(kontainer.bandingkanBerkasUseCase)
        }
        val vmBulkRename: ViewModelBulkRename = viewModel(key = "rename_$kunciKontainer") {
            ViewModelBulkRename(
                repositoriBerkas = kontainer.repositoriBerkas,
                jalankanRenameMassalUseCase = kontainer.jalankanRenameMassalUseCase
            )
        }
        val vmArsip: ViewModelArsip = viewModel(key = "arsip_$kunciKontainer") {
            ViewModelArsip(
                ekstrakArsipUseCase = kontainer.ekstrakArsipUseCase,
                kompresArsipUseCase = kontainer.kompresArsipUseCase,
                repositoriBerkas = kontainer.repositoriBerkas,
                repositoriOperasi = kontainer.repositoriOperasi,
                lokasiPintasanAwal = lokasiPintasan
            )
        }

        val lokasiAktifFm by remember(vmFilemanager) {
            vmFilemanager.state.map { it.lokasiSaatIni }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = null)

        val infoEditorHeader by remember(vmEditor) {
            vmEditor.state.map { it.jumlahTab to it.tabAktif?.namaBerkas }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = 0 to null)

        val wordWrapUmumAktif by remember(vmPengaturan) {
            vmPengaturan.state.map { it.wordWrapUmum }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = true)

        val statusBackFm by remember(vmFilemanager) {
            vmFilemanager.state
                .map { Triple(it.sedangMultiSelect, it.kataKunciSaring.isNotEmpty(), it.dapatNavigasiKembali) }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = Triple(false, false, false))

        val sedangModeTempel by remember(vmOperasiMassal) {
            vmOperasiMassal.state.map { it.sedangMemilihTujuan }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = false)

        var tabTerpilih by remember { mutableStateOf(TabUtama.FILE) }
        var ruangEditorTerbuka by remember { mutableStateOf(false) }
        var ruangDiffTerbuka by remember { mutableStateOf(false) }
        var ruangArsipTerbuka by remember { mutableStateOf(false) }
        var tujuanPemilihSistem by remember { mutableStateOf(TujuanPemilihFolderSistem.FILE_MANAGER) }

        LaunchedEffect(lokasiPintasan) {
            vmArsip.prosesIntent(IntentArsip.SetLokasiPintasan(lokasiPintasan))
        }

        LaunchedEffect(wordWrapUmumAktif) {
            vmEditor.prosesIntent(IntentEditor.SetWordWrapUmum(wordWrapUmumAktif))
        }

        LaunchedEffect(izinMemoriInternalAktif) {
            if (izinMemoriInternalAktif == false) {
                val lok = lokasiAktifFm
                if (lok != null && lok.jenis == JenisLokasi.POSIX &&
                    lok.pengenal != kontainer.lokasiAwal.pengenal
                ) {
                    vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                }
            }
        }

        LaunchedEffect(kontainer) {
            val prefAwal = kontainer.preferensiUseCase.ambilPreferensi()
            vmEditor.prosesIntent(IntentEditor.SetWordWrapUmum(prefAwal.wordWrapUmum))

            if (vmFilemanager.state.value.lokasiSaatIni == null) {
                val resolusi = kontainer.berandaUseCase.selesaikanLokasiAwal(onSiapkanAksesLokasi)
                val lokasiAwalSiap = when (resolusi) {
                    is HasilResolusiBeranda.GunakanKhusus -> resolusi.lokasiDigunakan
                    is HasilResolusiBeranda.GunakanBawaan,
                    is HasilResolusiBeranda.IzinAtauLokasiHilang ->
                        onSiapkanAksesLokasi?.invoke(resolusi.lokasiDigunakan) ?: resolusi.lokasiDigunakan
                }
                vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiAwalSiap))
                vmPencarian.prosesIntent(IntentPencarian.SetFolderAkar(lokasiAwalSiap))
                if (resolusi is HasilResolusiBeranda.IzinAtauLokasiHilang) {
                    vmFilemanager.prosesIntent(
                        IntentFilemanager.SetPesanInfo(
                            "Beranda tersimpan tidak dapat diakses (${resolusi.pesan}). Dibuka di folder bawaan."
                        )
                    )
                }
            }
        }

        LaunchedEffect(lokasiAktifFm) {
            vmEditor.prosesIntent(IntentEditor.SetLokasiFolderSimpan(lokasiAktifFm))
            if (lokasiAktifFm != null) {
                vmPencarian.prosesIntent(IntentPencarian.SetFolderAkar(lokasiAktifFm))
            }
        }

        LaunchedEffect(permintaanBukaEksternal?.id) {
            val lokasi = permintaanBukaEksternal?.lokasi ?: return@LaunchedEffect
            val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
            val tujuan = tujuanPemilihSistem
            tujuanPemilihSistem = TujuanPemilihFolderSistem.FILE_MANAGER

            when (tujuan) {
                TujuanPemilihFolderSistem.EKSTRAK_ARSIP -> {
                    vmArsip.prosesIntent(IntentArsip.TerimaFolderSistemTujuan(lokasiSiap))
                }
                TujuanPemilihFolderSistem.BOOKMARK_PILIH_ULANG -> {
                    vmBookmark.prosesIntent(IntentBookmark.TerimaLokasiSistemUntukBookmark(lokasiSiap))
                }
                TujuanPemilihFolderSistem.BERANDA -> {
                    vmBeranda.prosesIntent(IntentBeranda.TetapkanBeranda(lokasiSiap))
                }
                TujuanPemilihFolderSistem.FILE_MANAGER -> {
                    val arsipState = vmArsip.state.value
                    if (arsipState.browser.dialogKonfirmasiEkstrak != null || arsipState.pemilihFolder.terbuka) {
                        vmArsip.prosesIntent(IntentArsip.TerimaFolderSistemTujuan(lokasiSiap))
                    } else {
                        ruangEditorTerbuka = false
                        ruangArsipTerbuka = false
                        vmArsip.prosesIntent(IntentArsip.TutupBrowser)
                        tabTerpilih = TabUtama.FILE
                        vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
                    }
                }
            }
        }

        LaunchedEffect(pesanEksternal?.first) {
            val pesan = pesanEksternal?.second ?: return@LaunchedEffect
            vmFilemanager.prosesIntent(IntentFilemanager.SetPesanInfo(pesan))
        }

        if (ruangDiffTerbuka) {
            LayarDiff(
                viewModel = vmDiff,
                onKembali = { ruangDiffTerbuka = false },
                modifier = modifier
            )
        } else if (ruangEditorTerbuka) {
            LayarEditor(
                viewModel = vmEditor,
                modifier = modifier,
                onKembali = {
                    val tabAktif = vmEditor.state.value.tabAktif
                    if (ruangArsipTerbuka && tabAktif?.konteksArsip != null) {
                        vmEditor.prosesIntent(IntentEditor.MintaKeluarDariTabArsip(tabAktif.idTab))
                    } else {
                        ruangEditorTerbuka = false
                        vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                    }
                },
                onArsipDiperbarui = { kembaliKeArsip ->
                    vmArsip.prosesIntent(IntentArsip.SegarkanDariSesi)
                    vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                    if (kembaliKeArsip) {
                        ruangEditorTerbuka = false
                    }
                }
            )
        } else if (ruangArsipTerbuka) {
            LayarArsip(
                viewModel = vmArsip,
                onKembali = {
                    vmArsip.prosesIntent(IntentArsip.TutupBrowser)
                    ruangArsipTerbuka = false
                    vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                },
                onBukaBerkasDiEditor = { entitas, konteksArsip ->
                    vmEditor.prosesIntent(
                        IntentEditor.BukaBerkas(
                            entitas = entitas,
                            konteksArsip = konteksArsip
                        )
                    )
                    ruangEditorTerbuka = true
                },
                modifier = modifier,
                onPilihFolderSistem = if (onPilihFolder != null) {
                    {
                        tujuanPemilihSistem = TujuanPemilihFolderSistem.EKSTRAK_ARSIP
                        onPilihFolder()
                    }
                } else null
            )
        } else {
            val tanganiBackUtama = tabTerpilih != TabUtama.FILE ||
                statusBackFm.first ||
                sedangModeTempel ||
                statusBackFm.second ||
                statusBackFm.third

            PenanganKembaliSistem(aktif = tanganiBackUtama) {
                when {
                    tabTerpilih != TabUtama.FILE -> tabTerpilih = TabUtama.FILE
                    statusBackFm.first -> vmFilemanager.prosesIntent(IntentFilemanager.BersihkanPilihan)
                    sedangModeTempel -> vmOperasiMassal.batalModeTempel()
                    statusBackFm.second -> vmFilemanager.prosesIntent(IntentFilemanager.UbahKataKunciSaring(""))
                    statusBackFm.third -> vmFilemanager.prosesIntent(IntentFilemanager.NavigasiKembali)
                }
            }

            NavigasiUtama(
                tabAktif = tabTerpilih,
                onPilihTab = { tabTerpilih = it },
                jumlahTabEditor = infoEditorHeader.first,
                onBukaEditor = { ruangEditorTerbuka = true },
                modifier = modifier,
                namaTabEditorAktif = infoEditorHeader.second
            ) { tab ->
                when (tab) {
                    TabUtama.FILE -> DaftarBerkasLayar(
                        viewModel = vmFilemanager,
                        onBukaBerkas = { item ->
                            val entitas = EntitasBerkas(
                                lokasi = item.lokasi,
                                nama = item.nama,
                                jenis = item.jenis,
                                ukuranByte = item.ukuranByte,
                                waktuUbahEpochMs = item.waktuUbahEpochMs,
                                tersembunyi = item.tersembunyi,
                                dapatDibaca = item.dapatDibaca,
                                dapatDitulis = item.dapatDitulis
                            )
                            vmEditor.prosesIntent(IntentEditor.BukaBerkas(entitas))
                            ruangEditorTerbuka = true
                        },
                        viewModelOperasiMassal = vmOperasiMassal,
                        viewModelBulkRename = vmBulkRename,
                        viewModelArsip = vmArsip,
                        aksiPlatform = kontainer.aksiPlatform,
                        onBandingkanBerkas = { itemA, itemB ->
                            vmDiff.prosesIntent(
                                IntentDiff.Bandingkan(
                                    lokasiA = itemA.lokasi,
                                    namaA = itemA.nama,
                                    lokasiB = itemB.lokasi,
                                    namaB = itemB.nama
                                )
                            )
                            ruangDiffTerbuka = true
                        },
                        onBukaArsip = { item ->
                            vmArsip.prosesIntent(
                                IntentArsip.BukaArsip(
                                    sumber = item.lokasi,
                                    folderTujuanDefault = lokasiAktifFm
                                )
                            )
                            ruangArsipTerbuka = true
                        },
                        onBookmarkItem = { lokasi, jenisBerkas, adalahFolder ->
                            scope.launch {
                                val res = kontainer.bookmarkUseCase.tambahBookmark(
                                    lokasi = lokasi,
                                    jenisBerkas = jenisBerkas,
                                    adalahFolder = adalahFolder
                                )
                                val pesan = when (res) {
                                    is HasilOperasi.Sukses -> "Ditambahkan ke Bookmark: ${res.nilai.namaTampilan}"
                                    is HasilOperasi.Gagal -> res.pesan
                                }
                                vmFilemanager.prosesIntent(IntentFilemanager.SetPesanInfo(pesan))
                            }
                        },
                        onJadikanBeranda = { lokasi ->
                            vmBeranda.prosesIntent(IntentBeranda.TetapkanBeranda(lokasi))
                            vmFilemanager.prosesIntent(
                                IntentFilemanager.SetPesanInfo(
                                    "Beranda ditetapkan ke '${lokasi.label ?: lokasi.pengenal.substringAfterLast('/')}'"
                                )
                            )
                        },
                        onPilihFolder = if (onPilihFolder != null) {
                            {
                                val arsipState = vmArsip.state.value
                                tujuanPemilihSistem = if (
                                    arsipState.browser.dialogKonfirmasiEkstrak != null ||
                                    arsipState.pemilihFolder.terbuka
                                ) {
                                    TujuanPemilihFolderSistem.EKSTRAK_ARSIP
                                } else {
                                    TujuanPemilihFolderSistem.FILE_MANAGER
                                }
                                onPilihFolder()
                            }
                        } else null,
                        lokasiPintasan = lokasiPintasan,
                        onPilihLokasiPintasan = { lokasi ->
                            scope.launch {
                                val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
                                vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
                            }
                        }
                    )
                    TabUtama.TERBARU -> LayarTerbaru(
                        viewModel = vmTerbaru,
                        onBukaLokasi = { lokasi ->
                            scope.launch {
                                val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
                                tabTerpilih = TabUtama.FILE
                                vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
                            }
                        }
                    )
                    TabUtama.BOOKMARK -> LayarBookmark(
                        viewModel = vmBookmark,
                        onBukaBookmark = { bookmark ->
                            scope.launch {
                                val lokasiSiap = onSiapkanAksesLokasi?.invoke(bookmark.lokasi) ?: bookmark.lokasi
                                when (bookmark.jenisTarget) {
                                    JenisTargetBookmark.FOLDER -> {
                                        tabTerpilih = TabUtama.FILE
                                        vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
                                    }
                                    JenisTargetBookmark.ARSIP -> {
                                        vmArsip.prosesIntent(
                                            IntentArsip.BukaArsip(
                                                sumber = lokasiSiap,
                                                folderTujuanDefault = lokasiAktifFm
                                            )
                                        )
                                        ruangArsipTerbuka = true
                                    }
                                    JenisTargetBookmark.BERKAS -> {
                                        vmEditor.prosesIntent(
                                            IntentEditor.BukaLokasi(
                                                lokasi = lokasiSiap,
                                                nama = bookmark.namaTampilan
                                            )
                                        )
                                        ruangEditorTerbuka = true
                                    }
                                }
                            }
                        },
                        lokasiPintasan = lokasiPintasan,
                        onPilihFolderSistem = if (onPilihFolder != null) {
                            {
                                tujuanPemilihSistem = TujuanPemilihFolderSistem.BOOKMARK_PILIH_ULANG
                                onPilihFolder()
                            }
                        } else null
                    )
                    TabUtama.PENCARIAN -> LayarPencarian(
                        viewModel = vmPencarian,
                        onBukaFolder = { lokasi ->
                            scope.launch {
                                val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
                                tabTerpilih = TabUtama.FILE
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
                    TabUtama.PENGATURAN -> LayarPengaturan(
                        viewModelPengaturan = vmPengaturan,
                        viewModelBeranda = vmBeranda,
                        viewModelEditor = vmEditor,
                        lokasiAktifFileManager = lokasiAktifFm,
                        lokasiPintasan = lokasiPintasan,
                        onPilihFolderSistemUntukBeranda = if (onPilihFolder != null) {
                            {
                                tujuanPemilihSistem = TujuanPemilihFolderSistem.BERANDA
                                onPilihFolder()
                            }
                        } else null,
                        izinMemoriInternalAktif = izinMemoriInternalAktif,
                        onMintaIzinMemoriInternal = onMintaIzinMemoriInternal
                    )
                }
            }
        }

        if (tampilkanDialogIzinMemori && izinMemoriInternalAktif == false && onMintaIzinMemoriInternal != null) {
            DialogIzinMemoriSistem(
                terbuka = true,
                onBukaPengaturanIzin = onMintaIzinMemoriInternal,
                onGunakanSafSaja = { onTutupDialogIzinMemori?.invoke(true) },
                onNanti = { onTutupDialogIzinMemori?.invoke(false) }
            )
        }
    }
}