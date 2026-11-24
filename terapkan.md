--- a/shared/src/androidHostTest/kotlin/org/fm/com/data/saf/SafAdapterTest.kt
+++ b/shared/src/androidHostTest/kotlin/org/fm/com/data/saf/SafAdapterTest.kt
@@ -255,5 +255,34 @@
         assertFalse(SafOperasiBerkas.apakahKonflikDuplikatProvider("Main.kt", "Main.kt"))
         assertFalse(SafOperasiBerkas.apakahKonflikDuplikatProvider("Main.kt", "Main.kt.txt"))
         assertTrue(SafOperasiBerkas.apakahKonflikDuplikatProvider("Main.kt", "Main (1).kt"))
     }
+
+    @Test
+    fun resolusiIndukDanRootTreeTermuxSertaExternalStorageAkurat() {
+        assertTrue(
+            SafOperasiBerkas.apakahRootTreeSaf(
+                treeDocId = "/data/data/com.termux/files/home",
+                docId = "/data/data/com.termux/files/home/"
+            )
+        )
+        assertFalse(
+            SafOperasiBerkas.apakahRootTreeSaf(
+                treeDocId = "/data/data/com.termux/files/home",
+                docId = "/data/data/com.termux/files/home/script.sh"
+            )
+        )
+        assertEquals(
+            "/data/data/com.termux/files/home/proyek",
+            SafOperasiBerkas.kandidatIndukDocId(
+                docId = "/data/data/com.termux/files/home/proyek/script.sh",
+                treeDocId = "/data/data/com.termux/files/home"
+            )
+        )
+        assertEquals(
+            "primary:",
+            SafOperasiBerkas.kandidatIndukDocId(
+                docId = "primary:catatan.txt",
+                treeDocId = "primary:"
+            )
+        )
+        assertNull(
+            SafOperasiBerkas.kandidatIndukDocId(
+                docId = "/data/data/com.termux/files/home",
+                treeDocId = "/data/data/com.termux/files/home"
+            )
+        )
+    }
 }

--- a/shared/src/commonTest/kotlin/org/fm/com/domain/usecase/OperasiDasarUseCaseTest.kt
+++ b/shared/src/commonTest/kotlin/org/fm/com/domain/usecase/OperasiDasarUseCaseTest.kt
@@ -57,6 +57,9 @@
         val sukses = renameUc(sumber, "baru.txt")
         assertIs<HasilOperasi.Sukses<*>>(sukses)
         assertEquals("baru.txt", repo.data("/root/baru.txt")?.nama)
+
+        val suksesDenganInduk = renameUc(LokasiBerkas("/root/baru.txt", JenisLokasi.POSIX, "baru.txt"), "akhir.txt", induk = root)
+        assertIs<HasilOperasi.Sukses<*>>(suksesDenganInduk)
+        assertEquals("akhir.txt", repo.data("/root/akhir.txt")?.nama)
     }
