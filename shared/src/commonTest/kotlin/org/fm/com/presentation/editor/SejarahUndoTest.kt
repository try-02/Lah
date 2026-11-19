package org.fm.com.presentation.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SejarahUndoTest {
    @Test
    fun undoDanRedoMengembalikanTeksDanKursorTepat() {
        var waktu = 0L
        val sejarah = SejarahUndo(maksLangkah = 50, batasJedaGabungMs = 800L) { waktu }

        assertFalse(sejarah.dapatUndo)
        assertFalse(sejarah.dapatRedo)

        sejarah.catatPerubahan("halo", "halo dunia", kursorSebelum = 4, kursorSesudah = 10)
        waktu += 1000L
        sejarah.catatPerubahan("halo dunia", "halo kmp", kursorSebelum = 10, kursorSesudah = 8)

        assertTrue(sejarah.dapatUndo)
        val u1 = assertNotNull(sejarah.undo("halo kmp"))
        assertEquals("halo dunia", u1.teks)
        assertEquals(10, u1.indeksKursor)
        assertTrue(sejarah.dapatRedo)

        val u2 = assertNotNull(sejarah.undo(u1.teks))
        assertEquals("halo", u2.teks)
        assertEquals(4, u2.indeksKursor)
        assertFalse(sejarah.dapatUndo)

        val r1 = assertNotNull(sejarah.redo(u2.teks))
        assertEquals("halo dunia", r1.teks)
        assertEquals(10, r1.indeksKursor)

        // Perubahan baru pasca undo membersihkan tumpukan redo
        waktu += 1000L
        sejarah.catatPerubahan(r1.teks, "halo dunia!", kursorSebelum = 10, kursorSesudah = 11)
        assertFalse(sejarah.dapatRedo)
        assertNull(sejarah.redo("halo dunia!"))
    }

    @Test
    fun ketikanCepatBerurutanDigabungDalamSatuLangkahUndo() {
        var waktu = 100L
        val sejarah = SejarahUndo(batasJedaGabungMs = 800L) { waktu }

        sejarah.catatPerubahan("", "a", kursorSebelum = 0, kursorSesudah = 1)
        waktu += 100L
        sejarah.catatPerubahan("a", "ab", kursorSebelum = 1, kursorSesudah = 2)
        waktu += 100L
        sejarah.catatPerubahan("ab", "abc", kursorSebelum = 2, kursorSesudah = 3)

        val u = assertNotNull(sejarah.undo("abc"))
        assertEquals("", u.teks)
        assertEquals(0, u.indeksKursor)
        assertFalse(sejarah.dapatUndo)
    }

    @Test
    fun batasMaksLangkahMenjagaUkuranRiwayat() {
        var waktu = 0L
        val sejarah = SejarahUndo(maksLangkah = 2, batasJedaGabungMs = 0L) { waktu++ }

        sejarah.catatPerubahan("0", "1", 1, 1)
        sejarah.catatPerubahan("1", "12", 2, 2)
        sejarah.catatPerubahan("12", "123", 3, 3)

        val u1 = assertNotNull(sejarah.undo("123"))
        assertEquals("12", u1.teks)
        val u2 = assertNotNull(sejarah.undo(u1.teks))
        assertEquals("1", u2.teks)
        assertFalse(sejarah.dapatUndo)
    }
}