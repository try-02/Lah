shared/src/androidMain/kotlin/org/fm/com/data/saf/SafOperasiBerkas.kt (Batas Aman, Anti-Siklus Symlink, & Percepatan Stream Baru)

Perbarui bagian konstanta teratas serta salinFolderRekursifSaf, salinDanVerifikasiStreamSaf, bukaOutputStreamTarget, hapusFolderRekursifBottomUp, dan companion object pada SafOperasiBerkas.kt:
1. Konstanta di bagian atas file:
```
private const val UKURAN_BUFFER_SALIN_SAF = 16 * 1024
private const val BATAS_BFS_FOLDER_SAF = 250
private const val BATAS_KEDALAMAN_REKURSIF_SAF = 32
private const val BATAS_ITEM_FALLBACK_SAF = 500
```
1. Ganti fungsi salinFolderRekursifSaf, salinDanVerifikasiStreamSaf, bukaOutputStreamTarget, dan hapusFolderRekursifBottomUp:
```
private suspend fun salinFolderRekursifSaf(
        sumberFolderUri: Uri,
        parentTujuanUri: Uri,
        namaFolderBaru: String
    ): HasilOperasi<Uri> {
        val rootTujuanUri = try {
            DocumentsContract.createDocument(
                contentResolver,
                parentTujuanUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                namaFolderBaru
            ) ?: return HasilOperasi.Gagal("Gagal membuat folder '$namaFolderBaru'", PenyebabGalat.TIDAK_DIKETAHUI)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return HasilOperasi.Gagal(e.message ?: "Izin buat folder ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal membuat folder '$namaFolderBaru'", PenyebabGalat.TIDAK_DIKETAHUI)
        }

        val dikunjungi = HashSet<String>()
        val antrean = ArrayDeque<Triple<Uri, Uri, Int>>()
        runCatching { DocumentsContract.getDocumentId(sumberFolderUri) }.getOrNull()?.let { dikunjungi.add(it) }
        antrean.addLast(Triple(sumberFolderUri, rootTujuanUri, 0))
        var totalItem = 0

        try {
            while (antrean.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (sumberDir, tujuanDir, kedalaman) = antrean.removeFirst()
                if (kedalaman >= BATAS_KEDALAMAN_REKURSIF_SAF) {
                    withContext(NonCancellable) { hapusFolderRekursifBottomUp(rootTujuanUri) }
                    return HasilOperasi.Gagal(
                        "Kedalaman folder melebihi batas aman ($BATAS_KEDALAMAN_REKURSIF_SAF tingkat)",
                        PenyebabGalat.VALIDASI
                    )
                }

                val anakList = when (val res = daftarAnakSingkat(sumberDir)) {
                    is HasilOperasi.Sukses -> res.nilai
                    is HasilOperasi.Gagal -> {
                        withContext(NonCancellable) { hapusFolderRekursifBottomUp(rootTujuanUri) }
                        return res
                    }
                }

                for (anak in anakList) {
                    currentCoroutineContext().ensureActive()
                    if (!dikunjungi.add(anak.docId)) continue
                    if (apakahJalurBerulang(anak.docId)) {
                        withContext(NonCancellable) { hapusFolderRekursifBottomUp(rootTujuanUri) }
                        return HasilOperasi.Gagal(
                            "Terdeteksi potensi siklus tautan simbolik pada '${anak.nama}'",
                            PenyebabGalat.VALIDASI
                        )
                    }
                    totalItem++
                    if (totalItem > BATAS_ITEM_FALLBACK_SAF) {
                        withContext(NonCancellable) { hapusFolderRekursifBottomUp(rootTujuanUri) }
                        return HasilOperasi.Gagal(
                            "Folder berisi lebih dari $BATAS_ITEM_FALLBACK_SAF item; ganti nama fallback SAF dibatalkan agar tidak membebani sistem",
                            PenyebabGalat.TIDAK_DIDUKUNG
                        )
                    }

                    if (anak.adalahFolder) {
                        val subTujuan = DocumentsContract.createDocument(
                            contentResolver,
                            tujuanDir,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            anak.nama
                        ) ?: run {
                            withContext(NonCancellable) { hapusFolderRekursifBottomUp(rootTujuanUri) }
                            return HasilOperasi.Gagal("Gagal membuat subfolder '${anak.nama}'", PenyebabGalat.TIDAK_DIKETAHUI)
                        }
                        antrean.addLast(Triple(anak.uri, subTujuan, kedalaman + 1))
                    } else {
                        val mimeAnak = tentukanMimeType(anak.nama)
                        val berkasTujuan = DocumentsContract.createDocument(
                            contentResolver,
                            tujuanDir,
                            mimeAnak,
                            anak.nama
                        ) ?: run {
                            withContext(NonCancellable) { hapusFolderRekursifBottomUp(rootTujuanUri) }
                            return HasilOperasi.Gagal("Gagal membuat berkas '${anak.nama}'", PenyebabGalat.TIDAK_DIKETAHUI)
                        }
                        val salin = salinDanVerifikasiStreamSaf(anak.uri, berkasTujuan)
                        if (salin is HasilOperasi.Gagal) {
                            withContext(NonCancellable) { hapusFolderRekursifBottomUp(rootTujuanUri) }
                            return salin
                        }
                    }
                }
            }
            return HasilOperasi.Sukses(rootTujuanUri)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { hapusFolderRekursifBottomUp(rootTujuanUri) }
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) { hapusFolderRekursifBottomUp(rootTujuanUri) }
            return HasilOperasi.Gagal(e.message ?: "Gagal menyalin isi folder SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private suspend fun salinDanVerifikasiStreamSaf(
        sumberUri: Uri,
        targetUri: Uri
    ): HasilOperasi<Unit> {
        val crcSumber = CRC32()
        var totalByte = 0L
        val buffer = ByteArray(UKURAN_BUFFER_SALIN_SAF)
        try {
            val inStream = contentResolver.openInputStream(sumberUri)
                ?: return HasilOperasi.Gagal("Gagal membuka aliran baca sumber SAF", PenyebabGalat.IZIN_DITOLAK)
            val outStream = bukaOutputStreamTarget(targetUri, preferModeLangsung = true)
                ?: run {
                    runCatching { inStream.close() }
                    return HasilOperasi.Gagal("Gagal membuka aliran tulis target SAF", PenyebabGalat.IZIN_DITOLAK)
                }

            inStream.use { src ->
                outStream.use { dst ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = src.read(buffer)
                        if (n == -1) break
                        if (n > 0) {
                            dst.write(buffer, 0, n)
                            crcSumber.update(buffer, 0, n)
                            totalByte += n
                        }
                    }
                    dst.flush()
                }
            }

            if (totalByte == 0L) {
                return HasilOperasi.Sukses(Unit)
            }

            val verifikasiIn = try {
                contentResolver.openInputStream(targetUri)
            } catch (_: Exception) {
                null
            }
            if (verifikasiIn != null) {
                val crcAktual = CRC32()
                var byteAktual = 0L
                verifikasiIn.use { ver ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = ver.read(buffer)
                        if (n == -1) break
                        if (n > 0) {
                            crcAktual.update(buffer, 0, n)
                            byteAktual += n
                        }
                    }
                }
                if (byteAktual != totalByte || crcAktual.value != crcSumber.value) {
                    return HasilOperasi.Gagal(
                        "Verifikasi salinan gagal: diharapkan $totalByte byte, terbaca $byteAktual byte",
                        PenyebabGalat.TIDAK_DIKETAHUI
                    )
                }
            }
            return HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return HasilOperasi.Gagal(e.message ?: "Izin salin dokumen SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: FileNotFoundException) {
            return HasilOperasi.Gagal(e.message ?: "Berkas SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal menyalin dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }
```
Dan perbarui bukaOutputStreamTarget serta hapusFolderRekursifBottomUp:
```
private fun bukaOutputStreamTarget(
        targetUri: Uri,
        preferModeLangsung: Boolean = false
    ): OutputStream? {
        val daftarMode = if (preferModeLangsung) {
            arrayOf("w", "wt", "rwt")
        } else {
            arrayOf("wt", "rwt", "w")
        }
        var galatTerakhir: Exception? = null
        for (mode in daftarMode) {
            try {
                return contentResolver.openOutputStream(targetUri, mode)
            } catch (e: UnsupportedOperationException) {
                galatTerakhir = e
            } catch (e: IllegalArgumentException) {
                galatTerakhir = e
            }
        }
        if (galatTerakhir != null) throw galatTerakhir
        return null
    }
```

```
private suspend fun hapusFolderRekursifBottomUp(rootFolderUri: Uri): HasilOperasi<Unit> {
        val daftarFolderUrut = mutableListOf<Uri>()
        val daftarBerkas = mutableListOf<Uri>()
        val antrean = ArrayDeque<Pair<Uri, Int>>()
        val dikunjungi = HashSet<String>()

        runCatching { DocumentsContract.getDocumentId(rootFolderUri) }.getOrNull()?.let { dikunjungi.add(it) }
        antrean.addLast(rootFolderUri to 0)
        var totalItem = 0

        while (antrean.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (dirUri, kedalaman) = antrean.removeFirst()
            if (kedalaman > BATAS_KEDALAMAN_REKURSIF_SAF) {
                return HasilOperasi.Gagal(
                    "Kedalaman folder melebihi batas aman hapus SAF ($BATAS_KEDALAMAN_REKURSIF_SAF tingkat)",
                    PenyebabGalat.VALIDASI
                )
            }
            daftarFolderUrut.add(dirUri)

            val anakList = when (val res = daftarAnakSingkat(dirUri)) {
                is HasilOperasi.Sukses -> res.nilai
                is HasilOperasi.Gagal -> return res
            }
            for (anak in anakList) {
                if (!dikunjungi.add(anak.docId)) continue
                if (apakahJalurBerulang(anak.docId)) {
                    return HasilOperasi.Gagal(
                        "Terdeteksi potensi siklus tautan simbolik saat menghapus '${anak.nama}'",
                        PenyebabGalat.VALIDASI
                    )
                }
                totalItem++
                if (totalItem > BATAS_ITEM_FALLBACK_SAF * 2) {
                    return HasilOperasi.Gagal(
                        "Folder terlalu besar untuk dihapus rekursif via SAF",
                        PenyebabGalat.TIDAK_DIDUKUNG
                    )
                }
                if (anak.adalahFolder) {
                    antrean.addLast(anak.uri to (kedalaman + 1))
                } else {
                    daftarBerkas.add(anak.uri)
                }
            }
        }

        try {
            for (berkasUri in daftarBerkas) {
                currentCoroutineContext().ensureActive()
                if (!DocumentsContract.deleteDocument(contentResolver, berkasUri)) {
                    return HasilOperasi.Gagal("Gagal menghapus salah satu berkas di dalam folder", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            for (folderUri in daftarFolderUrut.asReversed()) {
                currentCoroutineContext().ensureActive()
                if (!DocumentsContract.deleteDocument(contentResolver, folderUri)) {
                    return HasilOperasi.Gagal("Gagal menghapus folder SAF setelah dikosongkan", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            return HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return HasilOperasi.Gagal(e.message ?: "Izin hapus isi folder SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal menghapus isi folder SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }
```
Dan tambahkan di dalam internal companion object pada SafOperasiBerkas.kt:
```
internal fun apakahJalurBerulang(docId: String): Boolean {
            val segmen = docId.split('/').filter { it.isNotEmpty() }
            if (segmen.size < 4) return false
            for (i in 0 until segmen.size - 2) {
                if (segmen[i] == segmen[i + 1] && segmen[i + 1] == segmen[i + 2]) {
                    return true
                }
            }
            if (segmen.size >= 6) {
                val n = segmen.size
                if (segmen[n - 1] == segmen[n - 3] &&
                    segmen[n - 2] == segmen[n - 4] &&
                    segmen[n - 3] == segmen[n - 5] &&
                    segmen[n - 4] == segmen[n - 6]
                ) {
                    return true
                }
            }
            return false
        }
```
