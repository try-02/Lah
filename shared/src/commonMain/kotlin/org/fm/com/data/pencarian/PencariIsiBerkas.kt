package org.fm.com.data.pencarian

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okio.Buffer
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FilterCariIsi
import org.fm.com.domain.model.GalatBacaCariIsi
import org.fm.com.domain.model.HasilCariIsiBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.KecocokanBarisIsi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.ProgresCariIsi
import org.fm.com.domain.model.UtilitasFilterPencarian
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.PemindaiIsiBerkas

private const val UKURAN_SAMPEL_BINER = 8 * 1024
private const val MAKS_PANJANG_BARIS_BUFFER = 256 * 1024
private const val MAKS_PANJANG_CUPLIKAN_TEKS = 160
private const val INTERVAL_EMISI_BERKAS = 10

class PencariIsiBerkas(
    private val repositoriBerkas: RepositoriBerkas,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PemindaiIsiBerkas {

    private data class SimpulFolder(
        val lokasi: LokasiBerkas,
        val jalurRelatif: String,
        val kedalaman: Int
    )

    private enum class EncodingDeteksi {
        BELUM_DITENTUKAN,
        UTF_8,
        UTF_8_BOM,
        UTF_16_LE,
        UTF_16_BE
    }

    private sealed interface StatusPindaiBerkas {
        data class Cocok(val hasil: HasilCariIsiBerkas) : StatusPindaiBerkas
        data object TidakCocok : StatusPindaiBerkas
        data object DilewatiBiner : StatusPindaiBerkas
        data object DilewatiUkuran : StatusPindaiBerkas
        data class GalatBaca(val pesan: String) : StatusPindaiBerkas
    }

    private class PencocokBaris(private val filter: FilterCariIsi) {
        private val ignoreCase = !filter.pekaHuruf
        private val regex: Regex? = if (filter.gunakanRegex) {
            val opsi = if (filter.pekaHuruf) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(filter.kueri, opsi)
        } else {
            null
        }

        data class HasilCocokBaris(
            val jumlahCocok: Int,
            val indeksAwalPertama: Int,
            val indeksAkhirPertama: Int
        )

        fun periksa(baris: String): HasilCocokBaris? {
            if (baris.isEmpty()) return null
            if (regex == null) {
                if (filter.kueri.length > baris.length) return null
                var jumlah = 0
                var awalPertama = -1
                var akhirPertama = -1
                var idx = 0
                while (idx <= baris.length - filter.kueri.length) {
                    val ketemu = baris.indexOf(filter.kueri, startIndex = idx, ignoreCase = ignoreCase)
                    if (ketemu < 0) break
                    val akhir = ketemu + filter.kueri.length
                    if (!filter.kataUtuh || apakahBatasKataUtuh(baris, ketemu, akhir)) {
                        jumlah++
                        if (awalPertama < 0) {
                            awalPertama = ketemu
                            akhirPertama = akhir
                        }
                    }
                    idx = akhir
                }
                return if (jumlah > 0) {
                    HasilCocokBaris(jumlah, awalPertama, akhirPertama)
                } else {
                    null
                }
            } else {
                var jumlah = 0
                var awalPertama = -1
                var akhirPertama = -1
                for (cocok in regex.findAll(baris)) {
                    val awal = cocok.range.first
                    val akhir = cocok.range.last + 1
                    if (filter.kataUtuh && (awal == akhir || !apakahBatasKataUtuh(baris, awal, akhir))) {
                        continue
                    }
                    jumlah++
                    if (awalPertama < 0) {
                        awalPertama = awal
                        akhirPertama = akhir
                    }
                }
                return if (jumlah > 0) {
                    HasilCocokBaris(jumlah, awalPertama, akhirPertama)
                } else {
                    null
                }
            }
        }

        private fun apakahBatasKataUtuh(teks: String, awal: Int, akhirEksklusif: Int): Boolean {
            val kiri = awal == 0 || !apakahKarakterKata(teks[awal - 1])
            val kanan = akhirEksklusif == teks.length || !apakahKarakterKata(teks[akhirEksklusif])
            return kiri && kanan
        }

        private fun apakahKarakterKata(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
    }

    override fun cariIsi(
        folderAkar: LokasiBerkas,
        filter: FilterCariIsi
    ): Flow<ProgresCariIsi> = flow {
        val pencocok = try {
            PencocokBaris(filter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(
                ProgresCariIsi(
                    selesai = true,
                    galatFatal = "Pola regex tidak valid: ${e.message ?: "kesalahan pola"}"
                )
            )
            return@flow
        }

        val hasilDaftarBerkas = mutableListOf<HasilCariIsiBerkas>()
        val daftarGalat = mutableListOf<GalatBacaCariIsi>()
        val antrean = ArrayDeque<SimpulFolder>()
        val dikunjungi = HashSet<String>()

        antrean.addLast(SimpulFolder(folderAkar, jalurRelatif = "", kedalaman = 0))
        dikunjungi.add("${folderAkar.jenis.name}:${folderAkar.pengenal}")

        var jumlahBerkasDipindai = 0
        var jumlahFolderDipindai = 0
        var jumlahBinerDilewati = 0
        var jumlahTerlaluBesarDilewati = 0
        var terpotongBatasHasil = false
        var berkasSejakEmisi = 0

        while (antrean.isNotEmpty() && !terpotongBatasHasil) {
            currentCoroutineContext().ensureActive()
            val folder = antrean.removeFirst()

            val daftarOperasi = repositoriBerkas.daftarIsi(folder.lokasi).first()
            val daftarAnak = when (daftarOperasi) {
                is HasilOperasi.Sukses -> daftarOperasi.nilai
                is HasilOperasi.Gagal -> {
                    if (folder.kedalaman == 0) {
                        emit(
                            ProgresCariIsi(
                                daftarHasil = hasilDaftarBerkas.toList(),
                                jumlahBerkasDipindai = jumlahBerkasDipindai,
                                jumlahFolderDipindai = jumlahFolderDipindai,
                                jumlahBinerDilewati = jumlahBinerDilewati,
                                jumlahTerlaluBesarDilewati = jumlahTerlaluBesarDilewati,
                                daftarGalatBerkas = daftarGalat.toList(),
                                selesai = true,
                                galatFatal = daftarOperasi.pesan
                            )
                        )
                        return@flow
                    } else {
                        daftarGalat.add(
                            GalatBacaCariIsi(
                                lokasiRelatif = folder.jalurRelatif,
                                namaBerkas = folder.lokasi.label ?: folder.jalurRelatif,
                                pesan = daftarOperasi.pesan
                            )
                        )
                        continue
                    }
                }
            }

            jumlahFolderDipindai++
            for (entitas in daftarAnak) {
                currentCoroutineContext().ensureActive()
                if (entitas.tersembunyi && !filter.sertakanTersembunyi) {
                    continue
                }

                val lokasiRelatif = if (folder.jalurRelatif.isEmpty()) {
                    entitas.nama
                } else {
                    "${folder.jalurRelatif}/${entitas.nama}"
                }

                if (entitas.jenis == JenisBerkas.FOLDER) {
                    if (filter.rekursif && folder.kedalaman < filter.batasKedalamanMaks) {
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
                    continue
                }

                if (entitas.jenis == JenisBerkas.TAUTAN) {
                    continue
                }
                if (!lolosFilterKandidatBerkas(entitas, filter)) {
                    continue
                }
                val ukuranMetadata = entitas.ukuranByte
                if (ukuranMetadata != null) {
                    if (ukuranMetadata > filter.batasUkuranPerFileByte) {
                        jumlahTerlaluBesarDilewati++
                        continue
                    }
                    if (filter.ukuranMinByte != null && ukuranMetadata < filter.ukuranMinByte) {
                        continue
                    }
                    if (filter.ukuranMaksByte != null && ukuranMetadata > filter.ukuranMaksByte) {
                        continue
                    }
                }

                jumlahBerkasDipindai++
                berkasSejakEmisi++
                when (val status = pindaiSatuBerkas(entitas, lokasiRelatif, filter, pencocok)) {
                    is StatusPindaiBerkas.Cocok -> {
                        hasilDaftarBerkas.add(status.hasil)
                        if (hasilDaftarBerkas.size >= filter.maksBerkasHasil) {
                            terpotongBatasHasil = true
                        }
                        berkasSejakEmisi = 0
                        emit(
                            ProgresCariIsi(
                                daftarHasil = hasilDaftarBerkas.toList(),
                                jumlahBerkasDipindai = jumlahBerkasDipindai,
                                jumlahFolderDipindai = jumlahFolderDipindai,
                                jumlahBinerDilewati = jumlahBinerDilewati,
                                jumlahTerlaluBesarDilewati = jumlahTerlaluBesarDilewati,
                                daftarGalatBerkas = daftarGalat.toList(),
                                namaBerkasSaatIni = lokasiRelatif,
                                selesai = false,
                                terpotongBatasHasil = terpotongBatasHasil
                            )
                        )
                        if (terpotongBatasHasil) break
                    }
                    is StatusPindaiBerkas.TidakCocok -> Unit
                    is StatusPindaiBerkas.DilewatiBiner -> jumlahBinerDilewati++
                    is StatusPindaiBerkas.DilewatiUkuran -> jumlahTerlaluBesarDilewati++
                    is StatusPindaiBerkas.GalatBaca -> {
                        daftarGalat.add(
                            GalatBacaCariIsi(
                                lokasiRelatif = lokasiRelatif,
                                namaBerkas = entitas.nama,
                                pesan = status.pesan
                            )
                        )
                    }
                }

                if (berkasSejakEmisi >= INTERVAL_EMISI_BERKAS) {
                    berkasSejakEmisi = 0
                    emit(
                        ProgresCariIsi(
                            daftarHasil = hasilDaftarBerkas.toList(),
                            jumlahBerkasDipindai = jumlahBerkasDipindai,
                            jumlahFolderDipindai = jumlahFolderDipindai,
                            jumlahBinerDilewati = jumlahBinerDilewati,
                            jumlahTerlaluBesarDilewati = jumlahTerlaluBesarDilewati,
                            daftarGalatBerkas = daftarGalat.toList(),
                            namaBerkasSaatIni = lokasiRelatif,
                            selesai = false,
                            terpotongBatasHasil = terpotongBatasHasil
                        )
                    )
                }
            }
        }

        emit(
            ProgresCariIsi(
                daftarHasil = hasilDaftarBerkas.toList(),
                jumlahBerkasDipindai = jumlahBerkasDipindai,
                jumlahFolderDipindai = jumlahFolderDipindai,
                jumlahBinerDilewati = jumlahBinerDilewati,
                jumlahTerlaluBesarDilewati = jumlahTerlaluBesarDilewati,
                daftarGalatBerkas = daftarGalat.toList(),
                namaBerkasSaatIni = "",
                selesai = true,
                terpotongBatasHasil = terpotongBatasHasil
            )
        )
    }.flowOn(ioDispatcher)

    private fun lolosFilterKandidatBerkas(entitas: EntitasBerkas, filter: FilterCariIsi): Boolean {
        val ekstensi = UtilitasFilterPencarian.ambilEkstensi(entitas.nama)
        if (filter.ekstensiDiizinkan.isNotEmpty()) {
            return ekstensi in filter.ekstensiDiizinkan
        }
        if (!filter.hanyaFormatTeksDikenal) {
            return true
        }
        return when (entitas.jenis) {
            JenisBerkas.BERKAS_TEKS -> true
            JenisBerkas.BERKAS_GAMBAR -> ekstensi == "svg"
            JenisBerkas.BERKAS_AUDIO, JenisBerkas.BERKAS_VIDEO, JenisBerkas.ARSIP -> false
            JenisBerkas.LAIN -> ekstensi.isEmpty() || ekstensi in EKSTENSI_TEKS_TAMBAHAN
            JenisBerkas.FOLDER, JenisBerkas.TAUTAN -> false
        }
    }

    private suspend fun pindaiSatuBerkas(
        entitas: EntitasBerkas,
        lokasiRelatif: String,
        filter: FilterCariIsi,
        pencocok: PencocokBaris
    ): StatusPindaiBerkas {
        val reader = when (val hasilBuka = repositoriBerkas.bukaUntukBaca(entitas.lokasi)) {
            is HasilOperasi.Sukses -> hasilBuka.nilai
            is HasilOperasi.Gagal -> return StatusPindaiBerkas.GalatBaca(hasilBuka.pesan)
        }

        val buffer = Buffer()
        try {
            val ukuranStream = reader.ukuranByte
            if (ukuranStream != null) {
                if (ukuranStream > filter.batasUkuranPerFileByte) {
                    return StatusPindaiBerkas.DilewatiUkuran
                }
                if (filter.ukuranMinByte != null && ukuranStream < filter.ukuranMinByte) {
                    return StatusPindaiBerkas.TidakCocok
                }
                if (filter.ukuranMaksByte != null && ukuranStream > filter.ukuranMaksByte) {
                    return StatusPindaiBerkas.TidakCocok
                }
            }

            var encoding = EncodingDeteksi.BELUM_DITENTUKAN
            val headerBuffer = Buffer()
            var totalByteDibaca = 0L
            var sudahCekSampelBiner = false
            var sisaByteUtf16: Byte? = null

            var nomorBaris = 0
            var sedangBarisPanjang = false
            var jumlahKecocokanTotal = 0
            val daftarBarisCocok = mutableListOf<KecocokanBarisIsi>()
            var daftarTerpotongBatas = false

            fun prosesSatuBaris(baris: String, adalahAkhirBaris: Boolean) {
                if (!sedangBarisPanjang) {
                    nomorBaris++
                }
                sedangBarisPanjang = !adalahAkhirBaris
                if (baris.indexOf('\u0000') >= 0) {
                    throw BerkasBinerTerdeteksiException()
                }
                if (baris.length >= 16) {
                    val jumlahReplacement = baris.count { it == '\uFFFD' }
                    if (jumlahReplacement * 5 > baris.length) {
                        throw BerkasBinerTerdeteksiException()
                    }
                }
                val cocok = pencocok.periksa(baris) ?: return
                jumlahKecocokanTotal += cocok.jumlahCocok
                if (daftarBarisCocok.size < filter.maksBarisCocokPerFile) {
                    val potongan = buatPotonganTeks(baris, cocok.indeksAwalPertama, cocok.indeksAkhirPertama)
                    daftarBarisCocok.add(
                        KecocokanBarisIsi(
                            nomorBaris = nomorBaris,
                            potonganTeks = potongan,
                            jumlahKecocokanDiBaris = cocok.jumlahCocok,
                            kolomPertama = cocok.indeksAwalPertama + 1
                        )
                    )
                } else {
                    daftarTerpotongBatas = true
                }
            }

            fun prosesIsiBuffer() {
                while (true) {
                    val indeksLf = buffer.indexOf('\n'.code.toByte())
                    if (indeksLf < 0L) break
                    val barisMentah = buffer.readUtf8(indeksLf)
                    buffer.readByte()
                    prosesSatuBaris(barisMentah.trimEnd('\r'), adalahAkhirBaris = true)
                }
                if (buffer.size >= MAKS_PANJANG_BARIS_BUFFER) {
                    val panjangAman = hitungPanjangAmanUtf8(buffer, MAKS_PANJANG_BARIS_BUFFER.toLong())
                    if (panjangAman > 0L) {
                        val blokPanjang = buffer.readUtf8(panjangAman)
                        prosesSatuBaris(blokPanjang.trimEnd('\r'), adalahAkhirBaris = false)
                    }
                }
            }

            fun prosesBytesTerdeteksi(bytes: ByteArray, offsetAwal: Int) {
                if (offsetAwal >= bytes.size) return
                when (encoding) {
                    EncodingDeteksi.UTF_8, EncodingDeteksi.UTF_8_BOM -> {
                        if (!sudahCekSampelBiner) {
                            sudahCekSampelBiner = true
                            val panjangSampel = minOf(bytes.size - offsetAwal, UKURAN_SAMPEL_BINER)
                            if (apakahSampelBiner(bytes, offsetAwal, panjangSampel)) {
                                throw BerkasBinerTerdeteksiException()
                            }
                        } else {
                            for (i in offsetAwal until bytes.size) {
                                if (bytes[i] == 0.toByte()) {
                                    throw BerkasBinerTerdeteksiException()
                                }
                            }
                        }
                        buffer.write(bytes, offsetAwal, bytes.size - offsetAwal)
                    }
                    EncodingDeteksi.UTF_16_LE, EncodingDeteksi.UTF_16_BE -> {
                        val littleEndian = encoding == EncodingDeteksi.UTF_16_LE
                        val sumber = if (sisaByteUtf16 != null) {
                            val gabung = ByteArray(1 + (bytes.size - offsetAwal))
                            gabung[0] = sisaByteUtf16!!
                            bytes.copyInto(gabung, destinationOffset = 1, startIndex = offsetAwal)
                            sisaByteUtf16 = null
                            gabung
                        } else if (offsetAwal == 0) {
                            bytes
                        } else {
                            bytes.copyOfRange(offsetAwal, bytes.size)
                        }
                        val batasPasangan = (sumber.size / 2) * 2
                        if (sumber.size % 2 != 0) {
                            sisaByteUtf16 = sumber[sumber.lastIndex]
                        }
                        if (batasPasangan > 0) {
                            val chars = CharArray(batasPasangan / 2)
                            var idxChar = 0
                            var i = 0
                            while (i < batasPasangan) {
                                val b0 = sumber[i].toInt() and 0xFF
                                val b1 = sumber[i + 1].toInt() and 0xFF
                                val unit = if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
                                if (unit == 0) throw BerkasBinerTerdeteksiException()
                                chars[idxChar++] = unit.toChar()
                                i += 2
                            }
                            buffer.writeUtf8(chars.concatToString())
                        }
                    }
                    EncodingDeteksi.BELUM_DITENTUKAN -> Unit
                }
                prosesIsiBuffer()
            }

            while (true) {
                currentCoroutineContext().ensureActive()
                when (val potonganHasil = reader.potongan()) {
                    is SumberIsiBerkas.HasilPotongan.Isi -> {
                        val p = potonganHasil.potongan
                        if (p.data.isNotEmpty()) {
                            totalByteDibaca += p.data.size
                            if (totalByteDibaca > filter.batasUkuranPerFileByte) {
                                return StatusPindaiBerkas.DilewatiUkuran
                            }
                            if (encoding == EncodingDeteksi.BELUM_DITENTUKAN) {
                                headerBuffer.write(p.data)
                                if (headerBuffer.size >= 4L || p.selesai) {
                                    val awalBytes = headerBuffer.readByteArray()
                                    val (deteksi, offsetBom) = deteksiBom(awalBytes)
                                    encoding = deteksi
                                    prosesBytesTerdeteksi(awalBytes, offsetBom)
                                }
                            } else {
                                prosesBytesTerdeteksi(p.data, 0)
                            }
                        }
                        if (p.selesai) {
                            if (encoding == EncodingDeteksi.BELUM_DITENTUKAN && headerBuffer.size > 0L) {
                                val awalBytes = headerBuffer.readByteArray()
                                val (deteksi, offsetBom) = deteksiBom(awalBytes)
                                encoding = deteksi
                                prosesBytesTerdeteksi(awalBytes, offsetBom)
                            }
                            break
                        }
                    }
                    is SumberIsiBerkas.HasilPotongan.Gagal -> {
                        return StatusPindaiBerkas.GalatBaca(potonganHasil.pesan)
                    }
                }
            }

            if (buffer.size > 0L) {
                val sisaBaris = buffer.readUtf8().trimEnd('\r')
                prosesSatuBaris(sisaBaris, adalahAkhirBaris = true)
            }

            if (filter.ukuranMinByte != null && totalByteDibaca < filter.ukuranMinByte) {
                return StatusPindaiBerkas.TidakCocok
            }
            if (filter.ukuranMaksByte != null && totalByteDibaca > filter.ukuranMaksByte) {
                return StatusPindaiBerkas.TidakCocok
            }

            return if (jumlahKecocokanTotal > 0) {
                StatusPindaiBerkas.Cocok(
                    HasilCariIsiBerkas(
                        entitas = entitas,
                        lokasiRelatif = lokasiRelatif,
                        jumlahKecocokanTotal = jumlahKecocokanTotal,
                        daftarBarisCocok = daftarBarisCocok,
                        daftarTerpotongBatas = daftarTerpotongBatas
                    )
                )
            } else {
                StatusPindaiBerkas.TidakCocok
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: BerkasBinerTerdeteksiException) {
            return StatusPindaiBerkas.DilewatiBiner
        } catch (e: Exception) {
            return StatusPindaiBerkas.GalatBaca(e.message ?: "Gagal membaca berkas saat pemindaian")
        } finally {
            buffer.clear()
            withContext(NonCancellable) {
                reader.tutup()
            }
        }
    }

    private fun deteksiBom(bytes: ByteArray): Pair<EncodingDeteksi, Int> {
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) {
            return EncodingDeteksi.UTF_8_BOM to 3
        }
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return EncodingDeteksi.UTF_16_LE to 2
            if (b0 == 0xFE && b1 == 0xFF) return EncodingDeteksi.UTF_16_BE to 2
        }
        return EncodingDeteksi.UTF_8 to 0
    }

    private fun apakahSampelBiner(bytes: ByteArray, offset: Int, panjang: Int): Boolean {
        if (panjang <= 0) return false
        var jumlahKontrol = 0
        val batas = offset + panjang
        for (i in offset until batas) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return true
            val kontrolIlegal = (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D && b != 0x0C) || b == 0x7F
            if (kontrolIlegal) jumlahKontrol++
        }
        return jumlahKontrol * 20 > panjang
    }

    private fun hitungPanjangAmanUtf8(buffer: Buffer, maksByte: Long): Long {
        val n = minOf(buffer.size, maksByte)
        if (n <= 0L) return 0L
        var mundur = 0L
        while (mundur < 4L && mundur < n) {
            val b = buffer[n - 1L - mundur].toInt() and 0xFF
            if ((b and 0x80) == 0) {
                return n
            }
            if ((b and 0xC0) == 0xC0) {
                val panjangHarapan = when {
                    (b and 0xE0) == 0xC0 -> 2L
                    (b and 0xF0) == 0xE0 -> 3L
                    (b and 0xF8) == 0xF0 -> 4L
                    else -> 1L
                }
                val tersedia = mundur + 1L
                return if (tersedia < panjangHarapan) n - tersedia else n
            }
            mundur++
        }
        return n
    }

    private fun buatPotonganTeks(baris: String, awalCocok: Int, akhirCocok: Int): String {
        val dirapikan = baris.trim()
        if (dirapikan.length <= MAKS_PANJANG_CUPLIKAN_TEKS) {
            return dirapikan
        }
        val awal = (awalCocok - 40).coerceAtLeast(0)
        val akhir = minOf(baris.length, maxOf(akhirCocok + 80, awal + MAKS_PANJANG_CUPLIKAN_TEKS))
        val bagian = baris.substring(awal, akhir).trim()
        val awalanElipsis = if (awal > 0) "…" else ""
        val akhiranElipsis = if (akhir < baris.length) "…" else ""
        return "$awalanElipsis$bagian$akhiranElipsis"
    }

    private class BerkasBinerTerdeteksiException : RuntimeException()

    private companion object {
        val EKSTENSI_TEKS_TAMBAHAN = setOf(
            "conf", "cfg", "env", "gitignore", "gitattributes", "editorconfig",
            "dart", "cs", "jsx", "tsx", "vue", "svelte", "proto", "graphql",
            "makefile", "dockerfile", "svg", "bat", "ps1", "zsh", "fish", "tex"
        )
    }
}