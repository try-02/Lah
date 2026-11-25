@file:OptIn(ExperimentalMaterial3Api::class)

package org.fm.com.presentation.sftp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.sftp.JenisAutentikasi
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun DialogKoneksiSftp(
    viewModel: ViewModelSftp,
    onBukaLokasiSftp: (LokasiBerkas) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fontMono = LocalFontMono.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.lokasiSiapDibuka?.first) {
        val acara = state.lokasiSiapDibuka ?: return@LaunchedEffect
        onBukaLokasiSftp(acara.second)
        viewModel.prosesIntent(IntentSftp.KonsumsiLokasiDibuka)
    }

    if (state.dialogTerbuka) {
        Dialog(
            onDismissRequest = { if (!state.sedangMemproses) viewModel.prosesIntent(IntentSftp.TutupDialog) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 700.dp)
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.form.terbuka) {
                                    if (state.form.modeEdit) "Edit Koneksi SFTP" else "Tambah Koneksi SFTP"
                                } else {
                                    "Koneksi SFTP (Termux Full POSIX)"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Termux: pkg install openssh && sshd (port 8022)",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.prosesIntent(IntentSftp.TutupDialog) },
                            enabled = !state.sedangMemproses
                        ) {
                            Icon(IkonAplikasi.Tutup, contentDescription = "Tutup", modifier = Modifier.size(20.dp))
                        }
                    }

                    if (state.sedangMemproses) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    state.pesanGalat?.let { galat ->
                        Text(
                            text = galat,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (!state.form.terbuka) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.daftarProfil.isEmpty()) {
                                Text(
                                    text = "Belum ada profil SFTP. Tambahkan profil Termux (127.0.0.1:8022) untuk rename instan, symlink, dan chmod +x.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                state.daftarProfil.forEach { profil ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                                            .clickable { viewModel.prosesIntent(IntentSftp.BukaProfil(profil)) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = profil.nama,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${profil.namaPengguna}@${profil.host}:${profil.port}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { viewModel.prosesIntent(IntentSftp.BukaFormEdit(profil)) }) {
                                            Icon(IkonAplikasi.GantiNama, contentDescription = "Edit ${profil.nama}", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { viewModel.prosesIntent(IntentSftp.HapusProfil(profil.id)) }) {
                                            Icon(IkonAplikasi.Hapus, contentDescription = "Hapus ${profil.nama}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.prosesIntent(IntentSftp.BukaFormBaruTermux) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Tambah Profil SFTP (Termux)")
                            }
                        }
                    } else {
                        val f = state.form
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = f.nama,
                                onValueChange = { viewModel.prosesIntent(IntentSftp.UbahNama(it)) },
                                label = { Text("Nama Profil") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = f.host,
                                    onValueChange = { viewModel.prosesIntent(IntentSftp.UbahHost(it)) },
                                    label = { Text("Host") },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f)
                                )
                                OutlinedTextField(
                                    value = f.portInput,
                                    onValueChange = { viewModel.prosesIntent(IntentSftp.UbahPort(it)) },
                                    label = { Text("Port") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = f.namaPengguna,
                                onValueChange = { viewModel.prosesIntent(IntentSftp.UbahPengguna(it)) },
                                label = { Text("Username (cek di Termux: whoami)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = f.pathAwal,
                                onValueChange = { viewModel.prosesIntent(IntentSftp.UbahPathAwal(it)) },
                                label = { Text("Path Awal") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                modifier = Modifier.fillMaxWidth()
                            )

                            val authList = JenisAutentikasi.entries
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                authList.forEachIndexed { idx, jenis ->
                                    SegmentedButton(
                                        selected = f.autentikasi == jenis,
                                        onClick = { viewModel.prosesIntent(IntentSftp.UbahAutentikasi(jenis)) },
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = authList.size),
                                        icon = {},
                                        label = { Text(if (jenis == JenisAutentikasi.SANDI) "Sandi" else "Kunci Pribadi") }
                                    )
                                }
                            }

                            if (f.autentikasi == JenisAutentikasi.SANDI) {
                                OutlinedTextField(
                                    value = f.sandiInput,
                                    onValueChange = { viewModel.prosesIntent(IntentSftp.UbahSandi(it)) },
                                    label = { Text(if (f.modeEdit && f.sudahPunyaRahasiaTersimpan) "Kata Sandi Baru (kosongkan jika tetap)" else "Kata Sandi SSH") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.prosesIntent(IntentSftp.BuatKunciEcdsaBaru) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Buat Kunci ECDSA P-256 Baru")
                                }
                                f.kunciPublikDibuat?.let { pubKey ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("Tempel ke ~/.ssh/authorized_keys di Termux:", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            text = pubKey,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        TextButton(onClick = { clipboard.setText(AnnotatedString(pubKey)) }) {
                                            Text("Salin Kunci Publik")
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = f.kunciPemInput,
                                    onValueChange = { viewModel.prosesIntent(IntentSftp.UbahKunciPem(it)) },
                                    label = { Text(if (f.modeEdit && f.sudahPunyaRahasiaTersimpan) "Kunci Pribadi PEM (kosongkan jika tetap)" else "Kunci Pribadi PEM (RSA/ECDSA)") },
                                    maxLines = 4,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = f.frasaSandiInput,
                                    onValueChange = { viewModel.prosesIntent(IntentSftp.UbahFrasaSandi(it)) },
                                    label = { Text("Passphrase Kunci (opsional)") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.prosesIntent(IntentSftp.TutupForm) },
                                enabled = !state.sedangMemproses
                            ) {
                                Text("Kembali")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.prosesIntent(IntentSftp.UjiDanSimpanForm(bukaSetelahnya = true)) },
                                enabled = !state.sedangMemproses
                            ) {
                                Text("Simpan & Buka")
                            }
                        }
                    }
                }
            }
        }
    }

    state.konfirmasiHostKey?.let { konf ->
        AlertDialog(
            onDismissRequest = { viewModel.prosesIntent(IntentSftp.BatalKonfirmasiHostKey) },
            title = {
                Text(
                    text = if (konf.adalahPerubahanKunci) "PERINGATAN: Kunci Host Berubah!" else "Percayai Host SSH?",
                    color = if (konf.adalahPerubahanKunci) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (konf.adalahPerubahanKunci) {
                            "Sidik jari ${konf.profil.host}:${konf.profil.port} berbeda dari yang tersimpan. Pastikan ini bukan penyadapan (hanya lanjutkan jika Anda memang memasang ulang Termux/OpenSSH)."
                        } else {
                            "Periksa sidik jari host ${konf.profil.host}:${konf.profil.port} sebelum mengirim kredensial:"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${konf.jenisKunci}\n${konf.sidikJariBaru}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontWeight = FontWeight.SemiBold)
                    )
                    konf.sidikJariLama?.let { lama ->
                        Text(
                            text = "Sebelumnya: $lama",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.prosesIntent(IntentSftp.KonfirmasiPercayaiHostKey) }) {
                    Text(if (konf.adalahPerubahanKunci) "Perbarui Kunci" else "Percayai & Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentSftp.BatalKonfirmasiHostKey) }) {
                    Text("Batal")
                }
            }
        )
    }
}