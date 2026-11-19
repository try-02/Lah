package org.fm.com.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.fm.com.domain.model.FilterCariIsi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.ProgresCariIsi

interface PemindaiIsiBerkas {
    fun cariIsi(folderAkar: LokasiBerkas, filter: FilterCariIsi): Flow<ProgresCariIsi>
}

class CariIsiBerkasUseCase(
    private val pemindaiIsi: PemindaiIsiBerkas
) {
    operator fun invoke(
        folderAkar: LokasiBerkas,
        filter: FilterCariIsi
    ): Flow<ProgresCariIsi> {
        val galat = validasiFilter(filter)
        if (galat != null) {
            return flowOf(ProgresCariIsi(selesai = true, galatFatal = galat))
        }
        return pemindaiIsi.cariIsi(folderAkar, filter)
    }

    private fun validasiFilter(filter: FilterCariIsi): String? {
        if (filter.kueri.isEmpty()) {
            return "Kata kunci pencarian isi tidak boleh kosong"
        }
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
        if (filter.gunakanRegex) {
            try {
                val opsi = if (filter.pekaHuruf) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(filter.kueri, opsi)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = e.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                return if (detail.isNotEmpty()) {
                    "Pola regex tidak valid: $detail"
                } else {
                    "Pola regex tidak valid"
                }
            }
        }
        return null
    }
}