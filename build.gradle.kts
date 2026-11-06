import java.security.MessageDigest
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
    flatDir { dirs(providers.environmentVariable("BC_UPSTREAM_MOD_DIR").getOrElse(file("../../.cache/bc/packwiz-downloads/mods").absolutePath)) }
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
val nativeProfile = providers.gradleProperty("manufacturingNativeProfile").getOrElse("core")
// Opt-in catalogue GameTests leave existing native populations untouched.
// Example (after source authoring): ./gradlew -PmanufacturingNativeProfile=catalogue
// -PmanufacturingCatalogueFixture=true runGameTestServer. For create/pneu, pass the
// existing profile's base -PmanufacturingNativeTestCount; the fixture adds 1 (or 4
// for catalogue) to the required total and receipt automatically.
val catalogueFixtureEnabled = providers.gradleProperty("manufacturingCatalogueFixture").map(String::toBooleanStrict).getOrElse(false)
require(!catalogueFixtureEnabled || nativeProfile in setOf("create", "create_powergrid", "pneu", "catalogue")) {
    "Catalogue native fixtures require a supported optional-owner profile"
}
require(nativeProfile != "catalogue" || catalogueFixtureEnabled) { "Catalogue profile requires -PmanufacturingCatalogueFixture=true" }
require(nativeProfile in setOf("core", "create", "create_bumblezone", "create_powergrid", "fluids", "pneu", "catalogue")) { "Unknown Manufacturing native profile: $nativeProfile" }
val packRoot = file("../../better-content-modpack/kubejs")
val upstreamCache = providers.environmentVariable("BC_UPSTREAM_MOD_DIR")
    .getOrElse(file("../../.cache/bc/packwiz-downloads/mods").absolutePath)
// Fluids-only userdev: preserve the original AdPother and ForgeGradle caches.
// A bounded helper publishes one resource-only FIELD mapping correction under a
// DISTINCT local coordinate; normal ForgeGradle deobf maps that raw archive.
val devAdpotherRoot = layout.buildDirectory.dir("generated/dev-adpother-fieldmap")
val devAdpotherCoordinate = "com.bettercontent.manufacturing.dev:adpother-fieldmap-1.20.1:8.1.49.0-fieldmap.1"
val originalAdpother = file("$upstreamCache/AdPother-1.20.1-8.1.49.0-build.2294.jar")
val originalMappedAdpother = gradle.gradleUserHomeDir.resolve(
    "caches/forge_gradle/deobf_dependencies/local/AdPother/1.20.1-8.1.49.0-build.2294_mapped_official_1.20.1/AdPother-1.20.1-8.1.49.0-build.2294_mapped_official_1.20.1.jar")
if (nativeProfile == "fluids") repositories {
    maven {
        url = uri(devAdpotherRoot.map { it.dir("maven") }.get().asFile)
        content { includeGroup("com.bettercontent.manufacturing.dev") }
    }
}
val prepareDevAdpotherFieldmap by tasks.registering(Exec::class) {
    inputs.files(originalAdpother, originalMappedAdpother, file("scripts/prepare_adpother_fieldmap.py"))
    outputs.upToDateWhen { false } // Always revalidate immutable raw, mapped cache, derived bytes and receipt.
    commandLine("python3", "-B", "scripts/prepare_adpother_fieldmap.py", originalAdpother.absolutePath,
        originalMappedAdpother.absolutePath, devAdpotherRoot.get().asFile.absolutePath)
}
val verifyDevAdpotherMapped by tasks.registering {
    dependsOn(prepareDevAdpotherFieldmap)
    outputs.upToDateWhen { false }
    doLast {
        val matches = configurations.runtimeClasspath.get().files.filter { it.isFile && it.name.contains("adpother", ignoreCase = true) }
        check(matches.size == 1 && matches.single().name.startsWith("adpother-fieldmap-1.20.1-8.1.49.0-fieldmap.1_mapped_")) {
            "Exactly one distinct derived mapped AdPother dependency is required, not the pinned original: $matches"
        }
        val mapped = matches.single()
        exec { commandLine("python3", "-B", "scripts/prepare_adpother_fieldmap.py", originalAdpother.absolutePath,
            originalMappedAdpother.absolutePath, devAdpotherRoot.get().asFile.absolutePath, "--verify-mapped", mapped.absolutePath) }
        val hash = MessageDigest.getInstance("SHA-256").digest(mapped.readBytes()).joinToString("") { "%02x".format(it) }
        val record = "coordinate=$devAdpotherCoordinate\nartifact=${mapped.absolutePath}\nsha256=$hash\n" +
            "field_transform_and_pollution_callback=PENDING_SEPARATE_NATIVE_AUTHORIZATION\n"
        val output = devAdpotherRoot.get().asFile.resolve("mapped-provenance.txt")
        if (output.exists()) check(output.readText() == record) { "Refusing stale or changed mapped AdPother provenance: $output" }
        else output.writeText(record)
        println("DEV_ADPOTHER_SINGLE_MAPPED_DEPENDENCY=${mapped.absolutePath} SHA256=$hash")
    }
}
// Required Bumblezone userdev profile only: pin the original and ForgeGradle-mapped
// inputs, then remap stale SRG mixin selector strings in an isolated generated JAR.
val devBumblezoneRoot = layout.buildDirectory.dir("generated/dev-bumblezone-userdev")
val pinnedBumblezone = file("$upstreamCache/the_bumblezone-7.13.4+1.20.1-forge.jar")
val mappedBumblezone = gradle.gradleUserHomeDir.resolve(
    "caches/forge_gradle/deobf_dependencies/local/the_bumblezone/7.13.4+1.20.1-forge_mapped_official_1.20.1/the_bumblezone-7.13.4+1.20.1-forge_mapped_official_1.20.1.jar")
val devBumblezoneJar = devBumblezoneRoot.map { it.file("the_bumblezone-7.13.4+1.20.1-forge-userdev-selectors.jar") }
val prepareDevBumblezoneUserdev by tasks.registering(Exec::class) {
    dependsOn("createSrgToMcp")
    inputs.files(pinnedBumblezone, mappedBumblezone, file("build/createSrgToMcp/output.srg"),
        file("scripts/prepare_bumblezone_userdev.py"))
    outputs.upToDateWhen { false } // Validate immutable hashes and the complete generated artifact each run.
    commandLine("python3", "-B", "scripts/prepare_bumblezone_userdev.py", pinnedBumblezone.absolutePath,
        mappedBumblezone.absolutePath, file("build/createSrgToMcp/output.srg").absolutePath,
        devBumblezoneRoot.get().asFile.absolutePath)
}
val pinnedApis = layout.buildDirectory.dir("generated/embedded-apis")
val extractEmbeddedApis by tasks.registering(Exec::class) {
    inputs.files(file("$upstreamCache/valkyrienskies-120-2.4.11.jar"), file("$upstreamCache/clockwork-0.5.6.jar"),
        file("scripts/extract_embedded_apis.py"))
    outputs.files(pinnedApis.map { it.file("valkyrienskies-api-1.1.0.jar") }, pinnedApis.map { it.file("kelvin-forge-1.20.1-0.5.0.jar") })
    commandLine("python3", "-B", "scripts/extract_embedded_apis.py", upstreamCache, pinnedApis.get().asFile.absolutePath)
}
val createFixture by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
// ForgeGradle mounts SourceSet.output.classesDirs and resourcesDir, NOT arbitrary
// output.dir entries. Point both at the freshly built sibling owner outputs; never
// mount an old build/libs JAR or Gradle's nonexistent default placeholder paths.
val industrialHeatRuntime by sourceSets.creating {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
    (output.classesDirs as org.gradle.api.file.ConfigurableFileCollection).setFrom(files("../better-industrial-heat/build/classes/kotlin/main",
        "../better-industrial-heat/build/classes/java/main"))
    output.resourcesDir = file("../better-industrial-heat/build/resources/main")
}
val hazardsRuntime by sourceSets.creating {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
    (output.classesDirs as org.gradle.api.file.ConfigurableFileCollection).setFrom(files("../better-chemlib-hazards/build/classes/java/main"))
    output.resourcesDir = file("../better-chemlib-hazards/build/resources/main")
}
val airtightRuntime by sourceSets.creating {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
    (output.classesDirs as org.gradle.api.file.ConfigurableFileCollection).setFrom(files("../better-airtight-machines/build/classes/java/main"))
    output.resourcesDir = file("../better-airtight-machines/build/resources/main")
}
// These empty SourceSets only describe Forge's mounted owner mods. The separate
// compile*Owner tasks are the sole producers; disable local placeholder writers.
listOf(industrialHeatRuntime, hazardsRuntime, airtightRuntime).forEach { owner ->
    tasks.named(owner.compileJavaTaskName) { enabled = false }
    tasks.named(owner.processResourcesTaskName) { enabled = false }
}
val compileIndustrialHeatOwner by tasks.registering(Exec::class) {
    workingDir(file("../better-industrial-heat"))
    commandLine("./gradlew", "--offline", "--no-daemon", "--max-workers=2", "compileKotlin", "compileJava", "processResources")
}
val compileRailBeetleOwner by tasks.registering(Exec::class) {
    workingDir(file("../better-rail-beetle"))
    commandLine("./gradlew", "--offline", "--no-daemon", "--max-workers=2", "compileJava", "processResources")
}
val compileHazardsOwner by tasks.registering(Exec::class) {
    workingDir(file("../better-chemlib-hazards"))
    commandLine("./gradlew", "--offline", "--no-daemon", "--max-workers=2", "compileJava", "processResources")
}
val compileAirtightOwner by tasks.registering(Exec::class) {
    workingDir(file("../better-airtight-machines"))
    commandLine("./gradlew", "--offline", "--no-daemon", "--max-workers=2", "compileJava", "processResources")
}
// The owners generate these refmaps under build/tmp/compileJava, not under their
// mounted resources/main directories. Copy only fresh generated bytes into the
// selected fluids profile output, without writing to a sibling or staging a JAR.
val fluidOwnerMixins = listOf(
    Triple("better-industrial-heat", "better_industrial_heat.mixins.json", "better_industrial_heat.refmap.json"),
    Triple("better-chemlib-hazards", "better_chemlib_hazards.mixins.json", "better_chemlib_hazards.refmap.json"),
    Triple("better-airtight-machines", "better_airtight_machines.mixins.json", "better_airtight_machines.refmap.json")
)
// SourceSet identity, not just the compiler destination, must be profile-specific:
// ForgeGradle mounts SourceSet.output.classesDirs when discovering GameTests.
val wave2SourceSetName = when (nativeProfile) {
    "create" -> "wave2NativeCreate"
    "create_bumblezone" -> "wave2NativeBumblezone"
    "create_powergrid" -> "wave2NativePowergrid"
    "catalogue" -> "wave2NativeCatalogue"
    "fluids" -> "wave2NativeFluids"
    "pneu" -> "wave2NativePneu"
    else -> "wave2NativeCore"
}
val wave2Fixture = sourceSets.create(wave2SourceSetName) {
    val families = when (nativeProfile) {
        "create", "create_bumblezone" -> listOf("wave2CreateFixture")
        "create_powergrid" -> listOf("wave2CreateFixture", "wave2PowergridFixture")
        "catalogue" -> listOf("catalogueOptionalFixture")
        "fluids" -> listOf("wave2FluidsHeatFixture")
        "pneu" -> listOf("wave2PneuMechanicsFixture", "wave2PneuProcessFixture")
        else -> emptyList()
    }
    java.setSrcDirs(families.map { "src/$it/java" } + if (catalogueFixtureEnabled && nativeProfile != "catalogue") listOf(
        if (nativeProfile == "pneu") "src/cataloguePneuFixture/java" else "src/catalogueCreateFixture/java"
    ) else emptyList())
    if (nativeProfile in setOf("create", "create_powergrid")) java.exclude("**/Wave2CreateHarvesterTests.java")
    resources.setSrcDirs(families.map { "src/$it/resources" })
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath + createFixture.output + createFixture.compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath + createFixture.output + createFixture.runtimeClasspath
    if (nativeProfile == "fluids") {
        compileClasspath += industrialHeatRuntime.output + hazardsRuntime.output + airtightRuntime.output
        runtimeClasspath += industrialHeatRuntime.output + hazardsRuntime.output + airtightRuntime.output
    }
}
val clientFixtureEnabled = providers.gradleProperty("manufacturingClientFixture").map(String::toBooleanStrict).getOrElse(false)
val clientDirectory = layout.buildDirectory.dir("client-fixture/${UUID.randomUUID()}")
val clientFixture by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
require(nativeProfile == "core" || withCreate) { "Wave-two native profiles require Create" }
val nativeTestCount = providers.gradleProperty("manufacturingNativeTestCount").map(String::toInt).orNull
    ?: if (withCreate) 23 else 11 // 21 wave9 baseline plus two Wave2Core tests
val catalogueTestCount = if (!catalogueFixtureEnabled) 0 else if (nativeProfile == "catalogue") 4 else 1
val expectedNativeTestCount = nativeTestCount + catalogueTestCount
val authoredDrillData = listOf(
    "create/recipes/crafting/kinetics/mechanical_drill.json",
    "create/loot_tables/blocks/mechanical_drill.json",
    "better_manufacturing/recipes/retool_drill_head.json",
    "better_manufacturing/recipes/retool_mesh_frame.json",
    "create/recipes/crafting/kinetics/mechanical_saw.json",
    "create/recipes/crafting/kinetics/mechanical_press.json",
    "create/recipes/crafting/kinetics/millstone.json",
    "create/recipes/crafting/kinetics/whisk.json",
    "create/recipes/crafting/kinetics/mechanical_mixer.json",
    "create/loot_tables/blocks/mechanical_saw.json",
    "create/loot_tables/blocks/mechanical_press.json",
    "create/loot_tables/blocks/millstone.json",
    "create/loot_tables/blocks/mechanical_mixer.json",
    "create/recipes/crafting/kinetics/propeller.json",
    "create/recipes/crafting/kinetics/encased_fan.json",
    "create/loot_tables/blocks/encased_fan.json",
    "create/recipes/crafting/kinetics/mechanical_pump.json",
    "create/loot_tables/blocks/mechanical_pump.json")
tasks.named<ProcessResources>(createFixture.processResourcesTaskName) {
    from(file("../../better-content-modpack/kubejs/data/better_manufacturing/engineering")) { into("data/better_manufacturing/engineering") }
    from(file("../../better-content-modpack/kubejs/data/createsifter/recipes")) { into("data/createsifter/recipes") }
    authoredDrillData.forEach { relative ->
        from(file("../../better-content-modpack/kubejs/data/$relative")) { into("data/${relative.substringBeforeLast('/')}") }
    }
}
tasks.named<ProcessResources>(wave2Fixture.processResourcesTaskName) {
    if (nativeProfile == "fluids") {
        dependsOn(compileIndustrialHeatOwner, compileHazardsOwner, compileAirtightOwner)
        fluidOwnerMixins.forEach { (owner, config, refmap) ->
            val generated = file("../$owner/build/tmp/compileJava/$refmap")
            val ownerConfig = file("../$owner/build/resources/main/$config")
            inputs.file(ownerConfig)
            from(generated) { into("") }
            doFirst {
                check(generated.isFile && generated.length() > 0 && ownerConfig.isFile) {
                    "Missing fresh mounted owner mixin config/refmap: $ownerConfig / $generated"
                }
            }
        }
    }
    // Production TCon override makes cheap-part availability differ from upstream defaults.
    from(file(packRoot.resolve("data/tconstruct/tinkering/materials/stats/wood.json"))) {
        into("data/tconstruct/tinkering/materials/stats")
    }
    // Real pack inputs for the selected family, not synthetic fixture registries.
    val roots = when (nativeProfile) {
        "create", "create_bumblezone" -> listOf("create", "createsifter", "better_manufacturing")
        "create_powergrid" -> listOf("create", "createsifter", "powergrid", "better_manufacturing")
        "catalogue" -> listOf("create", "vs_clockwork", "complicated_bees", "sophisticatedstorage", "sophisticatedbackpacks", "better_manufacturing")
        "fluids" -> listOf("create", "better_industrial_heat", "better_manufacturing")
        "pneu" -> listOf("pneumaticcraft", "compressedcreativity", "better_manufacturing")
        else -> emptyList()
    }
    roots.forEach { name ->
        from(file(packRoot.resolve("data/$name/recipes"))) { into("data/$name/recipes") }
        from(file(packRoot.resolve("data/$name/loot_tables"))) { into("data/$name/loot_tables") }
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
            if (withCreate) mods { named(modId) { source(createFixture); if (nativeProfile != "core") source(wave2Fixture) } }
            if (nativeProfile == "fluids") {
                mods {
                    create("better_industrial_heat") { source(industrialHeatRuntime) }
                    create("better_chemlib_hazards") { source(hazardsRuntime) }
                    create("better_airtight_machines") { source(airtightRuntime) }
                }
                // Dev SourceSet mods have no owner JAR MixinConfigs manifest; register
                // all three real production owner configs for this profile only.
                fluidOwnerMixins.forEach { (_, config, _) -> args("--mixin.config", config) }
            }
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
    compileOnly("io.github.llamalad7:mixinextras-forge:0.5.4")
    if (withCreate) {
        runtimeOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-1.20.1:1.0.5"))
        runtimeOnly("io.github.llamalad7:mixinextras-forge:0.5.4")
    }
    compileOnly(fg.deobf("local:createsifter:1.20.1-1.8.6-6.0.6"))
    if (withCreate) runtimeOnly(fg.deobf("local:createsifter:1.20.1-1.8.6-6.0.6"))
    // Optional native owners are compile-only: the isolated Create fixture must NOT
    // start unrelated pack mods or turn them into hard dependencies at runtime.
    listOf(
        "local:appliedenergistics2-forge:15.4.10", "local:alexscaves:2.0.2", "local:Rats:1.20.1-8.1.3",
        "local:pneumaticcraft-repressurized:6.0.22+mc1.20.1", "local:compressedcreativity:1.20.1-0.2.0-all",
        "local:createdieselgenerators:1.20.1-1.3.12", "local:powergrid-mc1.20.1:0.5.4",
        "local:complicated_bees:1.20.1-3.4.6", "local:PrettyPipes:1.15.0-all",
        "local:sophisticatedstorage:1.20.1-1.4.39.1666", "local:sophisticatedcore:1.20.1-1.3.21.1676",
        "local:littlelogistics-mc1.20.1:v1.20.1.2", "local:trackwork:1.20.1-1.2.4",
        "local:clockwork:0.5.6", "local:eureka:1201-1.6.3",
        "local:valkyrienskies:120-2.4.11", "local:architectury:9.2.14-forge", "local:citadel:2.6.3-1.20.1"
    ).forEach { compileOnly(fg.deobf(it)) }
    compileOnly(files("../better-industrial-heat/build/classes/kotlin/main", "../better-industrial-heat/build/classes/java/main"))
    compileOnly(files("../better-rail-beetle/build/classes/java/main"))
    // Valkyrien Skies embeds this pinned API as a nested JAR; expose only its
    // extracted compile classes, never ship or stage it with Manufacturing.
    compileOnly(files(pinnedApis.map { it.file("valkyrienskies-api-1.1.0.jar") }))
    // Clockwork embeds Kelvin's node interfaces as another pinned nested JAR.
    compileOnly(files(pinnedApis.map { it.file("kelvin-forge-1.20.1-0.5.0.jar") }))
    if (nativeProfile != "core") {
        // KubeJS executes the selected real production scripts; JSON alone cannot prove effective roots.
        listOf("local:rhino-forge:2001.2.3-build.10", "local:architectury:9.2.14-forge",
            "local:kubejs-forge:2001.6.5-build.16", "local:kubejs-create-forge:2001.3.0-build.8")
            .forEach { runtimeOnly(fg.deobf(it)) }
    }
    // The pinned production Bumblezone JAR/cache are read-only. Only this required
    // userdev profile loads the derived official-mapped selector JAR, with every
    // original mixin/config and the positive harvester fixture retained.
    if (nativeProfile == "create_bumblezone") runtimeOnly(files(devBumblezoneJar))
    if (nativeProfile == "create_powergrid") runtimeOnly(fg.deobf("local:powergrid-mc1.20.1:0.5.4"))
    if (nativeProfile in setOf("pneu", "fluids")) {
        runtimeOnly(fg.deobf("local:pneumaticcraft-repressurized:6.0.22+mc1.20.1"))
    }
    if (nativeProfile == "fluids") {
        listOf("local:kotlinforforge:4.12.0-all", "local:chemlib:1.20.1-2.0.19",
            "local:ForgeEndertech:1.20.1-11.1.10.2-build.2294")
            .forEach { runtimeOnly(fg.deobf(it)) }
        runtimeOnly(fg.deobf(devAdpotherCoordinate))
    }
    if (nativeProfile == "pneu") runtimeOnly(fg.deobf("local:compressedcreativity:1.20.1-0.2.0-all"))
    if (nativeProfile == "catalogue") {
        listOf("local:clockwork:0.5.6", "local:valkyrienskies:120-2.4.11",
            "local:complicated_bees:1.20.1-3.4.6", "local:sophisticatedstorage:1.20.1-1.4.39.1666",
            "local:sophisticatedbackpacks:1.20.1-3.24.35.1675", "local:sophisticatedcore:1.20.1-1.3.21.1676",
            "local:architectury:9.2.14-forge").forEach { runtimeOnly(fg.deobf(it)) }
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
    // Only main outputs belong to the production JAR. In particular, the
    // fluids profile resolves a LOCAL dev-only AdPother mapping artifact and
    // mounts additional GameTest/owner SourceSets; never package from it.
    doFirst {
        check(nativeProfile == "core" && !clientFixtureEnabled) {
            "Production JAR requires the default core profile with no client fixture; refusing dev-only runtime inputs"
        }
    }
    // main/resources carries an empty GameTest template for userdev. The native
    // doFirst copies it into each fresh gameteststructures/ root; shipping it in
    // the production mod archive would leak a dev-only fixture to players.
    exclude("data/better_manufacturing/gametest/**")
    manifest.attributes("MixinConfigs" to "better_manufacturing.mixins.json")
    // MixinGradle also contributes this same AP output to the archive; retain one identical copy.
    filesMatching("better_manufacturing.refmap.json") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    finalizedBy("reobfJar")
}
tasks.register<Copy>("stageRuntimeJar") {
    doFirst {
        check(nativeProfile == "core" && !clientFixtureEnabled) {
            "Refusing to stage a production JAR from a fixture or derived-AdPother userdev profile"
        }
    }
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
val verifyImplementationLedger by tasks.registering(Exec::class) {
    group = "verification"
    description = "Source/owner-local completeness only; cannot promote full-pack acceptance."
    commandLine("python3", "-B", "scripts/implementation_coverage.py", "--pack", "../../better-content-modpack", "--workspace", "../..")
}
tasks.register("verifyImplementationComplete") {
    group = "verification"
    dependsOn("verifyFast", "verifyNative", verifyImplementationLedger)
}
tasks.register<Exec>("testBuildTools") {
    commandLine("python3", "-B", "-m", "unittest", "discover", "-s", "scripts/tests", "-v")
}
val verifyCreateFixture by tasks.registering(Exec::class) {
    val cache = providers.environmentVariable("BC_UPSTREAM_MOD_DIR").getOrElse(file("../../.cache/bc/packwiz-downloads/mods").absolutePath)
    doFirst { commandLine("python3", "-B", "scripts/create_fixture_identity.py", file("../../better-content-modpack/mods/create.pw.toml").absolutePath,
        createUpstream.singleFile.absolutePath, cache, layout.buildDirectory.file("create-fixture-identity.json").get().asFile.absolutePath) }
}
val verifySifterFixture by tasks.registering(Exec::class) {
    val cache = providers.environmentVariable("BC_UPSTREAM_MOD_DIR").getOrElse(file("../../.cache/bc/packwiz-downloads/mods").absolutePath)
    commandLine("python3", "-B", "scripts/sifter_fixture_identity.py", file("../../better-content-modpack/mods/create-sifting.pw.toml").absolutePath,
        cache, layout.buildDirectory.file("sifter-fixture-identity.json").get().asFile.absolutePath)
}
tasks.named("compileJava") { dependsOn(verifySifterFixture, extractEmbeddedApis, compileIndustrialHeatOwner, compileRailBeetleOwner) }
if (nativeProfile == "fluids") {
    tasks.named(wave2Fixture.compileJavaTaskName) { dependsOn(compileIndustrialHeatOwner, compileHazardsOwner, compileAirtightOwner) }
    // ForgeGradle registers this run preparation task lazily; configure it when present.
    tasks.matching { it.name == "prepareRunGameTestServer" }.configureEach { dependsOn(verifyDevAdpotherMapped) }
    tasks.matching { it.name == "runGameTestServer" }.configureEach {
        dependsOn(compileIndustrialHeatOwner, compileHazardsOwner, compileAirtightOwner, verifyDevAdpotherMapped)
    }
}
if (nativeProfile == "create_bumblezone") {
    tasks.matching { it.name == "prepareRunGameTestServer" }.configureEach { dependsOn(prepareDevBumblezoneUserdev) }
    tasks.matching { it.name == "runGameTestServer" }.configureEach { dependsOn(prepareDevBumblezoneUserdev) }
}
tasks.register("verifyFast") {
    group = "verification"
    dependsOn("check", "testBuildTools", verifyCoverage)
    if (withCreate) dependsOn(verifyCreateFixture, verifySifterFixture)
}
tasks.matching { it.name == "runGameTestServer" }.configureEach {
    if (withCreate) dependsOn(verifyCreateFixture)
    if (nativeProfile != "core") dependsOn(tasks.named(wave2Fixture.classesTaskName))
    doFirst {
        if (nativeProfile != "core") check(expectedNativeTestCount > 23) { "Positive $nativeProfile profile requires more than the 23 core tests (21 wave9 + two Wave2Core tests)" }
        val directory = nativeDirectory.get().asFile
        check(!directory.exists()) { "Refusing reused native fixture: $directory" }
        directory.mkdirs()
        if (withCreate) copy { from(layout.buildDirectory.file("create-fixture-identity.json")); from(layout.buildDirectory.file("sifter-fixture-identity.json")); into(directory) }
        if (nativeProfile != "core") {
            val scripts = listOf("startup_scripts/progression/10_machine_blocks.js",
                "startup_scripts/progression/20_transition_items.js",
                "server_scripts/utility/10_recipe_surface_helpers.js",
                "server_scripts/progression/20_powered_works.js") +
                (if (nativeProfile == "create_powergrid") listOf("server_scripts/progression/51_powergrid_cutting.js") else emptyList()) +
                (if (nativeProfile == "catalogue") listOf("server_scripts/progression/52_clockwork_blade_cutting.js") else emptyList()) +
                (if (nativeProfile == "pneu") listOf("server_scripts/progression/40_thermal_pressure.js",
                    "server_scripts/progression/81_pneumaticcraft_progression.js",
                    "server_scripts/progression/82_pneumaticcraft_acquisition.js") else emptyList()) +
                (if (nativeProfile in setOf("create_bumblezone", "pneu"))
                    listOf("server_scripts/progression/85_dimension_font_ecology.js") else emptyList())
            scripts.forEach { relative ->
                val input = packRoot.resolve(relative)
                check(input.isFile) { "Missing production KubeJS input: $input" }
                val destination = directory.resolve("kubejs/$relative")
                destination.parentFile.mkdirs()
                input.copyTo(destination)
            }
            directory.resolve("production-scripts.txt").writeText(scripts.joinToString("\n", postfix = "\n"))
            val dataRoots = when (nativeProfile) {
                "create", "create_bumblezone" -> listOf("create", "createsifter", "better_manufacturing")
                "create_powergrid" -> listOf("create", "createsifter", "powergrid", "better_manufacturing")
                "catalogue" -> listOf("create", "vs_clockwork", "complicated_bees", "sophisticatedstorage", "sophisticatedbackpacks", "better_manufacturing")
                "fluids" -> listOf("create", "better_industrial_heat", "better_manufacturing")
                else -> listOf("pneumaticcraft", "compressedcreativity", "better_manufacturing")
            }
            val packFiles = (dataRoots.flatMap { root ->
                listOf("recipes", "loot_tables").flatMap { kind ->
                    fileTree(packRoot.resolve("data/$root/$kind")).files.toList()
                }
            } + packRoot.resolve("data/tconstruct/tinkering/materials/stats/wood.json") +
                (if (nativeProfile == "catalogue") listOf(packRoot.resolve("data/minecraft/recipes/gyrofuge_panel.json")) else emptyList())).sortedBy { it.absolutePath }
            check(packFiles.all(File::isFile)) { "Missing production pack fixture data" }
            // KubeJS data is a real high-priority pack. Merely copying cross-mod
            // recipe JSON into Manufacturing dev-mod resources lets the native IH
            // recipe with the same ID win; mount the exact selected pack bytes here.
            packFiles.forEach { input ->
                val destination = directory.resolve("kubejs/${input.relativeTo(packRoot).invariantSeparatorsPath}")
                destination.parentFile.mkdirs()
                input.copyTo(destination)
                check(destination.readBytes().contentEquals(input.readBytes())) { "Pack input copy changed bytes: $input" }
            }
            directory.resolve("production-pack-inputs.txt").writeText(
                packFiles.joinToString("\n", postfix = "\n") { input ->
                    val hash = MessageDigest.getInstance("SHA-256").digest(input.readBytes())
                        .joinToString("") { "%02x".format(it) }
                    "${input.relativeTo(packRoot).invariantSeparatorsPath}\t$hash"
                })
        }
        directory.resolve("runtime-classpath.txt").writeText(configurations.runtimeClasspath.get().files.sortedBy { it.absolutePath }.joinToString("\n") { it.absolutePath })
        val outputs = sourceSets.main.get().output.files + (if (withCreate) createFixture.output.files else emptySet()) +
            (if (nativeProfile != "core") wave2Fixture.output.files else emptySet())
        directory.resolve("fixture-inputs.txt").writeText(outputs.sortedBy { it.absolutePath }.joinToString("\n") { it.absolutePath })
        if (nativeProfile == "fluids") {
            val provenance = devAdpotherRoot.get().asFile
            listOf("provenance.json", "mapped-provenance.txt").forEach { name ->
                val input = provenance.resolve(name)
                check(input.isFile) { "Missing verified dev AdPother provenance: $input" }
                input.copyTo(directory.resolve("dev-adpother-$name"))
            }
            val owners = listOf("better-industrial-heat", "better-chemlib-hazards", "better-airtight-machines")
            directory.resolve("native-owner-inputs.txt").writeText(owners.flatMap { owner ->
                listOf("classes/kotlin/main", "classes/java/main", "resources/main")
                    .map { file("../$owner/build/$it") }.filter(File::isDirectory)
            }.joinToString("\n", postfix = "\n") { it.absolutePath })
        }
        copy {
            from("src/main/resources/data/better_manufacturing/gametest/structures")
            if (withCreate) from("src/createFixture/resources/data/better_manufacturing/gametest/structures")
            if (nativeProfile != "core") {
                val families = when (nativeProfile) {
                    "create", "create_bumblezone" -> listOf("wave2CreateFixture")
                    "create_powergrid" -> listOf("wave2CreateFixture", "wave2PowergridFixture")
                    "fluids" -> listOf("wave2FluidsHeatFixture")
                    "catalogue" -> listOf("catalogueOptionalFixture")
                    else -> listOf("wave2PneuMechanicsFixture", "wave2PneuProcessFixture")
                }
                families.forEach { from("src/$it/resources/data/better_manufacturing/gametest/structures") }
            }
            into(directory.resolve("gameteststructures"))
        }
    }
    doLast {
        val log = nativeDirectory.get().asFile.resolve("logs/latest.log").readText()
        val complete = Regex("(\\d+) GAME TESTS COMPLETE").findAll(log).map { it.groupValues[1].toInt() }.toList()
        val passed = Regex("All (\\d+) required tests passed").findAll(log).map { it.groupValues[1].toInt() }.toList()
        check(complete == listOf(expectedNativeTestCount) && passed == listOf(expectedNativeTestCount)) { "Missing/incorrect native test population: complete=$complete passed=$passed" }
        check(log.contains("Game test server shutting down")) { "Native test server did not shut down normally" }
        exec { commandLine("python3", "-B", "scripts/native_receipt.py", projectDir.absolutePath, nativeDirectory.get().asFile.absolutePath, expectedNativeTestCount.toString()) }
    }
}
// Non-GameTest launches must never inherit the fluids-only derived AdPother
// dependency through runtimeClasspath. Keep the experimental profile intact
// for a future separately authorized GameTest, without exposing it as a
// normal client/server run or a production package.
tasks.matching { it.name == "runClient" || it.name == "runServer" }.configureEach {
    if (nativeProfile == "fluids") doFirst {
        error("The fluids dev-only AdPother mapping dependency is restricted to GameTest; use the default core profile for normal runs")
    }
}
tasks.matching { it.name == "runClient" }.configureEach {
    if (clientFixtureEnabled) {
        dependsOn(verifyCreateFixture)
        doFirst {
            val sourceWorld = providers.gradleProperty("clientFixtureWorld").orNull ?: error("Pass -PclientFixtureWorld=<successful native fixture directory>")
            exec { commandLine("python3", "-B", "scripts/prepare_client_fixture.py", projectDir.absolutePath, sourceWorld, clientDirectory.get().asFile.absolutePath) }
            val directory = clientDirectory.get().asFile
            copy { from(layout.buildDirectory.file("create-fixture-identity.json")); from(layout.buildDirectory.file("sifter-fixture-identity.json")); into(directory) }
            directory.resolve("runtime-classpath.txt").writeText(configurations.runtimeClasspath.get().files.sortedBy { it.absolutePath }.joinToString("\n") { it.absolutePath })
            // clientFixture deliberately has Java only, so no resource output directory exists.
            val outputs = sourceSets.main.get().output.files + createFixture.output.files + clientFixture.output.classesDirs.files
            directory.resolve("fixture-inputs.txt").writeText(outputs.sortedBy { it.absolutePath }.joinToString("\n") { it.absolutePath })
        }
        doLast {
            val evidence = clientDirectory.get().asFile.resolve("client-witness.json")
            check(evidence.isFile && evidence.readText().contains("\"status\": \"passed\"")) { "Client fixture failed or produced no witness: $evidence" }
            listOf("drill-native-recipe.png", "drill-installed-world.png", "drill-inventory.png", "mesh-native-recipe.png", "mesh-installed-world.png", "mesh-inventory.png").forEach { name ->
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
