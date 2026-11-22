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

@Composable
fun LayarMemuatAplikasi() {
    TemaAplikasi {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    }
}
