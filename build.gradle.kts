import java.util.zip.ZipFile

plugins {
    java
}

group = "com.plexon"
version = "1.0.0"

val pluginVersion = version.toString()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    // GriefPrevention 16.18.7-compatible artifact used by the previous Plexon addon.
    compileOnly("maven.modrinth:O4o4mKaq:dGfCZHqk")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:deprecation", "-Xlint:-processing"))
}

tasks.processResources {
    val props = mapOf("version" to pluginVersion)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(props) }
}

tasks.test { useJUnitPlatform() }

tasks.jar {
    archiveBaseName.set("PlexonGPFlags")
    archiveVersion.set(pluginVersion)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest {
        attributes(
            "Implementation-Title" to "PlexonGPFlags",
            "Implementation-Version" to pluginVersion,
            "Implementation-Vendor" to "ZpkDxGames"
        )
    }
}

val verifyHotPaths = tasks.register("verifyHotPaths") {
    group = "verification"
    doLast {
        val protection = file("src/main/java/com/plexon/gpflags/protection/ProtectionListener.java").readText()
        require(!protection.contains("flags.yml")) { "Protection hot path must not access flags.yml" }
        require(!protection.contains("saveConfig")) { "Protection hot path must not write config" }
        require(!protection.contains("Files.")) { "Protection hot path must not perform file I/O" }
        val visualizer = file("src/main/java/com/plexon/gpflags/service/VisualizerService.java").readText()
        require(!visualizer.contains("getClaims()")) { "Visualizer must not scan every GriefPrevention claim" }
    }
}

val verifyDistribution = tasks.register("verifyDistribution") {
    group = "verification"
    dependsOn(tasks.jar, verifyHotPaths)
    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile
        require(archive.isFile && archive.length() > 20_000L) { "Runtime JAR is missing or unexpectedly small: $archive" }
        ZipFile(archive).use { zip ->
            listOf(
                "plugin.yml",
                "com/plexon/gpflags/PlexonGPFlags.class",
                "com/plexon/gpflags/api/PlexonGPFlagsAPI.class",
                "com/plexon/gpflags/event/PlexonGPFlagChangedEvent.class",
                "com/plexon/gpflags/compat/LegacyClaimFlagsBridge.class",
                "net/plexon/claimflags/api/PlexonClaimFlagsAPI.class",
                "net/plexon/claimflags/api/FlagChangeResult.class",
                "net/plexon/claimflags/event/PlexonClaimFlagChangedEvent.class"
            ).forEach { entry -> require(zip.getEntry(entry) != null) { "Missing JAR entry: $entry" } }
            val pluginYml = zip.getInputStream(zip.getEntry("plugin.yml")).bufferedReader().readText()
            require(pluginYml.contains("name: PlexonGPFlags"))
            require(pluginYml.contains("version: 1.0.0"))
            require(pluginYml.contains("GriefPrevention"))
        }
    }
}

tasks.check { dependsOn(verifyDistribution) }
