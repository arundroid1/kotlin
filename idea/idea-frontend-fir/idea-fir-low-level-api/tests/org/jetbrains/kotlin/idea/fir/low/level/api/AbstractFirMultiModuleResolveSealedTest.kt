/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.idea.fir.low.level.api.api.getOrBuildFir
import org.jetbrains.kotlin.idea.fir.low.level.api.api.getResolveState
import org.jetbrains.kotlin.idea.jsonUtils.getString
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.io.File
import java.nio.file.Paths

abstract class AbstractFirMultiModuleResolveSealedTest : AbstractMultiModuleTest() {
    override fun getTestDataPath(): String =
        "${KtTestUtil.getHomeDirectory()}/idea/idea-frontend-fir/idea-fir-low-level-api/testdata/multiModuleResolveSealed/"

    fun doTest(path: String) {
        val testStructure = MultiModuleTestProjectStructureForSealed.fromTestProjectStructure(TestProjectStructureReader.read(Paths.get(path)))
        val modulesByNames = testStructure.modules.associate { moduleData ->
            moduleData.name to module(moduleData.name)
        }
        testStructure.modules.forEach { moduleData ->
            val module = modulesByNames.getValue(moduleData.name)
            moduleData.dependsOnModules.forEach { dependencyName ->
                module.addDependency(modulesByNames.getValue(dependencyName))
            }
        }
        val moduleToResolve = modulesByNames.getValue(testStructure.fileToResolve.moduleName)
        val fileToAnalysePath = moduleToResolve.sourceRoots.first().url + "/" + testStructure.fileToResolve.relativeFilePath

        val virtualFileToAnalyse = VirtualFileManager.getInstance().findFileByUrl(fileToAnalysePath)
                ?: error("File ${testStructure.fileToResolve.filePath} not found")
        val ktFileToAnalyse = PsiManager.getInstance(project).findFile(virtualFileToAnalyse) as KtFile
        val resolveState = ktFileToAnalyse.getResolveState()

        val fails = testStructure.fails

        try {
            val inheritors = executeOnPooledThreadInReadAction {
                val fir = ktFileToAnalyse.getOrBuildFir(resolveState)
                val declaration = (fir as FirFile).declarations.firstOrNull { it is FirRegularClass && it.isSealed } as? FirRegularClass
                declaration?.sealedInheritors
            }

            val toString = inheritors?.map { it.asString() }?.toList()
            KotlinTestUtils.assertEqualsToFileIgnoreOrder(File("$path/expected.txt"), toString!!)

        } catch (e: Throwable) {
            if (!fails) throw e
            return
        }
        if (fails) {
            throw AssertionError("Looks like test is passing, please remove `\"fails\": true` from structure.json")
        }
    }
}

private data class FileToResolveForSealed(val moduleName: String, val relativeFilePath: String) {
    val filePath get() = "$moduleName/$relativeFilePath"

    companion object {
        fun parse(json: JsonElement): FileToResolveForSealed {
            require(json is JsonObject)
            return FileToResolveForSealed(
                moduleName = json.getString("module"),
                relativeFilePath = json.getString("file")
            )
        }
    }
}

private data class MultiModuleTestProjectStructureForSealed(
    val modules: List<TestProjectModule>,
    val fileToResolve: FileToResolveForSealed,
    val fails: Boolean
) {
    companion object {
        fun fromTestProjectStructure(testProjectStructure: TestProjectStructure): MultiModuleTestProjectStructureForSealed {
            val json = testProjectStructure.json

            val fails = if (json.has(FAILS_FIELD)) json.get(FAILS_FIELD).asBoolean else false
            val fileToResolve = FileToResolveForSealed.parse(json.getAsJsonObject("fileToResolve"))

            return MultiModuleTestProjectStructureForSealed(
                testProjectStructure.modules,
                fileToResolve,
                fails
            )
        }

        private const val FAILS_FIELD = "fails"
    }
}
