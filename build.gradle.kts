import com.github.jk1.license.filter.ExcludeTransitiveDependenciesFilter
import com.github.jk1.license.render.JsonReportRenderer
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.kotlin.com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.nio.file.Path
import kotlin.io.path.div

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    `java-library`
    alias(libs.plugins.dependency.license.report)
    alias(libs.plugins.ksp)
    alias(libs.plugins.gradle.wrapper)
}

buildscript {
    dependencies {
        classpath(libs.marketplace.client)
    }
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/tbx/toolbox-api")
}

jvmWrapper {
    unixJvmInstallDir = "jvm"
    winJvmInstallDir = "jvm"
    linuxAarch64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-21.0.5-linux-aarch64-b631.28.tar.gz"
    linuxX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-21.0.5-linux-x64-b631.28.tar.gz"
    macAarch64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-21.0.5-osx-aarch64-b631.28.tar.gz"
    macX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-21.0.5-osx-x64-b631.28.tar.gz"
    windowsX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-21.0.5-windows-x64-b631.28.tar.gz"
}

dependencies {
    compileOnly(libs.bundles.toolbox.plugin.api)
    implementation(libs.slf4j)
    implementation(libs.bundles.serialization)
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.exec)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    testImplementation(kotlin("test"))
}

licenseReport {
    renderers = arrayOf(JsonReportRenderer("dependencies.json"))
    filters = arrayOf(ExcludeTransitiveDependenciesFilter())
    // jq script to convert to our format:
    // `jq '[.dependencies[] | {name: .moduleName, version: .moduleVersion, url: .moduleUrl, license: .moduleLicense, licenseUrl: .moduleLicenseUrl}]' < build/reports/dependency-license/dependencies.json > src/main/resources/dependencies.json`
}

tasks.compileKotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.test {
    useJUnitPlatform()
}

val pluginId = "com.coder.gateway"
val pluginVersion = "0.0.1"

val assemblePlugin by tasks.registering(Jar::class) {
    archiveBaseName.set(pluginId)
    from(sourceSets.main.get().output)
}

val copyPlugin by tasks.creating(Sync::class.java) {
    dependsOn(assemblePlugin)

    val userHome = System.getProperty("user.home").let { Path.of(it) }
    val toolboxCachesDir = when {
        SystemInfoRt.isWindows -> System.getenv("LOCALAPPDATA")?.let { Path.of(it) } ?: (userHome / "AppData" / "Local")
        // currently this is the location that TBA uses on Linux
        SystemInfoRt.isLinux -> System.getenv("XDG_DATA_HOME")?.let { Path.of(it) } ?: (userHome / ".local" / "share")
        SystemInfoRt.isMac -> userHome / "Library" / "Caches"
        else -> error("Unknown os")
    } / "JetBrains" / "Toolbox"

    val pluginsDir = when {
        SystemInfoRt.isWindows -> toolboxCachesDir / "cache"
        SystemInfoRt.isLinux || SystemInfoRt.isMac -> toolboxCachesDir
        else -> error("Unknown os")
    } / "plugins"

    val targetDir = pluginsDir / pluginId

    from(assemblePlugin.get().outputs.files)

    from("src/main/resources") {
        include("extension.json")
        include("dependencies.json")
        include("icon.svg")
    }

    // Copy dependencies, excluding those provided by Toolbox.
    from(
        configurations.compileClasspath.map { configuration ->
            configuration.files.filterNot { file ->
                listOf(
                    "kotlin",
                    "remote-dev-api",
                    "core-api",
                    "ui-api",
                    "annotations",
                    "okhttp",
                    "okio",
                    "slf4j",
                ).any { file.name.contains(it) }
            }
        },
    )

    into(targetDir)
}

val pluginZip by tasks.creating(Zip::class) {
    dependsOn(assemblePlugin)

    from(assemblePlugin.get().outputs.files)
    from("src/main/resources") {
        include("extension.json")
        include("dependencies.json")
    }
    from("src/main/resources") {
        include("icon.svg")
        rename("icon.svg", "pluginIcon.svg")
    }
    archiveBaseName.set("$pluginId-$pluginVersion")
}

val uploadPlugin by tasks.creating {
    dependsOn(pluginZip)

    doLast {
        val instance = PluginRepositoryFactory.create("https://plugins.jetbrains.com", project.property("pluginMarketplaceToken").toString())

        // first upload
        // instance.uploader.uploadNewPlugin(pluginZip.outputs.files.singleFile, listOf("toolbox", "gateway"), LicenseUrl.APACHE_2_0, ProductFamily.TOOLBOX)

        // subsequent updates
        instance.uploader.upload(pluginId, pluginZip.outputs.files.singleFile)
    }
}

// For use with kotlin-language-server.
tasks.register("classpath") {
    doFirst {
        File("classpath").writeText(
          sourceSets["main"].runtimeClasspath.asPath
        )
    }
}
