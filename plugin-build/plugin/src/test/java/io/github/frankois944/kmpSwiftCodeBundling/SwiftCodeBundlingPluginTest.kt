package io.github.frankois944.kmpSwiftCodeBundling

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwiftCodeBundlingPluginTest {
    private fun extensionOf(): SwiftCodeBundlingExtension {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.frankois944.kmpSwiftCodeBundling")

        return project.extensions.getByType(SwiftCodeBundlingExtension::class.java)
    }

    @Test
    fun `plugin registers its extension`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.frankois944.kmpSwiftCodeBundling")

        assertTrue(project.extensions.getByName(SwiftBundling.EXTENSION_NAME) is SwiftCodeBundlingExtension)
    }

    @Test
    fun `extension exposes the expected defaults`() {
        val extension = extensionOf()

        assertEquals(true, extension.enabled.get())
        assertEquals("5", extension.swiftVersion.get())
        assertEquals(false, extension.enableSwiftLibraryEvolution.get())
        assertEquals(false, extension.noClangModuleBreadcrumbsInStaticFrameworks.get())
        assertEquals(false, extension.enableRelativeSourcePathsInDebugSymbols.get())
        assertEquals(emptyList<String>(), extension.freeSwiftCompilerArgs.get())
    }

    @Test
    fun `produceDistributableFramework enables the distribution options`() {
        val extension = extensionOf()

        extension.produceDistributableFramework()

        assertEquals(true, extension.enableSwiftLibraryEvolution.get())
        assertEquals(true, extension.noClangModuleBreadcrumbsInStaticFrameworks.get())
        assertEquals(true, extension.enableRelativeSourcePathsInDebugSymbols.get())
    }

    @Test
    fun `plugin does nothing without the kotlin multiplatform plugin`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.frankois944.kmpSwiftCodeBundling")

        assertTrue(project.tasks.names.none { it.startsWith("processSwiftSources") })
    }
}
