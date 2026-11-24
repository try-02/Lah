@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.rename

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.data.rename.PenerapkanRenameMassal
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.JalankanRenameMassalUseCase

class ViewModelBulkRenameTest {
    @BeforeTest
    fun siapkanMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun pulihkanMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun konfirmasiDitahanSelamaDaftarDiskBelumSelesaiDimuatDanMendeteksiBentrok() = runTest {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/f", "f", JenisLokasi.POSIX, adalahFolder = true))
        val gerbangDaftar = CompletableDeferred<Unit>()
        val entitasDipilih = EntitasBerkas(
            lokasi = LokasiBerkas("/f/a.txt", JenisLokasi.POSIX, "a.txt"),
            nama = "a.txt",
            jenis = JenisBerkas.BERKAS_TEKS
        )
        val entitasLainDiDisk = EntitasBerkas(
            lokasi = LokasiBerkas("/f/a_01.txt", JenisLokasi.POSIX, "a_01.txt"),
            nama = "a_01.txt",
            jenis = JenisBerkas.BERKAS_TEKS
        )
        val repoTertunda = object : RepositoriBerkas by storage {
            override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> = flow {
                gerbangDaftar.await()
                emit(HasilOperasi.Sukses(listOf(entitasDipilih, entitasLainDiDisk)))
            }
        }
        val vm = ViewModelBulkRename(
            repositoriBerkas = repoTertunda,
            jalankanRenameMassalUseCase = JalankanRenameMassalUseCase(PenerapkanRenameMassal(storage.operasi))
        )

        vm.prosesIntent(
            IntentBulkRename.Buka(
                folderInduk = LokasiBerkas("/f", JenisLokasi.POSIX, "f"),
                daftarItem = listOf(entitasDipilih)
            )
        )
        assertTrue(vm.state.value.sedangMemuatIsiFolder)
        assertFalse(vm.state.value.bolehLanjutKonfirmasi)

        gerbangDaftar.complete(Unit)
        assertFalse(vm.state.value.sedangMemuatIsiFolder)
        assertEquals(1, vm.state.value.pratinjau.jumlahTabrakanDenganDisk)
        assertFalse(vm.state.value.bolehLanjutKonfirmasi)
    }
}