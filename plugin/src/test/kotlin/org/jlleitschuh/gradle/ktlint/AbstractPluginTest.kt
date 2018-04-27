package org.jlleitschuh.gradle.ktlint

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.TextUtil.normaliseFileSeparators
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

abstract class AbstractPluginTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    val projectRoot: File
        get() = temporaryFolder.root.resolve("plugin-test").apply { mkdirs() }

    protected
    fun buildscriptBlockWithUnderTestPlugin() =
        """
            buildscript {
                repositories { maven { url = "$testRepositoryPath" } }
                dependencies {
                    classpath("org.jlleitschuh.gradle:ktlint-gradle:${testProperties["version"]}")
                }
            }
        """.trimIndent()

    protected
    fun pluginsBlockWithKotlinJvmPlugin() =
        """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${testProperties["kotlinVersion"]}"
            }
        """.trimIndent()

    protected
    fun build(vararg arguments: String): BuildResult =
        gradleRunnerFor(*arguments).forwardOutput().build()

    protected
    fun buildAndFail(vararg arguments: String): BuildResult =
        gradleRunnerFor(*arguments).forwardOutput().buildAndFail()

    protected
    fun gradleRunnerFor(vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectRoot)
            .withArguments(arguments.toList())

    private
    val testRepositoryPath
        get() = normaliseFileSeparators(File("build/plugin-test-repository").absolutePath)

    protected
    val testProperties: Properties by lazy {
        javaClass.getResourceAsStream("/test.properties").use {
            Properties().apply { load(it) }
        }
    }

    protected
    fun withCleanSources() = createSourceFile("src/main/kotlin/source.kt", """val foo = "bar"""")

    protected
    fun withFailingSources() = createSourceFile("src/main/kotlin/source.kt", """val  foo    =     "bar"""")

    private
    fun createSourceFile(sourceFilePath: String, contents: String) {
        val sourceFile = projectRoot.resolve(sourceFilePath)
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(contents)
    }
}