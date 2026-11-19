@file:Suppress("UnstableApiUsage", "MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")

import java.util.*

plugins {
    id("mod-plugin")
    id("maven-publish")
    id("net.fabricmc.fabric-loom-remap")
    id("com.replaymod.preprocess")
}

version = fullProjectVersion
group = modMavenGroup

repositories {
    fun strictMaven(url: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) }
        filter {
            groups.forEach {
                includeGroupAndSubgroups(it)
                includeGroupAndSubgroups("$it.*")
            }
        }
    }
    strictMaven("https://maven.fabricmc.net")
    strictMaven("https://www.cursemaven.com", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "maven.modrinth")
    strictMaven("https://jitpack.io")
}

// https://github.com/FabricMC/fabric-loader/issues/783
configurations.all {
    resolutionStrategy {
        force("net.fabricmc:fabric-loader:$fabricLoaderVersion")
        force("maven.modrinth:malilib:${prop("malilib")}")
    }
    exclude(group = "com.github.sakura-ryoko", module = "malilib")
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("maven.modrinth:malilib:$malilib")
}

loom {
    runs {
        named("client") {
            client()
            runDirectory.set(file("../../run/client"))
            jvmArguments.add("-Dmixin.debug.export=true")
            jvmArguments.add("-Dmixin.debug.countInjections=false")
            jvmArguments.add("-Dmixin.env.remapRefMap=true")
            programArguments.addAll("--width", "1280")
            programArguments.addAll("--height", "720")
            programArguments.addAll("--username", "MaLiLibUITest")
            generateRunConfig.set(true)
        }
    }
}

tasks {
    register<Copy>("buildAndCollect") {
        group = "build"
        delete(project.layout.buildDirectory.dir("libs"))
        dependsOn("build")
        from(remapJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs"))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = modId
            version = modVersion
        }
    }
    repositories {
        mavenLocal()
        maven {
            url = uri("$rootDir/publish")
        }
    }
}
