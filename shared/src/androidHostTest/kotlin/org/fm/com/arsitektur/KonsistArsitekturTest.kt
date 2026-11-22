package org.fm.com.arsitektur

import androidx.lifecycle.ViewModel
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withAllParentsOf
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class KonsistArsitekturTest {

    @Test
    fun arahKetergantunganCleanArchitectureTerjaga() {
        Konsist.scopeFromProduction()
            .assertArchitecture {
                val domain = Layer("Domain", "org.fm.com.domain..")
                val data = Layer("Data", "org.fm.com.data..")
                val presentation = Layer("Presentation", "org.fm.com.presentation..")

                domain.dependsOnNothing()
                data.dependsOn(domain)
                presentation.dependsOn(domain)
                presentation.doesNotDependOn(data)
            }
    }

    @Test
    fun domainBebasDariApiPlatformComposeDanBasisData() {
        val awalanTerlarang = listOf(
            "android.",
            "androidx.",
            "platform.",
            "app.cash.sqldelight.",
            "org.koin.",
            "java.io.",
            "java.nio."
        )

        Konsist.scopeFromProduction()
            .files
            .filter { file ->
                val pkg = file.packagee?.name
                pkg == "org.fm.com.domain" || pkg?.startsWith("org.fm.com.domain.") == true
            }
            .assertFalse { file ->
                file.imports.any { imp ->
                    awalanTerlarang.any { prefiks -> imp.name.startsWith(prefiks) }
                }
            }
    }

    @Test
    fun useCaseBeradaDiPaketDomainUseCase() {
        Konsist.scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.resideInPackage("..domain.usecase..") }
    }

    @Test
    fun subkelasViewModelBeradaDiPresentationDenganPenamaanKonsisten() {
        Konsist.scopeFromProduction()
            .classes()
            .withAllParentsOf(ViewModel::class)
            .assertTrue { kelas ->
                kelas.resideInPackage("org.fm.com.presentation..") &&
                    (kelas.name.startsWith("ViewModel") || kelas.name.endsWith("ViewModel"))
            }
    }
}