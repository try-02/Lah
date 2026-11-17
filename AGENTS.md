# AGENTS.md — File Manager & Code Editor (KMP)

Aplikasi pengelola file + editor kode untuk Android & iOS. Kotlin Multiplatform, Compose Multiplatform, Clean Architecture.
Spesifikasi: `docs/PRD.md` · Rencana & status: `PLAN.md` · Versi pustaka: `gradle/libs.versions.toml`.

## Batas peran (wajib, berlaku juga untuk subagent)
- Kamu hanya membaca, menulis, dan mengubah file proyek. Pemilik proyek yang menjalankan git, build, dan CI.
- DILARANG lewat tool apa pun (terminal, execute_code, delegate_task): `git`, `gh`, `gradle`/`gradlew`, `xcodebuild`, `kotlinc`, `npm`/`pnpm`/`pip`/`pkg`/`apt`, dan perintah install/unduh/build/compile/test lainnya. Sertakan larangan ini saat mendelegasikan.
- Terminal hanya untuk membaca/mencari: `ls`, `find`, `grep`, `sed -n`, `head`, `tail`, `wc`. File sementara di `~/tmp/`, bukan `/tmp`.
- Jangan mengubah versi pustaka/plugin, Gradle wrapper, atau file CI (`.github/`). Butuh pustaka baru → sebut nama + alasan, tunggu persetujuan pemilik (versi harus lolos uji kompatibilitas, PRD §2). `build.gradle.kts` hanya diubah untuk dependensi/source set yang sudah disetujui.
- Tidak ada compiler di sini, jadi tulis kode yang benar sejak awal: cek import, nama simbol, tipe, dan signature lewat `grep`/baca kode yang ada. Ragu soal API pustaka → context7, jangan menebak.
- Error build datang dari pemilik sebagai potongan log → skill `kmp-debug`. Jangan minta log penuh; minta bagian tertentu saja bila perlu.

## Hemat token
- Jangan baca PRD penuh: `grep -n '^#' docs/PRD.md`, lalu `sed -n 'A,Bp'` pada bagian yang perlu. Peta: §4.1 File manager · §4.2 Pencarian · §4.3 Editor · §4.4 Diff · §4.5 Rename massal · §4.6 Bookmark · §4.7 Arsip · §5 Penyimpanan & keamanan · §6 Arsitektur · §7 Kinerja · §9 Pengujian.
- Satu sesi = satu task dari `PLAN.md`. Selesai → `kmp-serahterima`.
- Cari dulu (`grep`), baca hanya baris yang relevan. Ubah file dengan edit/patch pada bagian yang berubah, bukan tulis ulang seluruh file.
- Jangan menampilkan ulang kode yang baru ditulis. Laporan akhir: daftar file + 1–3 kalimat. Balas singkat, bahasa Indonesia.

## Arsitektur (PRD §6)
- Struktur: `appAndroid/`, `appIos/`, `shared/` (`domain/`, `data/`, `presentation/`; source set `commonMain`, `androidMain`, `iosMain`). Ikuti struktur yang sudah ada di repo.
- `domain`: Kotlin murni — tanpa Android/iOS/Compose/`Context`/`Uri`/`NSURL`/database. Isi: model, UseCase, interface repositori (mis. `RepositoriBerkas`).
- `presentation`: hanya bergantung ke `domain`. UDF: ViewModel memegang state immutable; UI mengirim event. Tanpa I/O, diff, atau statistik di dalam Composable.
- `data`: implementasi repositori, SQLDelight, Okio, adapter arsip & platform. Koin menyatukan semuanya di batas aplikasi.
- Tiga model terpisah (Data, Domain, UI) + mapper di layer masing-masing.
- Utamakan interface di `commonMain`; `expect/actual` seminimal mungkin. Pakai streaming (Okio, Flow); jangan asumsikan file muat di `ByteArray`/`String`.
- Stack tetap: Kotlin, Compose Multiplatform, Koin, SQLDelight (DILARANG Room di kode bersama), Coroutines + Flow, kotlinx.serialization, Okio, Myers diff.
- Target: Android `minSdk 30`, `compileSdk 37`; iOS deployment 14.0.

## Konvensi
- Simbol internal berbahasa Indonesia: fungsi/variabel camelCase (`cariBerkas`, `lokasiBerkas`, `sedangMemuat`); kelas/Composable PascalCase (`CariBerkasUseCase`, `DaftarBerkasLayar`). Nama override, API framework, dan pustaka tetap aslinya.
- Kerja berat berjalan asinkron di luar thread UI, bisa dibatalkan, menampilkan progres.
- Hapus, timpa, rename massal, ekstraksi arsip: wajib konfirmasi. Kegagalan dilaporkan jujur per item; jangan pernah melaporkan sukses bila gagal.
- Jangan membuat path POSIX palsu untuk SAF/URI/sandbox iOS. Setiap repositori punya matriks kapabilitas; opsi tak didukung dinonaktifkan dengan alasan.
- Jangan eksekusi file kode atau shell dari nama/isi file. Jangan log path/isi file. Tanpa telemetri, tanpa akun.
- Logika murni (diff, token rename, statistik, filter cari, kedalaman arsip) diuji dengan unit test di `commonTest`; pemilik yang menjalankannya.

## Alat
- context7 (MCP): hanya saat ragu API/versi pustaka (Compose Multiplatform, Koin, SQLDelight, Okio, kotlinx.*). Satu pustaka per panggilan, pertanyaan spesifik.
- delegate_task: untuk riset API, merangkum log panjang, atau review perubahan besar — minta hasil berupa ringkasan. Jangan delegasikan penulisan kode paralel pada modul yang sama.

## Skill
`kmp-plan` (rencana/brainstorm) · `kmp-implement` (satu task) · `kmp-review` (tinjau) · `kmp-debug` (potongan error build) · `kmp-serahterima` (akhir sesi).
