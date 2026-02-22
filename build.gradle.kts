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
	listOf(
		"compileJava",
		"processResources",
		"classes",
		"jar",
		"sourcesJar",
		"build",
		"remapJar",
		"remapSourcesJar"
	).forEach { taskName ->
		tasks.findByName(taskName)?.dependsOn(setupChiseledBuild)
	}
}
