package co.touchlab.skie

import org.gradle.api.Plugin
import org.gradle.api.Project

/** A SKIE whose configuration this plugin cannot read: applied, but registering nothing known. */
class UnknownSkiePlugin : Plugin<Project> {
    override fun apply(project: Project) = Unit
}
