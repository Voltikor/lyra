plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

val releaseVersion = project.property("mod.version") as String

tasks.register("buildAndCollect") {
    group = "project"
    description = "Builds and collects all supported Lyra versions."
    dependsOn(":1.21.11:buildAndCollect", ":26.1.2:buildAndCollect")
}

tasks.register("build") {
    group = "build"
    description = "Builds and collects all supported Lyra versions."
    dependsOn("buildAndCollect", "sourceBundle")
}

tasks.register<Zip>("sourceBundle") {
    group = "distribution"
    description = "Packages the complete corresponding source for a GPL release."
    archiveFileName.set("lyra-$releaseVersion-source.zip")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
    from(rootProject.projectDir) {
        include(
            ".gitattributes",
            ".github/**",
            ".gitignore",
            "LICENSE",
            "NOTICE",
            "README.md",
            "build.gradle.kts",
            "gradle/**",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "settings.gradle.kts",
            "src/**",
            "stonecutter.gradle.kts",
            "stonecutter.properties.toml"
        )
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register<Delete>("clean") {
    group = "build"
    description = "Cleans root and supported version build outputs."
    delete(rootProject.layout.buildDirectory)
    dependsOn(":1.21.11:clean", ":26.1.2:clean")
}

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    swaps["java"] = if (current.parsed >= "26.1") "\"25\";" else "\"21\";"

    replacements {
        string(current.parsed < "26.1") {
            replace("net.fabricmc.fabric.api.client.keymapping", "net.fabricmc.fabric.api.client.keybinding")
            replace("KeyMappingHelper", "KeyBindingHelper")
            replace("registerKeyMapping", "registerKeyBinding")
            replace("ClientCommands", "ClientCommandManager")
            replace("GuiGraphicsExtractor", "GuiGraphics")
            replace("extractRenderState(", "render(")
            replace("extractWidgetRenderState(", "renderWidget(")
            replace("extractContents(", "renderContents(")
            replace(".text(", ".drawString(")
            replace(".item(", ".renderItem(")
            replace("method = {\"extractRenderState\"}", "method = {\"render\"}")
        }
    }
}
