package org.fm.com.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FilterCariNama
import org.fm.com.domain.model.FilterJenisEntitas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.ItemHasilCariNama
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.ModeCocokNama
import org.fm.com.domain.model.ProgresCariNama
import org.fm.com.domain.model.UtilitasFilterPencarian
import org.fm.com.domain.repository.RepositoriBerkas

private const val INTERVAL_EMISI_ITEM_NAMA = 25

class CariNamaBerkasUseCase(
    private val repositoriBerkas: RepositoriBerkas,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private data class SimpulFolder(
        val lokasi: LokasiBerkas,
        val jalurRelatif: String,
        val kedalaman: Int
    )

    operator fun invoke(
        folderAkar: LokasiBerkas,
        filter: FilterCariNama
    ): Flow<ProgresCariNama> = flow {
        val galatFilter = validasiFilter(filter)
        if (galatFilter != null) {
            emit(ProgresCariNama(selesai = true, galatFatal = galatFilter))
            return@flow
        }

        val pencocokNama = when (val hasil = buatPencocokNama(filter)) {
            is HasilPencocok.Sah -> hasil.fungsi
            is HasilPencocok.Gagal -> {
                emit(ProgresCariNama(selesai = true, galatFatal = hasil.pesan))
                return@flow
            }
        }

        val temuan = mutableListOf<ItemHasilCariNama>()
        val antrean = ArrayDeque<SimpulFolder>()
        val dikunjungi = HashSet<String>()

        antrean.addLast(SimpulFolder(folderAkar, jalurRelatif = "", kedalaman = 0))
        dikunjungi.add("${folderAkar.jenis.name}:${folderAkar.pengenal}")

        var folderDipindai = 0
        var itemDiperiksa = 0
        var peringatanFolder = 0
        var terpotongBatas = false
        var itemSejakEmisi = 0

        while (antrean.isNotEmpty() && !terpotongBatas) {
            currentCoroutineContext().ensureActive()
            val folder = antrean.removeFirst()

            val hasilDaftar = repositoriBerkas.daftarIsi(folder.lokasi).first()
            val daftarAnak = when (hasilDaftar) {
                is HasilOperasi.Sukses -> hasilDaftar.nilai
                is HasilOperasi.Gagal -> {
                    if (folder.kedalaman == 0) {
                        emit(
                            ProgresCariNama(
                                daftarTemuan = temuan.toList(),
                                jumlahFolderDipindai = folderDipindai,
                                jumlahItemDiperiksa = itemDiperiksa,
                                jumlahPeringatanFolder = peringatanFolder,
                                selesai = true,
                                galatFatal = hasilDaftar.pesan
                            )
                        )
                        return@flow
                    } else {
                        peringatanFolder++
                        continue
                    }
                }
            }

            folderDipindai++
            for (entitas in daftarAnak) {
                currentCoroutineContext().ensureActive()
                if (entitas.tersembunyi && !filter.sertakanTersembunyi) {
                    continue
                }

                itemDiperiksa++
                itemSejakEmisi++
                val lokasiRelatif = if (folder.jalurRelatif.isEmpty()) {
                    entitas.nama
                } else {
                    "${folder.jalurRelatif}/${entitas.nama}"
                }

                if (apakahEntitasCocok(entitas, filter, pencocokNama)) {
                    temuan.add(ItemHasilCariNama(entitas = entitas, lokasiRelatif = lokasiRelatif))
                    if (temuan.size >= filter.maksHasil) {
                        terpotongBatas = true
                        break
                    }
                }

                if (entitas.jenis == JenisBerkas.FOLDER &&
                    filter.rekursif &&
                    folder.kedalaman < filter.batasKedalamanMaks
                ) {
                    val kunci = "${entitas.lokasi.jenis.name}:${entitas.lokasi.pengenal}"
                    if (dikunjungi.add(kunci)) {
                        antrean.addLast(
                            SimpulFolder(
                                lokasi = entitas.lokasi,
                                jalurRelatif = lokasiRelatif,
                                kedalaman = folder.kedalaman + 1
                            )
                        )
                    }
                }

                if (itemSejakEmisi >= INTERVAL_EMISI_ITEM_NAMA) {
                    itemSejakEmisi = 0
                    emit(
                        ProgresCariNama(
                            daftarTemuan = temuan.toList(),
                            jumlahFolderDipindai = folderDipindai,
                            jumlahItemDiperiksa = itemDiperiksa,
                            namaItemSaatIni = lokasiRelatif,
                            jumlahPeringatanFolder = peringatanFolder,
                            selesai = false,
                            terpotongBatasHasil = terpotongBatas
                        )
                    )
                }
            }
        }

        emit(
            ProgresCariNama(
                daftarTemuan = temuan.toList(),
                jumlahFolderDipindai = folderDipindai,
                jumlahItemDiperiksa = itemDiperiksa,
                namaItemSaatIni = "",
                jumlahPeringatanFolder = peringatanFolder,
                selesai = true,
                terpotongBatasHasil = terpotongBatas
            )
        )
    }.flowOn(ioDispatcher)

    private fun validasiFilter(filter: FilterCariNama): String? {
        if (filter.ukuranMinByte != null && filter.ukuranMinByte < 0L) {
            return "Ukuran minimum tidak boleh negatif"
        }
        if (filter.ukuranMaksByte != null && filter.ukuranMaksByte < 0L) {
            return "Ukuran maksimum tidak boleh negatif"
        }
        if (filter.ukuranMinByte != null &&
            filter.ukuranMaksByte != null &&
            filter.ukuranMinByte > filter.ukuranMaksByte
        ) {
            return "Ukuran minimum tidak boleh lebih besar dari ukuran maksimum"
        }
        if (filter.waktuUbahMinEpochMs != null &&
            filter.waktuUbahMaksEpochMs != null &&
            filter.waktuUbahMinEpochMs > filter.waktuUbahMaksEpochMs
        ) {
            return "Tanggal mulai tidak boleh melebihi tanggal akhir"
        }
        return null
    }

    private sealed interface HasilPencocok {
        data class Sah(val fungsi: (String) -> Boolean) : HasilPencocok
        data class Gagal(val pesan: String) : HasilPencocok
    }

    private fun buatPencocokNama(filter: FilterCariNama): HasilPencocok {
        val kueri = filter.kueri
        if (kueri.isEmpty()) {
            return HasilPencocok.Sah { true }
        }
        val ignoreCase = !filter.pekaHuruf
        return when (filter.modeCocok) {
            ModeCocokNama.SEBAGIAN -> HasilPencocok.Sah { nama ->
                nama.contains(kueri, ignoreCase = ignoreCase)
            }
            ModeCocokNama.LENGKAP -> HasilPencocok.Sah { nama ->
                nama.equals(kueri, ignoreCase = ignoreCase)
            }
            ModeCocokNama.GLOB -> {
                try {
                    val regex = UtilitasFilterPencarian.globKeRegex(kueri, filter.pekaHuruf)
                    HasilPencocok.Sah { nama -> regex.matches(nama) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    HasilPencocok.Gagal("Pola glob tidak valid: ${e.message ?: "kesalahan pola"}")
                }
            }
            ModeCocokNama.REGEX -> {
                try {
                    val opsi = if (filter.pekaHuruf) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    val regex = Regex(kueri, opsi)
                    HasilPencocok.Sah { nama -> regex.containsMatchIn(nama) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val detail = e.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                    val pesan = if (detail.isNotEmpty()) {
                        "Pola regex tidak valid: $detail"
                    } else {
                        "Pola regex tidak valid"
                    }
                    HasilPencocok.Gagal(pesan)
                }
            }
        }
    }

    private fun apakahEntitasCocok(
        entitas: EntitasBerkas,
        filter: FilterCariNama,
        pencocokNama: (String) -> Boolean
    ): Boolean {
        val adalahFolder = entitas.jenis == JenisBerkas.FOLDER
        when (filter.filterJenis) {
            FilterJenisEntitas.SEMUA -> Unit
            FilterJenisEntitas.BERKAS_SAJA -> if (adalahFolder) return false
            FilterJenisEntitas.FOLDER_SAJA -> if (!adalahFolder) return false
        }

        if (filter.jenisKhusus.isNotEmpty() && entitas.jenis !in filter.jenisKhusus) {
            return false
        }

        if (filter.ekstensiDiizinkan.isNotEmpty()) {
            if (adalahFolder) return false
            val ekstensi = UtilitasFilterPencarian.ambilEkstensi(entitas.nama)
            if (ekstensi !in filter.ekstensiDiizinkan) return false
        }

        val adaFilterUkuran = filter.ukuranMinByte != null || filter.ukuranMaksByte != null
        if (adaFilterUkuran) {
            if (adalahFolder) return false
            val ukuran = entitas.ukuranByte ?: return false
            if (filter.ukuranMinByte != null && ukuran < filter.ukuranMinByte) return false
            if (filter.ukuranMaksByte != null && ukuran > filter.ukuranMaksByte) return false
        }

        val adaFilterWaktu = filter.waktuUbahMinEpochMs != null || filter.waktuUbahMaksEpochMs != null
        if (adaFilterWaktu) {
            val waktu = entitas.waktuUbahEpochMs ?: return false
            if (filter.waktuUbahMinEpochMs != null && waktu < filter.waktuUbahMinEpochMs) return false
            if (filter.waktuUbahMaksEpochMs != null && waktu > filter.waktuUbahMaksEpochMs) return false
        }

        return pencocokNama(entitas.nama)
    }
}