pluginManagement {
	repositories {
		maven("https://maven.axle.coffee/releases")
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/")
		maven("https://maven.architectury.dev")
		maven("https://maven.minecraftforge.net")
		maven("https://maven.neoforged.net/releases/")
		maven("https://maven.kikugie.dev/snapshots")
		maven("https://maven.kikugie.dev/releases")
	}
}

plugins {
	id("dev.architectury.loom") version "1.14.473" apply false
	kotlin("jvm") version "2.3.10" apply false
	kotlin("plugin.serialization") version "2.3.10" apply false
	id("coffee.axle.blahaj") version "3.0.1"
	id("dev.kikugie.stonecutter") version "0.9.1"
}

blahaj {
	init(rootProject) {
		mc("1.20.1", "fabric", "forge")
		mc("1.21.1", "fabric", "neoforge")
	}
}

rootProject.name = settings.extra["mod.name"] as String
