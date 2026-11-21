### 3.2 `shared/src/commonMain/kotlin/org/fm/com/di/KontainerAplikasi.kt`
Tambahkan import `org.fm.com.domain.usecase.BandingkanBerkasUseCase`, tambahkan `val bandingkanBerkasUseCase: BandingkanBerkasUseCase` ke `KontainerAplikasi`, dan daftarkan `single { BandingkanBerkasUseCase(get()) }` di `buatKontainerAplikasi(...)`.

### 3.3 `shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/MenuKonteksBerkas.kt`
Tambahkan parameter opsional `onAksiBandingkan: (() -> Unit)? = null` di `BarAksiSeleksi` dan tampilkan ikon bandingkan bila `jumlahTerpilih == 2 && onAksiBandingkan != null`:
```kotlin
        actions = {
            if (jumlahTerpilih == 2 && onAksiBandingkan != null) {
                IconButton(onClick = onAksiBandingkan) {
                    Icon(
                        imageVector = IkonAplikasi.Bandingkan,
                        contentDescription = "Bandingkan 2 berkas terpilih",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(onClick = onPilihSemua) { ... }
            ...
        }
```

### 3.4 `shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/DaftarBerkasLayar.kt`
Tambahkan dukungan membandingkan 2 berkas langsung dari File Manager (baik lewat multi-select 2 berkas ataupun menu *"Bandingkan dengan..."* lintas folder/lokasi):
1. Tambah parameter opsional `onBandingkanBerkas: ((ItemBerkasUi, ItemBerkasUi) -> Unit)? = null` pada `DaftarBerkasLayar(...)`.
2. Tambah state lokal: `var itemPembandingAwal by remember { mutableStateOf<ItemBerkasUi?>(null) }`.
3. Pada `BarAksiSeleksi`:
   ```kotlin
   val duaBerkasTerpilih = remember(state.itemTerpilih, state.daftarItem) {
       if (state.itemTerpilih.size != 2) null
       else {
           val terpilih = state.daftarItem.filter { it.lokasi in state.itemTerpilih && !it.adalahFolder }
           if (terpilih.size == 2) terpilih[0] to terpilih[1] else null
       }
   }
   ```
   Teruskan `onAksiBandingkan = if (duaBerkasTerpilih != null && onBandingkanBerkas != null) { { viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan); onBandingkanBerkas(duaBerkasTerpilih.first, duaBerkasTerpilih.second) } } else null`.
4. Di bawah `BannerTempelBerkas`: bila `itemPembandingAwal != null`, tampilkan banner kecil `"Pilih berkas kedua untuk dibandingkan dengan '${itemPembandingAwal!!.nama}'"` + tombol `Batal`, dan saat item berkas (non-folder) diklik sementara `itemPembandingAwal != null` → panggil `onBandingkanBerkas?.invoke(itemPembandingAwal!!, item)` lalu reset `itemPembandingAwal = null`!
5. Di `MenuKonteksItemBerkas` (di cabang `if (!item.adalahFolder)`): tambahkan menu item `"Bandingkan dengan..."` (ikon `IkonAplikasi.Bandingkan`) ketika `onBandingkan != null`.

### 3.5 `shared/src/commonMain/kotlin/org/fm/com/App.kt`
Tambahkan state ruang kerja Diff (`var ruangDiffTerbuka by remember { mutableStateOf(false) }`) + `vmDiff`:
```kotlin
        val vmDiff: ViewModelDiff = viewModel(key = "diff_$kunciKontainer") {
            ViewModelDiff(kontainer.bandingkanBerkasUseCase)
        }
```
Ketika `ruangDiffTerbuka == true`:
```kotlin
        if (ruangDiffTerbuka) {
            LayarDiff(
                viewModel = vmDiff,
                onKembali = { ruangDiffTerbuka = false }
            )
        } else if (ruangEditorTerbuka) { ... }
```
Dan teruskan `onBandingkanBerkas` ke `DaftarBerkasLayar`:
```kotlin
                            onBandingkanBerkas = { itemA, itemB ->
                                vmDiff.prosesIntent(
                                    IntentDiff.Bandingkan(
                                        lokasiA = itemA.lokasi,
                                        namaA = itemA.nama,
                                        lokasiB = itemB.lokasi,
                                        namaB = itemB.nama
                                    )
                                )
                                ruangDiffTerbuka = true
                            },
```
*(Tambahkan import `org.fm.com.presentation.diff.IntentDiff`, `org.fm.com.presentation.diff.LayarDiff`, `org.fm.com.presentation.diff.ViewModelDiff`.)*

---
