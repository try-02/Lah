package org.fm.com.presentation.komponen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object IkonAplikasi {
    private val kuasHitam = SolidColor(Color.Black)

    private fun ikonGaris(nama: String, blok: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = nama,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = kuasHitam,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
                pathBuilder = blok
            )
        }.build()

    val Folder: ImageVector = ikonGaris("Folder") {
        moveTo(3f, 6.5f)
        lineTo(3f, 18.5f)
        quadTo(3f, 20f, 4.5f, 20f)
        lineTo(19.5f, 20f)
        quadTo(21f, 20f, 21f, 18.5f)
        lineTo(21f, 9f)
        quadTo(21f, 7.5f, 19.5f, 7.5f)
        lineTo(11.8f, 7.5f)
        lineTo(9.8f, 5f)
        lineTo(4.5f, 5f)
        quadTo(3f, 5f, 3f, 6.5f)
        close()
    }

    val Jam: ImageVector = ikonGaris("Jam") {
        moveTo(12f, 3f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 21f, 12f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 3f)
        moveTo(12f, 7.5f)
        lineTo(12f, 12f)
        lineTo(15.5f, 14f)
    }

    val Cari: ImageVector = ikonGaris("Cari") {
        moveTo(10.5f, 4f)
        arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 17f, 10.5f)
        arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.5f, 4f)
        moveTo(15.4f, 15.4f)
        lineTo(20f, 20f)
    }

    val Editor: ImageVector = ikonGaris("Editor") {
        moveTo(14.5f, 4.5f)
        lineTo(19.5f, 9.5f)
        lineTo(9f, 20f)
        lineTo(4f, 20f)
        lineTo(4f, 15f)
        close()
        moveTo(12.5f, 6.5f)
        lineTo(17.5f, 11.5f)
    }

    val Kembali: ImageVector = ikonGaris("Kembali") {
        moveTo(19f, 12f)
        lineTo(5f, 12f)
        moveTo(10.5f, 6.5f)
        lineTo(5f, 12f)
        lineTo(10.5f, 17.5f)
    }

    val Simpan: ImageVector = ikonGaris("Simpan") {
        moveTo(5f, 4f)
        lineTo(16.5f, 4f)
        lineTo(20f, 7.5f)
        lineTo(20f, 19f)
        quadTo(20f, 20f, 19f, 20f)
        lineTo(5f, 20f)
        quadTo(4f, 20f, 4f, 19f)
        lineTo(4f, 5f)
        quadTo(4f, 4f, 5f, 4f)
        close()
        moveTo(8f, 4f)
        lineTo(8f, 9.5f)
        lineTo(15f, 9.5f)
        lineTo(15f, 4f)
        moveTo(7.5f, 20f)
        lineTo(7.5f, 14f)
        lineTo(16.5f, 14f)
        lineTo(16.5f, 20f)
    }

    val Undo: ImageVector = ikonGaris("Undo") {
        moveTo(9f, 8f)
        lineTo(4.5f, 12f)
        lineTo(9f, 16f)
        moveTo(5f, 12f)
        lineTo(14.5f, 12f)
        arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14.5f, 19f)
        lineTo(12f, 19f)
    }

    val Redo: ImageVector = ikonGaris("Redo") {
        moveTo(15f, 8f)
        lineTo(19.5f, 12f)
        lineTo(15f, 16f)
        moveTo(19f, 12f)
        lineTo(9.5f, 12f)
        arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 9.5f, 19f)
        lineTo(12f, 19f)
    }

    val MenuLainnya: ImageVector = ikonGaris("MenuLainnya") {
        moveTo(12f, 5.5f)
        lineTo(12f, 5.6f)
        moveTo(12f, 12f)
        lineTo(12f, 12.1f)
        moveTo(12f, 18.5f)
        lineTo(12f, 18.6f)
    }

    val TampilanDaftar: ImageVector = ikonGaris("TampilanDaftar") {
        moveTo(4f, 6.5f)
        lineTo(5f, 6.5f)
        moveTo(8.5f, 6.5f)
        lineTo(20f, 6.5f)
        moveTo(4f, 12f)
        lineTo(5f, 12f)
        moveTo(8.5f, 12f)
        lineTo(20f, 12f)
        moveTo(4f, 17.5f)
        lineTo(5f, 17.5f)
        moveTo(8.5f, 17.5f)
        lineTo(20f, 17.5f)
    }

    val TampilanKisi: ImageVector = ikonGaris("TampilanKisi") {
        moveTo(4f, 4f)
        lineTo(10f, 4f)
        lineTo(10f, 10f)
        lineTo(4f, 10f)
        close()
        moveTo(14f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 10f)
        lineTo(14f, 10f)
        close()
        moveTo(4f, 14f)
        lineTo(10f, 14f)
        lineTo(10f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(14f, 14f)
        lineTo(20f, 14f)
        lineTo(20f, 20f)
        lineTo(14f, 20f)
        close()
    }

    val Urutkan: ImageVector = ikonGaris("Urutkan") {
        moveTo(4f, 7f)
        lineTo(14f, 7f)
        moveTo(4f, 12f)
        lineTo(11f, 12f)
        moveTo(4f, 17f)
        lineTo(8f, 17f)
        moveTo(17.5f, 6f)
        lineTo(17.5f, 18f)
        moveTo(14.5f, 15f)
        lineTo(17.5f, 18f)
        lineTo(20.5f, 15f)
    }

    val Segarkan: ImageVector = ikonGaris("Segarkan") {
        moveTo(19.5f, 10f)
        arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 18.5f, 16.5f)
        moveTo(19.5f, 5f)
        lineTo(19.5f, 10f)
        lineTo(14.5f, 10f)
    }

    val PilihFolder: ImageVector = ikonGaris("PilihFolder") {
        moveTo(3f, 6.5f)
        lineTo(3f, 18.5f)
        quadTo(3f, 20f, 4.5f, 20f)
        lineTo(19.5f, 20f)
        quadTo(21f, 20f, 21f, 18.5f)
        lineTo(21f, 9f)
        quadTo(21f, 7.5f, 19.5f, 7.5f)
        lineTo(11.8f, 7.5f)
        lineTo(9.8f, 5f)
        lineTo(4.5f, 5f)
        quadTo(3f, 5f, 3f, 6.5f)
        close()
        moveTo(12f, 11f)
        lineTo(12f, 16.5f)
        moveTo(9.5f, 13.8f)
        lineTo(14.5f, 13.8f)
    }

    val Tambah: ImageVector = ikonGaris("Tambah") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    val Tutup: ImageVector = ikonGaris("Tutup") {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }

    val PilihSemua: ImageVector = ikonGaris("PilihSemua") {
        moveTo(4f, 4f)
        lineTo(16f, 4f)
        lineTo(16f, 16f)
        lineTo(4f, 16f)
        close()
        moveTo(7.5f, 10f)
        lineTo(9.5f, 12f)
        lineTo(13f, 8f)
        moveTo(8f, 20f)
        lineTo(20f, 20f)
        lineTo(20f, 8f)
    }

    val Salin: ImageVector = ikonGaris("Salin") {
        moveTo(9f, 9f)
        lineTo(19f, 9f)
        lineTo(19f, 20f)
        lineTo(9f, 20f)
        close()
        moveTo(5f, 15f)
        lineTo(5f, 5f)
        lineTo(15f, 5f)
    }

    val Pindah: ImageVector = ikonGaris("Pindah") {
        moveTo(4f, 7f)
        lineTo(4f, 18f)
        lineTo(19f, 18f)
        moveTo(8f, 12f)
        lineTo(20f, 12f)
        moveTo(16f, 8.5f)
        lineTo(20f, 12f)
        lineTo(16f, 15.5f)
    }

    val Hapus: ImageVector = ikonGaris("Hapus") {
        moveTo(4.5f, 7f)
        lineTo(19.5f, 7f)
        moveTo(9.5f, 4.5f)
        lineTo(14.5f, 4.5f)
        moveTo(6.5f, 7f)
        lineTo(7.5f, 19.5f)
        lineTo(16.5f, 19.5f)
        lineTo(17.5f, 7f)
        moveTo(10f, 11f)
        lineTo(10f, 16f)
        moveTo(14f, 11f)
        lineTo(14f, 16f)
    }

    val GantiNama: ImageVector = ikonGaris("GantiNama") {
        moveTo(4f, 16f)
        lineTo(4f, 20f)
        lineTo(8f, 20f)
        lineTo(18f, 10f)
        lineTo(14f, 6f)
        close()
        moveTo(14f, 20f)
        lineTo(20f, 20f)
    }

    val Bagikan: ImageVector = ikonGaris("Bagikan") {
        moveTo(18f, 5f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = false, 18f, 9f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 18f, 5f)
        moveTo(6f, 10f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = false, 6f, 14f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 10f)
        moveTo(18f, 15f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = false, 18f, 19f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 18f, 15f)
        moveTo(8f, 11f)
        lineTo(16f, 7.5f)
        moveTo(8f, 13f)
        lineTo(16f, 16.5f)
    }

    val BukaDengan: ImageVector = ikonGaris("BukaDengan") {
        moveTo(14f, 5f)
        lineTo(19f, 5f)
        lineTo(19f, 10f)
        moveTo(10f, 14f)
        lineTo(19f, 5f)
        moveTo(18f, 14f)
        lineTo(18f, 19f)
        lineTo(5f, 19f)
        lineTo(5f, 6f)
        lineTo(10f, 6f)
    }

    val Info: ImageVector = ikonGaris("Info") {
        moveTo(12f, 3.5f)
        arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 20.5f, 12f)
        arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 3.5f)
        moveTo(12f, 11f)
        lineTo(12f, 16f)
        moveTo(12f, 8f)
        lineTo(12f, 8.1f)
    }

    val Tautan: ImageVector = ikonGaris("Tautan") {
        moveTo(10f, 14f)
        lineTo(14f, 10f)
        moveTo(8f, 11f)
        lineTo(6f, 13f)
        arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 18f)
        lineTo(13f, 16f)
        moveTo(16f, 13f)
        lineTo(18f, 11f)
        arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 13f, 6f)
        lineTo(11f, 8f)
    }

    val Filter: ImageVector = ikonGaris("Filter") {
        moveTo(4f, 5.5f)
        lineTo(20f, 5.5f)
        lineTo(14f, 12.5f)
        lineTo(14f, 18.5f)
        lineTo(10f, 16.5f)
        lineTo(10f, 12.5f)
        close()
    }

    val Statistik: ImageVector = ikonGaris("Statistik") {
        moveTo(5f, 19f)
        lineTo(19f, 19f)
        moveTo(7f, 19f)
        lineTo(7f, 13f)
        moveTo(12f, 19f)
        lineTo(12f, 7f)
        moveTo(17f, 19f)
        lineTo(17f, 10f)
    }

    val Centang: ImageVector = ikonGaris("Centang") {
        moveTo(5f, 12.5f)
        lineTo(9.5f, 17f)
        lineTo(19f, 7.5f)
    }

    val Berkas: ImageVector = ikonGaris("Berkas") {
        moveTo(6f, 3.5f)
        lineTo(14f, 3.5f)
        lineTo(18.5f, 8f)
        lineTo(18.5f, 20.5f)
        lineTo(6f, 20.5f)
        close()
        moveTo(14f, 3.5f)
        lineTo(14f, 8f)
        lineTo(18.5f, 8f)
    }

    val TukarLokasi: ImageVector = ikonGaris("TukarLokasi") {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }

    val Bandingkan: ImageVector = ikonGaris("Bandingkan") {
        moveTo(4f, 5f)
        lineTo(10f, 5f)
        lineTo(10f, 19f)
        lineTo(4f, 19f)
        close()
        moveTo(14f, 5f)
        lineTo(20f, 5f)
        lineTo(20f, 19f)
        lineTo(14f, 19f)
        close()
    }

    val Naik: ImageVector = ikonGaris("Naik") {
        moveTo(6f, 15f)
        lineTo(12f, 9f)
        lineTo(18f, 15f)
    }

    val Turun: ImageVector = ikonGaris("Turun") {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }

    val Arsip: ImageVector = ikonGaris("Arsip") {
        moveTo(4f, 5f)
        lineTo(20f, 5f)
        lineTo(20f, 9f)
        lineTo(4f, 9f)
        close()
        moveTo(5.5f, 9f)
        lineTo(5.5f, 19.5f)
        lineTo(18.5f, 19.5f)
        lineTo(18.5f, 9f)
        moveTo(10f, 13f)
        lineTo(14f, 13f)
    }

    val Ekstrak: ImageVector = ikonGaris("Ekstrak") {
        moveTo(4.5f, 15f)
        lineTo(4.5f, 19.5f)
        lineTo(19.5f, 19.5f)
        lineTo(19.5f, 15f)
        moveTo(12f, 15f)
        lineTo(12f, 5f)
        moveTo(8.5f, 8.5f)
        lineTo(12f, 5f)
        lineTo(15.5f, 8.5f)
    }

    val Bookmark: ImageVector = ikonGaris("Bookmark") {
        moveTo(6.5f, 4f)
        lineTo(17.5f, 4f)
        lineTo(17.5f, 20f)
        lineTo(12f, 16f)
        lineTo(6.5f, 20f)
        close()
    }

    val Beranda: ImageVector = ikonGaris("Beranda") {
        moveTo(3.5f, 11f)
        lineTo(12f, 4f)
        lineTo(20.5f, 11f)
        moveTo(5.5f, 9.5f)
        lineTo(5.5f, 20f)
        lineTo(18.5f, 20f)
        lineTo(18.5f, 9.5f)
        moveTo(10f, 20f)
        lineTo(10f, 14f)
        lineTo(14f, 14f)
        lineTo(14f, 20f)
    }

    val Pengaturan: ImageVector = ikonGaris("Pengaturan") {
        moveTo(4f, 6.5f)
        lineTo(9f, 6.5f)
        moveTo(13f, 6.5f)
        lineTo(20f, 6.5f)
        moveTo(11f, 4.5f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 8.5f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 11f, 4.5f)

        moveTo(4f, 12f)
        lineTo(14f, 12f)
        moveTo(18f, 12f)
        lineTo(20f, 12f)
        moveTo(16f, 10f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = false, 16f, 14f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16f, 10f)

        moveTo(4f, 17.5f)
        lineTo(7f, 17.5f)
        moveTo(11f, 17.5f)
        lineTo(20f, 17.5f)
        moveTo(9f, 15.5f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = false, 9f, 19.5f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 15.5f)
    }
}