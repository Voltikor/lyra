plugins {
    id("dev.kikugie.loom-back-compat")
}

val modId = property("mod.id") as String
val modName = property("mod.name") as String
val archiveName = property("mod.archive_name") as String
val modVersion = property("mod.version") as String
val fabricLoaderVersion = property("deps.fabric_loader") as String
val fabricApiVersion = property("deps.fabric_api") as String
val minecraftCompatibility = sc.properties["mod.mc_compat"] as String

version = "$modVersion+${sc.current.version}"
base.archivesName = archiveName

val requiredJava = JavaVersion.toVersion(sc.properties["mod.java"] as String)
val javaMajor = requiredJava.majorVersion.toInt()

repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.0")
    minecraft("com.mojang:minecraft:${sc.current.version}")
    if (sc.current.parsed >= "26.1") {
        implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
        implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
        compileOnly("maven.modrinth:modmenu:18.0.0")
        runtimeOnly("maven.modrinth:modmenu:18.0.0")
    } else {
        loomx.applyMojangMappings()
        modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
        modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
        modCompileOnly("maven.modrinth:modmenu:17.0.0")
        modRuntimeOnly("maven.modrinth:modmenu:17.0.0")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run/${sc.current.version}")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain.languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaMajor
    options.encoding = "UTF-8"
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_lyra" } }
    from(rootProject.file("NOTICE")) { rename { "NOTICE_lyra" } }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    val props = mapOf(
        "id" to modId,
        "name" to modName,
        "version" to project.version,
        "minecraft" to minecraftCompatibility,
        "fabric_api" to ">=$fabricApiVersion",
        "java" to javaMajor
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("*.mixins.json") { expand("java" to "JAVA_$javaMajor") }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds this version's mod and source JARs into the root build/libs directory."
    dependsOn(tasks.named("build"))
    // loom-back-compat selects remapJar for obfuscated nodes and jar for 26.1+.
    from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}

tasks.named("build") {
    finalizedBy("buildAndCollect")
}
