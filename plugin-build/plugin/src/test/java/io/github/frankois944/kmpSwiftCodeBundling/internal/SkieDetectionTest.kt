package io.github.frankois944.kmpSwiftCodeBundling.internal

import co.touchlab.skie.StubSkieExtension
import co.touchlab.skie.StubSkiePlugin
import co.touchlab.skie.UnknownSkiePlugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkieDetectionTest {
    private fun projectWithSkie(): Project =
        ProjectBuilder.builder().build().also {
            it.pluginManager.apply("io.github.frankois944.kmpSwiftCodeBundling")
            it.pluginManager.apply(StubSkiePlugin::class.java)
        }

    private fun Project.skie(): StubSkieExtension = extensions.getByType(StubSkieExtension::class.java)

    @Test
    fun `reports no skie on a project without it`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.frankois944.kmpSwiftCodeBundling")

        assertEquals(SkieDetection.Status.INACTIVE, SkieDetection.statusOf(project))
    }

    @Test
    fun `an applied skie bundles swift by default`() {
        assertEquals(SkieDetection.Status.BUNDLING_SWIFT, SkieDetection.statusOf(projectWithSkie()))
    }

    @Test
    fun `a disabled skie is out of the way`() {
        val project = projectWithSkie()

        project.skie().isEnabled.set(false)

        assertEquals(SkieDetection.Status.INACTIVE, SkieDetection.statusOf(project))
    }

    @Test
    fun `skie without its own bundling still owns the swift module`() {
        val project = projectWithSkie()
        val skie = project.skie()

        skie.swiftBundling.enabled.set(false)

        assertEquals(SkieDetection.Status.GENERATING_SWIFT_ONLY, SkieDetection.statusOf(project))
    }

    @Test
    fun `an unreadable skie configuration counts as bundling`() {
        // What an unknown SKIE version looks like: recognised by its package, nothing readable
        // behind it. Stepping aside is the safe answer.
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(UnknownSkiePlugin::class.java)

        assertEquals(SkieDetection.Status.BUNDLING_SWIFT, SkieDetection.statusOf(project))
    }

    @Test
    fun `the warning names the project and both plugins`() {
        val warning = SkieDetection.conflictWarning(":shared", SkieDetection.Status.BUNDLING_SWIFT)

        assertTrue(warning.contains(":shared"))
        assertTrue(warning.contains(SkieDetection.SKIE_PLUGIN_ID))
        assertTrue(warning.contains("src/<sourceSet>/swift"))
    }

    @Test
    fun `the warning explains why turning skie bundling off is not enough`() {
        val warning =
            SkieDetection.conflictWarning(":shared", SkieDetection.Status.GENERATING_SWIFT_ONLY)

        assertTrue(warning.contains("Its own Swift code bundling is off"))
        assertTrue(warning.contains("skie { isEnabled = false }"))
    }
}
