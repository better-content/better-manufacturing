import java.util.UUID

plugins {
    id("net.minecraftforge.gradle") version "6.0.54"
    id("org.spongepowered.mixin") version "0.7.38"
    jacoco
}

group = "com.bettercontent"
version = property("mod_version") as String
base { archivesName.set(property("artifact_name") as String) }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)); withSourcesJar() }
repositories {
    mavenCentral(); maven("https://maven.minecraftforge.net")
    maven("https://www.cursemaven.com") { content { includeGroup("curse.maven") } }
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/mirror")
    maven("https://maven.tterrag.com")
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

val modId = property("mod_id") as String
val nativeDirectory = layout.buildDirectory.dir("gametest/${UUID.randomUUID()}")
val withCreate = !providers.gradleProperty("withoutCreate").map(String::toBooleanStrict).getOrElse(false)
val createCoordinate = "com.simibubi.create:create-1.20.1:6.0.8-289:slim"
val createUpstream by configurations.creating { isTransitive = false }
val createFixture by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
val clientFixtureEnabled = providers.gradleProperty("manufacturingClientFixture").map(String::toBooleanStrict).getOrElse(false)
val clientDirectory = layout.buildDirectory.dir("client-fixture/${UUID.randomUUID()}")
val clientFixture by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
val nativeTestCount = if (withCreate) 13 else 9
val authoredDrillData = listOf(
    "create/recipes/crafting/kinetics/mechanical_drill.json",
    "create/loot_tables/blocks/mechanical_drill.json",
    "better_manufacturing/recipes/retool_drill_head.json")
tasks.named<ProcessResources>(createFixture.processResourcesTaskName) {
    authoredDrillData.forEach { relative ->
        from(file("../../better-content-modpack/kubejs/data/$relative")) { into("data/${relative.substringBeforeLast('/')}") }
    }
}
minecraft {
    mappings("official", property("minecraft_version") as String)
    copyIdeResources = true
    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "info")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)
            mods { create(modId) { source(sourceSets.main.get()) } }
        }
        create("client") {
            if (clientFixtureEnabled) {
                check(withCreate) { "The client fixture requires Create" }
                workingDirectory(clientDirectory.get().asFile)
                property("better_manufacturing.clientFixture", "true")
                args("--quickPlaySingleplayer", "manufacturing", "--width", "1280", "--height", "800")
                mods { named(modId) { source(createFixture); source(clientFixture) } }
            }
        }
        create("server") { arg("--nogui") }
        create("gameTestServer") {
            workingDirectory(nativeDirectory.get().asFile)
            property("forge.enableGameTest", "true")
            property("forge.gameTestServer", "true")
            property("forge.enabledGameTestNamespaces", modId)
            if (withCreate) mods { named(modId) { source(createFixture) } }
            arg("--nogui")
        }
    }
}
dependencies {
    minecraft("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}")
    implementation(fg.deobf("curse.maven:mantle-74924:7563777"))
    implementation(fg.deobf("curse.maven:tinkers-construct-74072:7449219"))
    createUpstream(createCoordinate)
    val createDependencies = listOf(createCoordinate,
        "net.createmod.ponder:Ponder-Forge-1.20.1:1.0.92", "com.tterrag.registrate:Registrate:MC1.20-1.3.3")
    createDependencies.forEach { compileOnly(fg.deobf(it)); if (withCreate) runtimeOnly(fg.deobf(it)) }
    compileOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-api-1.20.1:1.0.5"))
    if (withCreate) {
        runtimeOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-1.20.1:1.0.5"))
        runtimeOnly("io.github.llamalad7:mixinextras-forge:0.5.4")
    }
    compileOnly(fg.deobf("curse.maven:emi-580555:8081375"))
    if (clientFixtureEnabled) runtimeOnly(fg.deobf("curse.maven:emi-580555:8081375"))
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
tasks.withType<Test>().configureEach { useJUnitPlatform() }
jacoco { toolVersion = "0.8.12" }
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
}
val sourceIdentity by tasks.registering(Exec::class) {
    // The helper hashes production inputs, including dirty files; no false HEAD claim.
    outputs.upToDateWhen { false }
    commandLine("python3", "-B", "scripts/source_identity.py", projectDir.absolutePath,
        layout.buildDirectory.dir("generated/sourceIdentity").get().asFile.absolutePath)
}
tasks.processResources {
    dependsOn(sourceIdentity, tasks.compileJava)
    from(layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json")) { rename { "better_manufacturing.refmap.json" } }
    from(layout.buildDirectory.dir("generated/sourceIdentity"))
    val props = listOf("mod_id", "mod_name", "mod_version").associateWith { project.property(it) }
    inputs.properties(props)
    filesMatching("META-INF/mods.toml") { expand(props) }
}
mixin { add(sourceSets.main.get(), "better_manufacturing.refmap.json"); config("better_manufacturing.mixins.json") }
tasks.named<Jar>("jar") {
    manifest.attributes("MixinConfigs" to "better_manufacturing.mixins.json")
    // MixinGradle also contributes this same AP output to the archive; retain one identical copy.
    filesMatching("better_manufacturing.refmap.json") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    finalizedBy("reobfJar")
}
tasks.register<Copy>("stageRuntimeJar") {
    dependsOn("reobfJar")
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
}
val verifyCoverage by tasks.registering(Exec::class) {
    group = "verification"
    commandLine("python3", "-B", "scripts/coverage.py", "--pack", "../../better-content-modpack")
}
val verifyCoverageComplete by tasks.registering(Exec::class) {
    group = "verification"
    description = "Full-roster acceptance; deliberately fails while any implementation/evidence is missing."
    commandLine("python3", "-B", "scripts/coverage.py", "--pack", "../../better-content-modpack", "--require-complete")
}
tasks.register<Exec>("testBuildTools") {
    commandLine("python3", "-B", "-m", "unittest", "discover", "-s", "scripts/tests", "-v")
}
val verifyCreateFixture by tasks.registering(Exec::class) {
    val cache = providers.environmentVariable("BC_UPSTREAM_MOD_DIR").getOrElse(file("../../.cache/bc/packwiz-downloads/mods").absolutePath)
    doFirst { commandLine("python3", "-B", "scripts/create_fixture_identity.py", file("../../better-content-modpack/mods/create.pw.toml").absolutePath,
        createUpstream.singleFile.absolutePath, cache, layout.buildDirectory.file("create-fixture-identity.json").get().asFile.absolutePath) }
}
tasks.register("verifyFast") {
    group = "verification"
    dependsOn("check", "testBuildTools", verifyCoverage)
    if (withCreate) dependsOn(verifyCreateFixture)
}
tasks.matching { it.name == "runGameTestServer" }.configureEach {
    if (withCreate) dependsOn(verifyCreateFixture)
    doFirst {
        val directory = nativeDirectory.get().asFile
        check(!directory.exists()) { "Refusing reused native fixture: $directory" }
        directory.mkdirs()
        if (withCreate) copy { from(layout.buildDirectory.file("create-fixture-identity.json")); into(directory) }
        directory.resolve("runtime-classpath.txt").writeText(configurations.runtimeClasspath.get().files.sortedBy { it.absolutePath }.joinToString("\n") { it.absolutePath })
        val outputs = sourceSets.main.get().output.files + if (withCreate) createFixture.output.files else emptySet()
        directory.resolve("fixture-inputs.txt").writeText(outputs.sortedBy { it.absolutePath }.joinToString("\n") { it.absolutePath })
        copy {
            from("src/main/resources/data/better_manufacturing/gametest/structures")
            if (withCreate) from("src/createFixture/resources/data/better_manufacturing/gametest/structures")
            into(directory.resolve("gameteststructures"))
        }
    }
    doLast {
        val log = nativeDirectory.get().asFile.resolve("logs/latest.log").readText()
        val complete = Regex("(\\d+) GAME TESTS COMPLETE").findAll(log).map { it.groupValues[1].toInt() }.toList()
        val passed = Regex("All (\\d+) required tests passed").findAll(log).map { it.groupValues[1].toInt() }.toList()
        check(complete == listOf(nativeTestCount) && passed == listOf(nativeTestCount)) { "Missing/incorrect native test population: complete=$complete passed=$passed" }
        check(log.contains("Game test server shutting down")) { "Native test server did not shut down normally" }
        exec { commandLine("python3", "-B", "scripts/native_receipt.py", projectDir.absolutePath, nativeDirectory.get().asFile.absolutePath, nativeTestCount.toString()) }
    }
}
tasks.matching { it.name == "runClient" }.configureEach {
    if (clientFixtureEnabled) {
        dependsOn(verifyCreateFixture)
        doFirst {
            val sourceWorld = providers.gradleProperty("clientFixtureWorld").orNull ?: error("Pass -PclientFixtureWorld=<successful native fixture directory>")
            exec { commandLine("python3", "-B", "scripts/prepare_client_fixture.py", projectDir.absolutePath, sourceWorld, clientDirectory.get().asFile.absolutePath) }
            val directory = clientDirectory.get().asFile
            copy { from(layout.buildDirectory.file("create-fixture-identity.json")); into(directory) }
            directory.resolve("runtime-classpath.txt").writeText(configurations.runtimeClasspath.get().files.sortedBy { it.absolutePath }.joinToString("\n") { it.absolutePath })
            val outputs = sourceSets.main.get().output.files + createFixture.output.files + clientFixture.output.files
            directory.resolve("fixture-inputs.txt").writeText(outputs.sortedBy { it.absolutePath }.joinToString("\n") { it.absolutePath })
        }
        doLast {
            val evidence = clientDirectory.get().asFile.resolve("client-witness.json")
            check(evidence.isFile && evidence.readText().contains("\"status\": \"passed\"")) { "Client fixture failed or produced no witness: $evidence" }
            listOf("drill-wood.png", "drill-iron.png", "drill-retool.png").forEach { name ->
                check(clientDirectory.get().asFile.resolve("screenshots/$name").length() > 5000) { "Missing native screenshot: $name" }
            }
            val clientLog = clientDirectory.get().asFile.resolve("logs/latest.log").readLines()
            check(clientLog.none { it.contains("[ERROR]") && it.contains("[EMI") && it.contains("better_manufacturing:") }) { "Manufacturing EMI integration emitted recipe validation errors" }
            check(clientLog.none { it.contains("Unable to load model:") && it.contains("better_manufacturing:") }) { "Missing Manufacturing item models" }
            exec { commandLine("python3", "-B", "scripts/client_receipt.py", projectDir.absolutePath, clientDirectory.get().asFile.absolutePath) }
            println("Manufacturing client witness: $evidence")
        }
    }
}
tasks.register("verifyNative") { group = "verification"; dependsOn("runGameTestServer") }
tasks.register("verifyFull") {
    group = "verification"
    dependsOn("verifyFast", "verifyNative", verifyCoverageComplete)
}
