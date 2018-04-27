package org.jlleitschuh.gradle.ktlint

import net.swiftzer.semver.SemVer
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.jlleitschuh.gradle.ktlint.reporter.KtlintReport
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.io.File

open class KtlintCheck : DefaultTask() {

    @get:Classpath
    val classpath: ConfigurableFileCollection = project.files()

    @get:Internal
    val sourceDirectories: ConfigurableFileCollection = project.files()

    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val sources: FileTree = sourceDirectories.asFileTree.matching { it.include("**/*.kt") }

    @get:Input
    val verbose: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType)
    @get:Input
    val debug: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType)
    @get:Input
    val android: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType)
    @get:Input
    val ignoreFailures: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType)
    @get:Console
    val outputToConsole: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType)

    @get:Internal
    val reports: Map<ReporterType, KtlintReport> = ReporterType.values().map {
        it to KtlintReport(project.objects.property(Boolean::class.javaObjectType).apply { set(false) }, it, newOutputFile())
    }.toMap()

    @get:Nested
    val enabledReports
        get() = reports.filterValues { it.enabled.get() }

    @TaskAction
    fun lint() {
        val reportsToProcess = enabledReports.values
        val ktlintVersion = determineKtlintVersion(classpath)
        ktlintVersion?.let { version ->
            if (version < SemVer(0, 20, 0)) {
                reportsToProcess.forEach {
                    val reportOutputPath = it.outputFile.get().asFile.absolutePath
                    if (reportOutputPath.contains(" ")) {
                        throw GradleException(
                                "The output path passed `$reportOutputPath` contains spaces. Ktlint versions less than 0.20.0 do not support this.")
                    }
                }
            }
        }
        project.javaexec {
            it.classpath = classpath
            it.main = "com.github.shyiko.ktlint.Main"
            it.args(sourceDirectories.map { "${it.path}/**/*.kt" })
            if (verbose.get()) {
                it.args("--verbose")
            }
            if (debug.get()) {
                it.args("--debug")
            }
            if (android.get()) {
                it.args("--android")
            }
            if (outputToConsole.get()) {
                it.args("--reporter=plain")
            }
            it.isIgnoreExitValue = ignoreFailures.get()
            it.args(reportsToProcess.map { it.asArgument() })
        }
    }

    private
    fun determineKtlintVersion(ktlintClasspath: Iterable<File>): SemVer? {
        val ktlintJarRegex = "ktlint-(\\d+.*)\\.jar".toRegex()
        val ktlintJarName = ktlintClasspath.find { ktlintJarRegex.matches(it.name) }?.name
        val version = ktlintJarName?.let {
            val versionString = ktlintJarRegex.matchEntire(it)!!.groups[1]!!.value
            try { SemVer.parse(versionString) } catch (e: IllegalArgumentException) { null }
        }
        return version
    }
}