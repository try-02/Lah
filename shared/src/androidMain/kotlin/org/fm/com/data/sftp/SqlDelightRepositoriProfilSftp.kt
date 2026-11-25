package org.fm.com.data.sftp

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.PreferensiEntity
import org.fm.com.domain.sftp.JenisAutentikasi
import org.fm.com.domain.sftp.PengenalSftp
import org.fm.com.domain.sftp.PenyimpananRahasiaSftp
import org.fm.com.domain.sftp.ProfilSftp
import org.fm.com.domain.sftp.RahasiaSftp
import org.fm.com.domain.sftp.RepositoriProfilSftp

private const val KUNCI_DAFTAR_ID = "sftp.daftar_id"
private const val PREFIX_PROFIL = "sftp.profil."
private const val PREFIX_RAHASIA = "sftp.rahasia."

@OptIn(ExperimentalTime::class)
class SqlDelightRepositoriProfilSftp(
    private val database: DatabaseAplikasi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.System
) : RepositoriProfilSftp {

    override fun amatiDaftar(): Flow<List<ProfilSftp>> =
        database.preferensiEntityQueries
            .semuaPreferensi()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows ->
                val peta = rows.associateBy({ it.kunci }, { it.nilai })
                uraiDaftar(peta)
            }

    override suspend fun daftar(): List<ProfilSftp> = withContext(ioDispatcher) {
        val rows = database.preferensiEntityQueries.semuaPreferensi().executeAsList()
        val peta = rows.associateBy({ it.kunci }, { it.nilai })
        uraiDaftar(peta)
    }

    override suspend fun ambil(id: String): ProfilSftp? = withContext(ioDispatcher) {
        if (!PengenalSftp.apakahIdValid(id)) return@withContext null
        val mentah = database.preferensiEntityQueries.ambilNilai("$PREFIX_PROFIL$id").executeAsOneOrNull()
            ?: return@withContext null
        uraiProfil(id, mentah)
    }

    override suspend fun simpan(profil: ProfilSftp) {
        withContext(ioDispatcher) {
            require(PengenalSftp.apakahIdValid(profil.id)) { "idProfil tidak valid" }
            val sekarang = clock.now().toEpochMilliseconds()
            database.transaction {
                val idsSaatIni = database.preferensiEntityQueries.ambilNilai(KUNCI_DAFTAR_ID)
                    .executeAsOneOrNull()
                    ?.split('\n')
                    ?.filter { it.isNotBlank() }
                    ?.toMutableList() ?: mutableListOf()
                if (profil.id !in idsSaatIni) {
                    idsSaatIni.add(profil.id)
                    database.preferensiEntityQueries.simpanNilai(KUNCI_DAFTAR_ID, idsSaatIni.joinToString("\n"), sekarang)
                }
                database.preferensiEntityQueries.simpanNilai("$PREFIX_PROFIL${profil.id}", kodekanProfil(profil), sekarang)
            }
        }
    }

    override suspend fun hapus(id: String) {
        withContext(ioDispatcher) {
            val sekarang = clock.now().toEpochMilliseconds()
            database.transaction {
                val idsSaatIni = database.preferensiEntityQueries.ambilNilai(KUNCI_DAFTAR_ID)
                    .executeAsOneOrNull()
                    ?.split('\n')
                    ?.filter { it.isNotBlank() && it != id }
                    .orEmpty()
                if (idsSaatIni.isEmpty()) {
                    database.preferensiEntityQueries.hapusKunci(KUNCI_DAFTAR_ID)
                } else {
                    database.preferensiEntityQueries.simpanNilai(KUNCI_DAFTAR_ID, idsSaatIni.joinToString("\n"), sekarang)
                }
                database.preferensiEntityQueries.hapusKunci("$PREFIX_PROFIL$id")
                database.preferensiEntityQueries.hapusKunci("$PREFIX_RAHASIA$id")
            }
        }
    }

    private fun uraiDaftar(peta: Map<String, String>): List<ProfilSftp> {
        val ids = peta[KUNCI_DAFTAR_ID]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        return ids.mapNotNull { id ->
            val mentah = peta["$PREFIX_PROFIL$id"] ?: return@mapNotNull null
            uraiProfil(id, mentah)
        }
    }

    private fun kodekanProfil(p: ProfilSftp): String = buildString {
        append("nama=").append(escape(p.nama)).append('\n')
        append("host=").append(escape(p.host)).append('\n')
        append("port=").append(p.port).append('\n')
        append("user=").append(escape(p.namaPengguna)).append('\n')
        append("path=").append(escape(p.pathAwal)).append('\n')
        append("auth=").append(p.autentikasi.name).append('\n')
        p.jenisKunciHost?.let { append("hkType=").append(escape(it)).append('\n') }
        p.sidikJariHost?.let { append("hkFp=").append(escape(it)).append('\n') }
    }

    private fun uraiProfil(id: String, mentah: String): ProfilSftp? {
        val peta = HashMap<String, String>()
        for (baris in mentah.split('\n')) {
            val eq = baris.indexOf('=')
            if (eq > 0) {
                peta[baris.substring(0, eq)] = unescape(baris.substring(eq + 1))
            }
        }
        val nama = peta["nama"]?.takeIf { it.isNotBlank() } ?: return null
        val host = peta["host"]?.takeIf { it.isNotBlank() } ?: return null
        val port = peta["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 8022
        val user = peta["user"]?.takeIf { it.isNotBlank() } ?: return null
        val path = peta["path"]?.takeIf { it.isNotBlank() } ?: "/"
        val auth = peta["auth"]?.let { runCatching { JenisAutentikasi.valueOf(it) }.getOrNull() }
            ?: JenisAutentikasi.SANDI
        return ProfilSftp(
            id = id,
            nama = nama,
            host = host,
            port = port,
            namaPengguna = user,
            pathAwal = PengenalSftp.normalisasi(path),
            autentikasi = auth,
            jenisKunciHost = peta["hkType"]?.takeIf { it.isNotBlank() },
            sidikJariHost = peta["hkFp"]?.takeIf { it.isNotBlank() }
        )
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n")

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> sb.append('\n')
                    '\\' -> sb.append('\\')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }
}

@OptIn(ExperimentalTime::class)
class SqlDelightPenyimpananRahasiaSftp(
    private val database: DatabaseAplikasi,
    private val pengelola: PengelolaRahasiaSftp = PengelolaRahasiaSftp(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.System
) : PenyimpananRahasiaSftp {

    override suspend fun simpan(idProfil: String, rahasia: RahasiaSftp) {
        withContext(ioDispatcher) {
            val tersegel = pengelola.segel(rahasia, idProfil)
            database.preferensiEntityQueries.simpanNilai(
                kunci = "$PREFIX_RAHASIA$idProfil",
                nilai = tersegel,
                waktu_diubah = clock.now().toEpochMilliseconds()
            )
        }
    }

    override suspend fun ambil(idProfil: String): RahasiaSftp? = withContext(ioDispatcher) {
        val mentah = database.preferensiEntityQueries.ambilNilai("$PREFIX_RAHASIA$idProfil").executeAsOneOrNull()
            ?: return@withContext null
        pengelola.buka(mentah, idProfil)
    }

    override suspend fun hapus(idProfil: String) {
        withContext(ioDispatcher) {
            database.preferensiEntityQueries.hapusKunci("$PREFIX_RAHASIA$idProfil")
        }
    }

    override suspend fun ada(idProfil: String): Boolean = withContext(ioDispatcher) {
        database.preferensiEntityQueries.ambilNilai("$PREFIX_RAHASIA$idProfil").executeAsOneOrNull() != null
    }
}