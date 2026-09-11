import java.util.zip.ZipFile

plugins { java }

group = "com.plexon"
version = "1.1.0-rc.3"
val pluginVersion = version.toString()

repositories { mavenLocal(); mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/"); maven("https://api.modrinth.com/maven") }

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("maven.modrinth:O4o4mKaq:dGfCZHqk")
    compileOnly("com.zpkdxgames:PlexonCore:2.0.4")
    testImplementation(platform("org.junit:junit-bom:6.1.3")); testImplementation("org.junit.jupiter:junit-jupiter"); testImplementation("com.zpkdxgames:PlexonCore:2.0.4"); testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)); withSourcesJar(); withJavadocJar() }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(25); options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:deprecation", "-Xlint:-processing")) }
tasks.processResources { val props = mapOf("version" to pluginVersion); inputs.properties(props); filteringCharset = "UTF-8"; filesMatching("plugin.yml") { expand(props) } }
tasks.test { useJUnitPlatform(); reports.junitXml.required.set(true) }
tasks.jar { archiveBaseName.set("PlexonGPFlags"); archiveVersion.set(pluginVersion); isPreserveFileTimestamps = false; isReproducibleFileOrder = true; manifest { attributes("Implementation-Title" to "PlexonGPFlags", "Implementation-Version" to pluginVersion, "Implementation-Vendor" to "ZpkDxGames") } }

val verifyHotPaths = tasks.register("verifyHotPaths") {
    group = "verification"
    doLast {
        val protection = file("src/main/java/com/plexon/gpflags/protection/ProtectionListener.java").readText()
        listOf("flags.yml", "saveConfig", "Files.", "PlaceholderAPI", "Bukkit.getScheduler").forEach { forbidden -> require(!protection.contains(forbidden)) { "Protection hot path contains forbidden operation: $forbidden" } }
        val visualizer = file("src/main/java/com/plexon/gpflags/service/VisualizerService.java").readText(); require(!visualizer.contains("getClaims()")) { "Visualizer must not scan every GriefPrevention claim" }
    }
}

val verifyDistribution = tasks.register("verifyDistribution") {
    group = "verification"; dependsOn(tasks.jar, verifyHotPaths)
    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile; require(archive.isFile && archive.length() > 20_000L) { "Runtime JAR is missing or unexpectedly small: $archive" }
        ZipFile(archive).use { zip ->
            listOf("plugin.yml", "config.yml", "messages.yml", "com/plexon/gpflags/PlexonGPFlags.class", "com/plexon/gpflags/api/PlexonGPFlagsAPI.class", "com/plexon/gpflags/event/PlexonGPFlagChangedEvent.class", "com/plexon/gpflags/compat/LegacyClaimFlagsBridge.class", "com/plexon/gpflags/integration/core/CoreBridge.class", "com/plexon/gpflags/config/ConfigValidator.class", "com/plexon/gpflags/gui/MenuSessionGuard.class", "net/plexon/claimflags/api/PlexonClaimFlagsAPI.class", "net/plexon/claimflags/api/FlagChangeResult.class", "net/plexon/claimflags/event/PlexonClaimFlagChangedEvent.class").forEach { entry -> require(zip.getEntry(entry) != null) { "Missing JAR entry: $entry" } }
            val forbiddenPrefixes = listOf("com/zpkdxgames/plexoncore/", "me/ryanhamshire/GriefPrevention/", "org/bukkit/", "io/papermc/", "net/kyori/adventure/")
            for (entry in zip.entries()) require(forbiddenPrefixes.none { entry.name.startsWith(it) }) { "Compile-only/server dependency must not be shaded: ${entry.name}" }
            val mainClass = zip.getInputStream(zip.getEntry("com/plexon/gpflags/PlexonGPFlags.class")).readBytes(); val major = ((mainClass[6].toInt() and 0xff) shl 8) or (mainClass[7].toInt() and 0xff); require(major == 69) { "Expected Java 25 class major 69, got $major" }
            val pluginYml = zip.getInputStream(zip.getEntry("plugin.yml")).bufferedReader().readText(); require(pluginYml.contains("name: PlexonGPFlags")); require(pluginYml.contains("version: $pluginVersion")); require(pluginYml.contains("depend: [GriefPrevention]"))
        }
    }
}

tasks.check { dependsOn(verifyDistribution) }
