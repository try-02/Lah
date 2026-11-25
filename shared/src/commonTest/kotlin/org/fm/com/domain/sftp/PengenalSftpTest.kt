package org.fm.com.domain.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PengenalSftpTest {

    @Test
    fun buatDanUrai_bolakBalik_tanpaKredensial() {
        val path = "/data/data/com.termux/files/home/a b/[x] y?*"
        val id = PengenalSftp.buat("abc-1", path)
        assertEquals("sftp://abc-1$path", id)
        assertEquals(LokasiSftp("abc-1", path), PengenalSftp.urai(id))
    }

    @Test
    fun normalisasi_tidakPernahKeluarDariRoot() {
        assertEquals("/a", PengenalSftp.normalisasi("/../../a/./b/.."))
        assertEquals("/", PengenalSftp.normalisasi(""))
        assertEquals("/a/b", PengenalSftp.normalisasi("//a///b/"))
    }

    @Test
    fun urai_menolakSkemaLainDanIdTidakValid() {
        assertNull(PengenalSftp.urai("file:///x"))
        assertNull(PengenalSftp.urai("sftp://a b/x"))
        assertNull(PengenalSftp.urai("sftp:///x"))
        assertEquals(LokasiSftp("p", "/"), PengenalSftp.urai("sftp://p"))
    }

    @Test
    fun indukDanNama() {
        assertEquals("/", PengenalSftp.induk("/a"))
        assertEquals("/a", PengenalSftp.induk("/a/b"))
        assertEquals("b", PengenalSftp.nama("/a/b"))
    }

    @Test
    fun gabung_menolakNamaBerbahaya() {
        assertEquals("/a/b", PengenalSftp.gabung("/a", "b"))
        assertFailsWith<IllegalArgumentException> { PengenalSftp.gabung("/a", "..") }
        assertFailsWith<IllegalArgumentException> { PengenalSftp.gabung("/a", "b/c") }
        assertFailsWith<IllegalArgumentException> { PengenalSftp.gabung("/a", "") }
    }

    @Test
    fun normalisasi_menolakNul() {
        assertFailsWith<IllegalArgumentException> { PengenalSftp.normalisasi("/a\u0000b") }
        assertNull(PengenalSftp.urai("sftp://p/a\u0000b"))
    }
}