import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import java.security.MessageDigest

plugins { java }

group = "cn.codex.pluginlift"
version = "0.1.4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testCompileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

configurations.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

val smokeTest = tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
    mainClass.set("cn.codex.pluginlift.PluginLiftSmoke")
}

tasks.test {
    dependsOn(smokeTest)
    failOnNoDiscoveredTests.set(false)
}

tasks.jar {
    archiveFileName.set("PluginLift-${project.version}.jar")
}

val resourcePackZip = tasks.register<Zip>("resourcePackZip") {
    from("resource-pack")
    archiveFileName.set("PluginLift-ResourcePack-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val datapackZip = tasks.register<Zip>("datapackZip") {
    from("datapack")
    archiveFileName.set("PluginLift-Datapack-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

fun sha1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

val releaseDir = layout.buildDirectory.dir("release")

tasks.register("release") {
    dependsOn(tasks.build, resourcePackZip, datapackZip)
    doLast {
        val outputDir = releaseDir.get().asFile
        outputDir.mkdirs()
        val artifacts = listOf(
            layout.buildDirectory.file("libs/PluginLift-${project.version}.jar").get().asFile,
            datapackZip.get().archiveFile.get().asFile,
            resourcePackZip.get().archiveFile.get().asFile
        ).map { it.copyTo(outputDir.resolve(it.name), overwrite = true) }

        outputDir.resolve("PluginLift-SHA1-${project.version}.txt").writeText(
            artifacts.joinToString(System.lineSeparator(), postfix = System.lineSeparator()) {
                "${sha1(it)}  ${it.name}"
            }
        )
        file("README.md").copyTo(outputDir.resolve("README-${project.version}.md"), overwrite = true)
        file("THIRD_PARTY_NOTICES.md").copyTo(
            outputDir.resolve("THIRD_PARTY_NOTICES-${project.version}.md"),
            overwrite = true
        )
        file("LICENSE").copyTo(outputDir.resolve("LICENSE"), overwrite = true)
    }
}

tasks.wrapper {
    gradleVersion = "9.6.1"
    distributionType = Wrapper.DistributionType.BIN
}
