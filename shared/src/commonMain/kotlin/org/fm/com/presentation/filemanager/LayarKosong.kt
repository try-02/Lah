package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun LayarStatusFilemanager(
    sedangMemuat: Boolean,
    pesanGalat: String?,
    jumlahItem: Int,
    promptLokasi: String = "~/$",
    onMuatUlang: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontMono = LocalFontMono.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            sedangMemuat -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "memuat daftar berkas...",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            pesanGalat != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = pesanGalat,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = onMuatUlang) {
                        Text("Coba Lagi")
                    }
                }
            }
            jumlahItem == 0 -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "$promptLokasi ls  // 0 item",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = fontMono,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}