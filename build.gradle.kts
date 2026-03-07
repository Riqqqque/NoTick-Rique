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
	val syncRootUploadJars = (rootProject.tasks.findByName("syncRootUploadJars") as? org.gradle.api.tasks.Copy)
		?: rootProject.tasks.create("syncRootUploadJars", org.gradle.api.tasks.Copy::class.java).apply {
			into(rootProject.layout.buildDirectory.dir("libs"))
			doFirst {
				delete(
					rootProject.fileTree(rootProject.layout.buildDirectory.dir("libs")) {
						include("no_ticks-*.jar")
					}
				)
			}
		}
	val syncChiseledJava = tasks.register<org.gradle.api.tasks.Sync>("syncChiseledJava") {
		dependsOn(setupChiseledBuild)
		from(layout.buildDirectory.dir("chiseledSrc/main/java"))
		into(layout.buildDirectory.dir("generated/chiseledSrc/main/java"))
	}
	val syncChiseledResources = tasks.register<org.gradle.api.tasks.Sync>("syncChiseledResources") {
		dependsOn(setupChiseledBuild)
		from(layout.buildDirectory.dir("chiseledSrc/main/resources"))
		into(layout.buildDirectory.dir("generated/chiseledSrc/main/resources"))
	}
	syncRootUploadJars.from(layout.buildDirectory.dir("libs")) {
		include("no_ticks-*.jar")
		exclude("*-sources.jar")
	}
	tasks.findByName("build")?.let { syncRootUploadJars.dependsOn(it) }
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
	tasks.findByName("sourcesJar")?.dependsOn(syncChiseledJava, syncChiseledResources)
	tasks.findByName("build")?.finalizedBy(syncRootUploadJars)
}
