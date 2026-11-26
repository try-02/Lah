# PRODUCT REQUIREMENTS DOCUMENT (PRD)
**File Manager & Advanced Code Editor**  
**Versi:** 1.0 | **Status:** Rancangan Awal Produk  
**Platform:** Android & iOS | **Teknologi:** Kotlin Multiplatform (KMP), Compose Multiplatform, Clean Architecture

---

## 1. Visi, Pengguna & Prinsip Produk

**Visi:** Membangun aplikasi pengelola file lintas platform yang menggabungkan kemudahan *file manager* harian dengan editor teks/kode, perbandingan file (*diff*), pencarian teks rekursif, pengelolaan arsip, dan penggantian nama massal yang ringan serta aman pada perangkat Android dan iOS (termasuk perangkat berspesifikasi terbatas).

### Profil Pengguna
- **Pengguna Umum:** Menjelajahi folder, salin/pindah/hapus/ganti nama/bagikan file, mencari berdasarkan nama/format/ukuran, membuka & mengekstrak arsip, serta mengelola bookmark. Mengutamakan UI sederhana dan konfirmasi jelas sebelum aksi berisiko.
- **Pengguna Tingkat Lanjut (Pengembang):** Membaca & menyunting kode, membandingkan isi dua file, mencari/mengganti teks secara massal, mengelola arsip bertingkat, memeriksa metadata/statistik kode, serta *bulk rename* berbasis pola. Mengutamakan navigasi cepat dan akurasi hasil.

### Prinsip Produk
- **Local-first & Private:** Semua operasi berjalan lokal tanpa mengunggah isi, nama, atau path file ke server eksternal.
- **Fast & Memory-conscious:** Pemrosesan berat (daftar besar, pencarian, diff, statistik) berjalan di luar thread UI secara asinkron, dapat dibatalkan, dan memakai *streaming* untuk file besar.
- **Safe by default:** Operasi destruktif dan tabrakan nama wajib dikonfirmasi; kegagalan simpan tidak boleh menghilangkan perubahan lokal.
- **Platform-aware & Extensible:** Fitur mengikuti kapabilitas penyimpanan OS secara jujur; dukungan bahasa kode, format arsip, dan penyimpanan dapat diperluas via *adapter*.
- **Clean Architecture & Penamaan Indonesia:** Domain murni Kotlin tanpa ketergantungan platform. Seluruh simbol internal proyek (kelas, fungsi, variabel, state) memakai Bahasa Indonesia, kecuali kontrak API/framework yang diwajibkan.

---

## 2. Target Platform & Kompatibilitas

| Platform | Cakupan Rilis yang Didukung | Konfigurasi Target |
| :--- | :--- | :--- |
| **Android** | 7 generasi: Android 11 (API 30) s.d. Android 17 (API 37), plus pengujian ekstra Android 12L (API 32) | `minSdk = 30`, `compileSdk = 37`, `targetSdk` mengikuti syarat rilis Google Play terbaru |
| **iOS** | 7 rilis utama: iOS 14, 15, 16, 17, 18, 26, 27 *(Apple melompati nomor 19–25)* | `deploymentTarget = iOS 14.0`, Xcode sesuai versi KMP/CMP terpilih |

- **Catatan Framework:** Compose Multiplatform 1.12.2 mendukung minimum Android 5.0 (API 21) dan iOS 14, namun produk ini sengaja menetapkan batas minimum Android di API 30.
- **Pengujian & Matriks Dependensi:** Wajib uji pada min. 1 perangkat fisik + lingkungan uji per generasi OS (termasuk variasi OEM Android dan ukuran layar iPhone/iPad). Versi Kotlin, CMP, Gradle, AGP, SQLDelight, dan Koin harus dikunci pada kombinasi stabil yang teruji—dilarang memperbarui dependensi secara terpisah tanpa uji kompatibilitas.

---

## 3. Ruang Lingkup & Prioritas Rilis

| Prioritas | Cakupan Fitur |
| :--- | :--- |
| **P0 (Wajib Rilis v1.0)** | Struktur KMP & Clean Architecture; File manager (navigasi, daftar/kisi, operasi dasar & massal); Bookmark & homepage; Editor teks dasar (multi-tab dasar, undo/redo, cari & ganti, lompat baris, word wrap, mode hanya baca, dialog tutup, penyimpanan aman & cek konflik simpan, statistik teks dasar); Diff berdampingan & *unified*; Pencarian nama & isi file (filter format/ukuran, rekursif, batal); *Bulk rename* dengan pratinjau; Dukungan arsip utama terverifikasi; Penanganan izin Android/iOS; Benchmark performa & memori. |
| **P1 (Penyempurnaan Coding)** | Perluasan bahasa & *syntax highlighting*; Auto-indent, pencocokan kurung, & *code folding*; Statistik kode lanjutan; Mode diff *inline* & *fokus perubahan*; Bookmark lokasi dalam arsip; Arsip bertingkat lebih luas & edit file di dalam arsip (format yang mendukung); Tab editor lengkap & deteksi perubahan eksternal *real-time*; Mode khusus file sangat besar; Integrasi opsional Termux; Ekspor patch; Penyempurnaan UX & aksesibilitas. |
| **P2 (Lanjutan)** | *Three-way diff* & penggabungan manual; Integrasi Git; Penyedia penyimpanan tambahan; Indeks pencarian lokal opsional (dapat dimatikan & dihapus); Navigasi simbol lanjut & integrasi LSP jika layak; Format arsip tambahan. |
| **Di Luar Ruang Lingkup v1** | Kompilasi di perangkat, terminal shell umum, debugger/LSP penuh, eksekusi program sembarang, akses file sistem via root/jailbreak, sinkronisasi cloud dasar, dan kolaborasi *real-time*. |

---

## 4. Spesifikasi Fitur

### 4.1 File Manager
- **Penjelajahan:** Tampilan daftar & kisi; ikon tipe file; info nama, ukuran, dan tanggal modifikasi; navigasi bertingkat dengan *breadcrumb*; urutkan (nama, ukuran, tipe, tanggal; naik/turun); tampil/sembunyi file tersembunyi (bila didukung); multi-select; riwayat lokasi terbaru; pemuatan bertahap (*lazy loading*) tanpa memuat isi file ke memori.
- **Operasi & Menu Tekan Lama:** Salin, Pindah, Ganti nama, Hapus (wajib konfirmasi), Bagikan (fasilitas OS), Buat folder/file kosong, Bookmark, Kompres, Ekstrak (bila item arsip), Buat *symbolic link* (bila didukung), Lihat properti, Salin nama file, Salin lokasi/path, dan Buka dengan editor/penampil sesuai.
  - Operasi massal menampilkan jumlah item, menyesuaikan menu untuk item terpilih, dan menampilkan progres untuk proses panjang.
- **Aturan Path & Kapabilitas:** Tampilkan path POSIX hanya jika tersedia secara asli. Pada SAF/URI atau sandbox iOS, tampilkan URI atau pengenal lokasi asli (dilarang membuat path root palsu). Setiap *repository* harus menyediakan matriks kapabilitas penyimpanan; opsi tak didukung wajib dinonaktifkan atau dijelaskan alasannya, dan kegagalan dilarang dilaporkan sebagai sukses.

### 4.2 Pencarian File (Nama & Isi)
- **Mode:** Folder saat ini saja atau rekursif ke seluruh subfolder yang dapat diakses.
- **Pencarian Nama:** Filter ekstensi/format, ukuran (min/maks/rentang), jenis (file/folder), dan tanggal modifikasi (lanjutan). Opsi: peka huruf besar-kecil, sebagian/lengkap, pola glob, dan regex (lanjutan).
- **Pencarian Isi:** Filter ekstensi, rentang ukuran, format teks diizinkan, batas ukuran per file. Opsi: peka huruf, cocokkan kata utuh, regex (lanjutan).
  - Hasil menampilkan nama, lokasi relatif, nomor baris, potongan teks, dan jumlah kecocokan. Menekan hasil langsung membuka file (dan menuju baris terkait di editor).
- **Kinerja:** Berjalan di luar thread UI, dapat dibatalkan, menampilkan progres, melewati file biner, memproses file besar secara bertahap (*streaming*), dan tidak berhenti total hanya karena error baca pada satu file.

### 4.3 Editor Teks & Kode
- **Kemampuan Dasar (P0):** Multi-tab, indikator perubahan belum disimpan, undo/redo, nomor baris (tetap akurat saat *word wrap* aktif), seleksi/salin/tempel, mode hanya baca (otomatis aktif jika penyimpanan tidak dapat ditulis; tetap mengizinkan salin & cari; indikator jelas), *word wrap* (per file/umum), navigasi nomor baris (validasi input & batas jumlah baris), dan penyimpanan manual. File biner diarahkan ke penampil biner atau ditolak secara aman.
- **Cari & Ganti:** Cari berikutnya/sebelumnya, ganti satu/seluruh kecocokan (tampilkan jumlah ubahan, butuh konfirmasi sebelum eksekusi massal), peka huruf, kata utuh, regex (pesan error jelas jika tidak valid), memperhatikan posisi kursor/seleksi, serta riwayat pencarian lokal yang dapat dihapus.
- **Penyorotan Sintaks:** Berbasis aturan/parser bahasa (bukan sekadar kumpulan regex sederhana), dapat diperluas melalui *adapter*, dengan *fallback* teks biasa (penyorotan sintaks bukan pengganti IntelliSense/LSP).
  - **Bahasa prioritas:** Kotlin, Java, Swift, C, C++, C#, Python, JavaScript, TypeScript, HTML, CSS, JSON, XML, SQL, Shell/Bash, YAML, TOML, Markdown, Gradle, Rust, Go, PHP, Dart.
- **Fitur Lanjutan (P1):** Deteksi & pengaturan indentasi (tab/spasi, ukuran, auto-indent), pencocokan tanda kurung, sorot baris aktif, penanda error sintaks, pelipatan blok kode (*folding*), riwayat posisi kursor, daftar simbol/struktur, pilih encoding, deteksi LF/CRLF, tampilkan *whitespace*, serta tema & ukuran huruf. Pemrosesan parser harus bertahap; kegagalan parser tidak boleh menggagalkan pembukaan file.
- **Statistik File:** Ukuran (byte & satuan terbaca), jumlah baris (total, kosong, tidak kosong), kata, karakter, panjang baris terpanjang, akhir baris, encoding, dan jumlah baris kode (bila konsisten). Dihitung di *background*, dapat dibatalkan, dan statistik teks tidak boleh ditampilkan untuk file biner.
- **Penutupan & Penyimpanan Aman:**
  - Jika ada perubahan belum disimpan saat menutup tab/editor, tampilkan dialog **Simpan / Buang / Batal**.
  - Alur simpan: validasi akses tulis → siapkan konten → gunakan file sementara/transaksi jika penyimpanan mendukung → tulis & verifikasi → perbarui status. Peringatkan pengguna sebelum menimpa jika file berubah di luar aplikasi. Jika simpan gagal, pertahankan editor tetap terbuka beserta perubahan lokal.
- **Penanganan File Sangat Besar (P1/Kebijakan Khusus):** Deteksi ukuran sebelum membuka (batas dapat dikonfigurasi berdasarkan benchmark), tawarkan pratinjau sebagian/bertahap, konfirmasi pengguna, serta nonaktifkan fitur mahal (mis. *syntax highlighting* penuh) dengan penjelasan jelas.

### 4.4 Perbandingan File (Diff)
- **Mode Tampilan:**
  - **Berdampingan (P0):** File A di kiri, File B di kanan, nomor baris terpisah, baris kosong penyeimbang untuk menjaga kesejajaran, serta *scroll* tersinkron secara semantik. Tata letak adaptif/geser pada layar sempit.
  - **Unified (P0):** Satu aliran teks dengan penanda visual baris tambah (`+`)/hapus (`-`) dan kolom nomor baris A & B yang terpisah.
  - **Inline (P1)** & **Fokus Perubahan (P1):** Perbandingan detail tingkat karakter dalam satu baris, serta fitur menyembunyikan/melipat rentang baris yang identik.
  - *Aksesibilitas:* Perbedaan wajib memakai simbol/label selain warna.
- **Kontrol & Navigasi:** Lompat ke perubahan berikutnya/sebelumnya & baris beda pertama, peka huruf, abaikan spasi awal/akhir atau seluruh *whitespace* (sertakan pemberitahuan bila diabaikan), atur jumlah baris konteks, tampil/sembunyi baris sama, ekspor hasil sebagai teks/patch.
- **Algoritme & Penerimaan:** Gunakan diff berbasis baris (seperti Myers diff) ditambah diff karakter hanya pada baris yang berubah. Berjalan asinkron, dapat dibatalkan, tidak membaca ulang disk saat *scroll*, menghitung ulang otomatis jika file berubah, mendukung file kosong/identik, dan menolak file biner.

### 4.5 Penggantian Nama Massal
- **Token Pola:**
  - `{P}`: Nama dasar tanpa ekstensi terakhir (`laporan.txt` → `laporan`).
  - `{S}`: Ekstensi beserta titik (`laporan.txt` → `.txt`, atau kosong jika tanpa ekstensi).
  - `{N}`: Nomor berurutan (angka awal dapat dipilih, mis. `0`, `1`, `100`). Contoh (`awal=1`): `file_{N}{S}` → `file_1.txt`.
  - `{zN}` / `{zN:W}`: Nomor berurutan dengan *zero-padding* (default 2 digit; `:W` untuk minimal `W` digit, angka tidak dipotong jika digitnya melampaui `W`). Contoh (`awal=8`): `gambar_{zN}.jpg` → `gambar_08.jpg`, `gambar_{zN:3}.jpg` → `gambar_008.jpg` *(dapat mendukung penulisan angka awal inline seperti `{z8}`/`{z8:3}` bila diaktifkan)*.
- **Keamanan Eksekusi:** Pilih urutan item → tampilkan pratinjau nama lama & baru → validasi karakter nama → deteksi tabrakan nama & item yang tidak dapat diubah → konfirmasi ringkasan sebelum eksekusi. Dilarang menimpa file lain; gunakan nama sementara untuk pertukaran nama bila penyimpanan mengizinkan; catat kegagalan per item secara terpisah.

### 4.6 Bookmark & Homepage
- **Bookmark:** Simpan file, folder, lokasi arsip, atau folder kerja secara lokal. Jika izin akses eksternal dicabut atau lokasi hilang, tampilkan status tidak tersedia beserta opsi memilih ulang lokasi.
- **Homepage:** Pengguna dapat menetapkan folder awal saat tab **File** dibuka dan meresetnya ke bawaan. Homepage tidak memberikan akses otomatis ke lokasi tanpa izin.

### 4.7 Pengelolaan Arsip
- **Prioritas & Matriks Dukungan:** Setiap format wajib memiliki matriks kapabilitas eksplisit (*baca, ekstrak, buat, ubah isi, enkripsi*):
  - **Tahap Pertama (P0):** ZIP, TAR, GZIP *(kompresi berkas tunggal)*, TGZ, JAR, dan format berbasis ZIP aman lainnya.
  - **Tahap Lanjutan (P1/P2):** 7z, BZIP2, XZ, Zstandard; dukungan RAR bergantung pada pustaka dan lisensi.
- **Kemampuan & Arsip Bertingkat:** Lihat daftar isi, jelajah folder & arsip bertingkat (mis. `proyek.zip/sumber.tar/konfig.zip/app.json`) secara bertahap tanpa mengekstrak permanen seluruh arsip, dengan batas kedalaman aman yang dapat dikonfigurasi. Mendukung ekstraksi selektif (1/beberapa/semua), kompresi, cek ukuran, dan kata sandi (minta saat perlu, jangan simpan tanpa izin).
- **Edit File dalam Arsip (P1 — khusus format yang mendukung penulisan ulang):** Gunakan salinan kerja sementara → edit → tulis ulang arsip secara aman & verifikasi → ganti arsip lama (pertahankan cadangan/pemulihan jika gagal).
- **Keamanan Arsip:** Wajib cegah *Zip Slip* (*path traversal*), validasi nama/jalur internal, cek konflik nama, batasi ukuran hasil ekstraksi, pisahkan baca metadata dari proses ekstraksi, tangani arsip rusak tanpa *crash*, dan larang ekstraksi otomatis tanpa persetujuan pengguna.

---

## 5. Akses Penyimpanan Platform & Keamanan Data

### 5.1 Batasan OS & Akses Penyimpanan
- **Android:** Gunakan *Storage Access Framework* (SAF) untuk direktori/file eksternal pilihan pengguna. Bedakan dengan jelas penyimpanan privat aplikasi, penyimpanan bersama, direktori SAF, penyedia dokumen, serta lokasi terbatas (`Android/data`, `Android/obb`, sandbox aplikasi lain). Jangan memakai izin umum untuk mengakali sandbox aplikasi lain.
  - **Integrasi Termux (`/data/data/com.termux/files/home`):** Akses langsung tanpa root **tidak dapat dijamin**. Masuk sebagai integrasi **opsional (P1, nonaktif default)** via mekanisme resmi `RUN_COMMAND` (memerlukan izin dan `allow-external-apps` di konfigurasi Termux). Aplikasi wajib membatasi perintah yang diizinkan, mencegah injeksi shell, menjelaskan risiko, dan menangani kondisi Termux belum terpasang/dikonfigurasi.
- **iOS:** Terapkan aturan sandbox via *Document Picker*, simpan *security-scoped bookmark* bila platform mendukung, gunakan koordinasi file untuk baca/tulis, tangani pencabutan izin, dan lepaskan *security scope* setelah selesai.

### 5.2 Keamanan File, Temp File & Privasi
- **Pengamanan Operasi:** Konfirmasi hapus, validasi path/nama, tangani *permission denied* dengan jelas, dilarang mengeksekusi file kode atau menjalankan shell dari nama/isi file, dan hindari kebocoran path privat atau isi dokumen pada log rilis.
- **File Sementara:** Simpan hanya di direktori temp/cache yang sesuai, bersihkan berkala, jangan hapus file yang sedang dipakai tab/pekerjaan aktif, dan jangan jadikan temp sebagai satu-satunya penyimpan editan tanpa pemulihan.
- **Privasi:** Tanpa akun, tanpa telemetri konten/path, serta sediakan tombol hapus riwayat pencarian.

---

## 6. Arsitektur KMP & Standar Kode

### 6.1 Pemisahan Layer & Arah Dependensi (Clean Architecture)
```
  [ Presentation ] ──────┐
  (Compose UI, State,    │
   ViewModel, Mapper UI) │
                         ▼
                    [ Domain ]  ◄── (Pure Kotlin: Model, UseCase, Repository Interface)
                         ▲
                         │
  [ Data ] ──────────────┘
  (Implementasi Repo, SQLDelight,
   Okio, Adapter Arsip & Platform, Mapper Data)

  * Platform App (appAndroid / appIos) & Koin DI menyatukan implementasi di batas aplikasi.
```

- **Aturan Ketat:**
  - `Domain` wajib Pure Kotlin (dilarang mengimpor API Android, UIKit/iOS, Compose, `Context`, `Uri`, `NSURL`, `UIViewController`, atau pustaka database).
  - `Presentation` hanya berkomunikasi dengan `Domain` (dilarang memanggil implementasi `Data` langsung) dan menerima *UI State* yang *immutable* (pola UDF).
  - Pisahkan 3 jenis model (*Data*, *Domain*, *UI*) beserta *mapper* pada layer masing-masing.
  - Minimalkan `expect/actual`; utamakan kontrak *interface* di `commonMain` (contoh: `RepositoriBerkas`) dan *streaming I/O* (hindari asumsi semua file muat di `ByteArray`/`String`).

### 6.2 Struktur Modul Logis & Stack Teknologi
- **Struktur Modul:** `appAndroid/`, `appIos/`, dan `shared/` (`domain/`, `data/`, `presentation/` dengan source set `commonMain`, `androidMain`, `iosMain`). Modul boleh digabung jika pemisahan tidak memberi nilai tambah.
- **Stack:** Kotlin, KMP, Compose Multiplatform, Koin Multiplatform (DI), SQLDelight (database lokal; dilarang memakai Room di kode bersama), Kotlin Coroutines & Flow, `kotlinx.serialization`, Okio (abstraksi I/O), algoritme Myers Diff, dan abstraksi *Archive Provider*.

### 6.3 Aturan Penamaan Simbol
- Simbol internal proyek memakai Bahasa Indonesia: *camelCase* untuk fungsi/variabel (`cariBerkas()`, `lokasiBerkas`, `sedangMemuat`) dan *PascalCase* untuk kelas/Composable (`RepositoriBerkas`, `CariBerkasUseCase`, `DaftarBerkasLayar()`).
- Nama *override*, signature framework, atau kontrak pustaka eksternal tetap mengikuti nama aslinya.

---

## 7. Kinerja, Memori & UX

### 7.1 Target Performa Awal (P95 pada Perangkat Referensi)
| Area Pengukuran | Target Awal |
| :--- | :--- |
| Pembukaan awal aplikasi (*cold start*) | Maks. **1,5 detik** |
| Tampilan awal direktori lokal berisi 1.000 item | Maks. **500 ms** |
| Pembukaan file teks 1 MiB | Maks. **500 ms** |
| Diff dua file teks masing-masing 1 MiB | Maks. **2 detik** |
| Input editor & pengguliran daftar | Nol I/O sinkron di thread UI; tetap responsif saat pemuatan |

### 7.2 Optimasi & UX
- **Optimasi:** Wajib *lazy list*, I/O asinkron, pembatalan coroutine, *cache* terbatas dengan kebijakan penggusuran, dilarang membaca penuh berulang atau menjalankan diff/statistik/I/O langsung di dalam badan Composable (kendalikan *recomposition* dengan state sesempit mungkin).
- **Navigasi Utama:** **File**, **Terbaru**, **Bookmark**, **Pencarian**, dan **Pengaturan** *(tab editor dan layar diff dibuka sebagai ruang kerja terpisah)*.
- **Aksesibilitas:** Target sentuh memadai, kontras teks baik, dukungan skala huruf, label aksesibilitas pada tombol ikon, penanda non-warna, serta tata letak adaptif saat layar kecil atau papan ketik muncul.

---

## 8. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
| :--- | :--- | :--- |
| Perbedaan akses file Android/iOS & penyedia dokumen | Fitur tidak seragam / operasi gagal | Gunakan *adapter*, matriks kapabilitas eksplisit, dan pesan jujur |
| File besar menguras memori | *Crash* (OOM) / UI beku | Gunakan *streaming*, batas ukuran, dan mode file besar |
| Algoritme diff lambat pada file besar | UI tidak responsif | Proses asinkron, pembatalan, benchmark, & *fallback* |
| Pengubahan file dalam arsip gagal di tengah | Arsip asli rusak | Salinan kerja sementara, verifikasi, & penulisan ulang aman |
| Izin direktori iOS/SAF dicabut | Bookmark/homepage gagal dibuka | Deteksi kegagalan & sediakan alur pilih ulang lokasi |
| Penamaan Indonesia bentrok dengan API eksternal | Integrasi/kompilasi gagal | Pertahankan kontrak eksternal asli; Bahasa Indonesia khusus simbol internal |
| Akses Termux tidak tersedia/dikonfigurasi | Fitur lanjutan gagal | Jadikan opsional (P1), nonaktif default, jelaskan batasan |
| Pembaruan dependensi merusak *build* | Gagal kompilasi/runtime | Kunci matriks versi stabil & jalankan uji regresi |

---

## 9. Kriteria Penerimaan Rilis & Strategi Pengujian

### 9.1 Kriteria Penerimaan Produk (P0)
- **Fungsional:** Seluruh fitur P0 berjalan sesuai spesifikasi (navigasi & operasi penyimpanan, dialog perubahan editor, keamanan simpan, diff akurat pada mode berdampingan & *unified*, pencarian rekursif + skip biner, pratinjau & deteksi konflik *bulk rename*, arsip tanpa *Zip Slip* dan tanpa *crash* pada arsip rusak).
- **Arsitektur & Kepatuhan:** Tidak ada pelanggaran arah dependensi Clean Architecture; simbol internal konsisten berbahasa Indonesia; batasan platform & kegagalan operasi disampaikan secara jujur.
- **Kualitas & Kinerja:** Tidak ada I/O berat di thread UI; lulus target benchmark P95 pada perangkat fisik di seluruh generasi OS target; nol *crash* atau kehilangan data kritis pada skenario uji.

### 9.2 Strategi Pengujian
- **Unit Test (Pure Kotlin):** Validasi nama, token & konflik *bulk rename*, statistik teks, algoritme diff, filter pencarian, dan kebijakan kedalaman arsip (tanpa runtime Android/iOS).
- **Integration Test:** Adapter penyimpanan, persistensi & pencabutan izin bookmark, kompresi/ekstraksi & penulisan ulang arsip, skenario gagal simpan editor, dan pencarian rekursif.
- **UI Test:** Navigasi, multi-select, dialog simpan/batal, cari & ganti, lompat baris, peralihan mode diff, penyesuaian papan ketik, dan tata letak adaptif.
- **Uji Ketahanan (*Stress & Edge Cases*):** Direktori ribuan file, folder bertingkat dalam, file kosong & file teks/baris sangat panjang, encoding UTF-8/UTF-16, LF/CRLF, nama Unicode/karakter khusus, arsip rusak/berkata sandi, penyimpanan lambat/penuh, dan pembatalan operasi di tengah proses.