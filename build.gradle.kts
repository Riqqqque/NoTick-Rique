plugins {
	id("toni.blahaj")
}

blahaj {
	config {

	}
	setup {
		forgeConfig()

		if (mod.isForge) {
			deps.compileOnly(deps.annotationProcessor("io.github.llamalad7:mixinextras-common:0.4.1")!!)
			deps.implementation(deps.include("io.github.llamalad7:mixinextras-forge:0.4.1")!!)
		}
	}
}

afterEvaluate {
	val setupChiseledBuild = tasks.findByName("setupChiseledBuild") ?: return@afterEvaluate
	val resourceExcludes = when {
		name.endsWith("-fabric") -> listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml")
		name.endsWith("-neoforge") -> listOf("fabric.mod.json", "META-INF/mods.toml")
		name.endsWith("-forge") -> listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")
		else -> emptyList()
	}
	val syncRootUploadJars = if (rootProject.tasks.names.contains("syncRootUploadJars")) {
		rootProject.tasks.named("syncRootUploadJars", org.gradle.api.tasks.Copy::class.java)
	} else {
		rootProject.tasks.register("syncRootUploadJars", org.gradle.api.tasks.Copy::class.java) {
			into(rootProject.layout.buildDirectory.dir("libs"))
			doFirst {
				delete(
					rootProject.fileTree(rootProject.layout.buildDirectory.dir("libs")) {
						include("no_ticks-*.jar")
					}
				)
			}
		}
	}
	val syncChiseledJava = tasks.register<org.gradle.api.tasks.Sync>("syncChiseledJava") {
		dependsOn(setupChiseledBuild)
		from(layout.buildDirectory.dir("chiseledSrc/main/java"))
		into(layout.buildDirectory.dir("generated/chiseledSrc/main/java"))
	}
	val syncChiseledResources = tasks.register<org.gradle.api.tasks.Sync>("syncChiseledResources") {
		dependsOn(setupChiseledBuild)
		from(layout.buildDirectory.dir("chiseledSrc/main/resources")) {
			exclude(resourceExcludes)
		}
		into(layout.buildDirectory.dir("generated/chiseledSrc/main/resources"))
	}
	syncRootUploadJars.configure {
		from(layout.buildDirectory.dir("libs")) {
			include("no_ticks-*.jar")
			exclude("*-sources.jar")
		}
	}
	tasks.findByName("build")?.let { syncRootUploadJars.configure { dependsOn(it) } }
	@Suppress("UNCHECKED_CAST")
	(extensions.findByName("sourceSets") as? org.gradle.api.tasks.SourceSetContainer)
		?.findByName("main")
		?.apply {
			java.setSrcDirs(emptyList<Any>())
			java.srcDir(syncChiseledJava)
			resources.setSrcDirs(emptyList<Any>())
			resources.srcDir(syncChiseledResources)
		}
	listOf(
		"jar",
		"sourcesJar",
		"build",
		"remapJar",
		"remapSourcesJar"
	).forEach { taskName ->
		tasks.findByName(taskName)?.dependsOn(setupChiseledBuild)
	}
	tasks.findByName("compileJava")?.dependsOn(syncChiseledJava)
	tasks.findByName("processResources")?.dependsOn(syncChiseledResources)
	tasks.withType(org.gradle.language.jvm.tasks.ProcessResources::class.java).configureEach {
		exclude(resourceExcludes)
	}
	tasks.findByName("sourcesJar")?.dependsOn(syncChiseledJava, syncChiseledResources)
	tasks.findByName("build")?.finalizedBy(syncRootUploadJars)
}
