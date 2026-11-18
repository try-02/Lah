package org.fm.com.data.saf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat

class SafAliranTulisUji {

    @Test
    fun validasiNamaMencegahTraversalDanKarakterIlegal() {
        assertEquals(false, org.fm.com.domain.model.ValidasiNama.sah(".."))
        assertEquals(false, org.fm.com.domain.model.ValidasiNama.sah("."))
        assertEquals(false, org.fm.com.domain.model.ValidasiNama.sah("folder/file.txt"))
        assertEquals(false, org.fm.com.domain.model.ValidasiNama.sah("folder\\file.txt"))
        assertEquals(false, org.fm.com.domain.model.ValidasiNama.sah("berkas\u0000.txt"))
        assertEquals(false, org.fm.com.domain.model.ValidasiNama.sah("   "))
        assertEquals(true, org.fm.com.domain.model.ValidasiNama.sah("catatan_2026.txt"))
    }

    @Test
    fun potonganIsiEqualsDanData() {
        val p1 = PotonganIsi("halo".encodeToByteArray(), selesai = false)
        val p2 = PotonganIsi("halo".encodeToByteArray(), selesai = false)
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
    }
}