import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

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
	val minecraftVersion = name.substringBeforeLast("-")
	val loader = name.substringAfterLast("-")
	val modId = (findProperty("mod.id") as String?) ?: "no_ticks"
	val artifactBaseName = "${rootProject.name}-$loader"
	val releaseVersion = findProperty("mod.version") as String
	val preservedModernJarNames = setOf(
		"NoTick-neoforge-$releaseVersion-26.1.2.jar",
		"NoTick-neoforge-$releaseVersion-26.2.jar"
	)
	val resourcePackFormat = when (minecraftVersion) {
		"1.20.1" -> 15
		"1.21.1" -> 34
		else -> 15
	}
	val neoForgeVersionLine = if (loader == "neoforge") {
		val versionParts = minecraftVersion.split(".")
		val neoForgeLine = if (versionParts.size >= 3) {
			"[${versionParts[1]}.${versionParts[2]},)"
		} else {
			"[${minecraftVersion.removePrefix("1.")},)"
		}
		"""[[dependencies."$modId"]]
modId="neoforge"
mandatory=true
versionRange="$neoForgeLine"
ordering="NONE"
side="BOTH"

"""
	} else {
		""
	}
	tasks.withType(org.gradle.api.tasks.bundling.AbstractArchiveTask::class.java).configureEach {
		archiveBaseName.set(artifactBaseName)
	}
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
				val staleUploadJars = rootProject.fileTree(rootProject.layout.buildDirectory.dir("libs")) {
					include("NoTick-*.jar")
					include("no_ticks-*.jar")
				}.files.filterNot { it.name in preservedModernJarNames }
				delete(staleUploadJars)
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
			include("NoTick-*.jar")
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
		doLast {
			val resourcesDir = destinationDir
			val fabricModJson = resourcesDir.resolve("fabric.mod.json")
			if (fabricModJson.exists()) {
				fabricModJson.writeText(
					fabricModJson.readText().replace(
						Regex("""("minecraft"\s*:\s*")[^"]+(")"""),
						"""$1$minecraftVersion$2"""
					)
				)
			}

			val packMcmeta = resourcesDir.resolve("pack.mcmeta")
			if (packMcmeta.exists()) {
				packMcmeta.writeText(
					packMcmeta.readText().replace(
						Regex(""""pack_format"\s*:\s*\d+"""),
						""""pack_format": $resourcePackFormat"""
					)
				)
			}

			val tomlPattern = Regex(
				"""(\[\[dependencies\."[^"]+"\]\]\s*modId="minecraft"\s*mandatory=true\s*versionRange=")[^"]+(")""",
				setOf(RegexOption.DOT_MATCHES_ALL)
			)
			listOf(
				resourcesDir.resolve("META-INF/mods.toml"),
				resourcesDir.resolve("META-INF/neoforge.mods.toml")
			).forEach { descriptor ->
				if (!descriptor.exists()) return@forEach

				var descriptorText = descriptor.readText().replace(
					tomlPattern,
					"""$1[$minecraftVersion]$2"""
				)

				if (descriptor.name == "neoforge.mods.toml" && neoForgeVersionLine.isNotEmpty() && !descriptorText.contains("""modId="neoforge"""")) {
					descriptorText = descriptorText.replaceFirst(
						Regex("""(?=\[\[dependencies\."[^"]+"\]\]\s*modId="minecraft")"""),
						Regex.escapeReplacement(neoForgeVersionLine)
					)
				}

				descriptor.writeText(descriptorText)
			}
		}
	}
	tasks.findByName("sourcesJar")?.dependsOn(syncChiseledJava, syncChiseledResources)
	tasks.findByName("build")?.finalizedBy(syncRootUploadJars)

	val remapJarTask = tasks.named("remapJar", org.gradle.api.tasks.bundling.AbstractArchiveTask::class.java)
	val verifyReleaseJar = tasks.register("verifyReleaseJar") {
		dependsOn(remapJarTask)
		inputs.file(remapJarTask.flatMap { it.archiveFile })

		doLast {
			val jarFile = remapJarTask.get().archiveFile.get().asFile
			if (!jarFile.isFile) {
				throw GradleException("Release jar was not created: ${jarFile.absolutePath}")
			}

			ZipFile(jarFile).use { zip: ZipFile ->
				val descriptor = when (loader) {
					"fabric" -> "fabric.mod.json"
					"forge" -> "META-INF/mods.toml"
					"neoforge" -> "META-INF/neoforge.mods.toml"
					else -> throw GradleException("Unsupported loader $loader")
				}
				val requiredEntries = mutableListOf(
					descriptor,
					"mixins.no_ticks.json",
					"rique/notick/NoTick.class",
					"assets/notick/textures/mod_logo.png",
					"assets/notick/lang/en_us.json"
				)
				if (loader == "fabric") requiredEntries += "no_ticks.accesswidener"

				requiredEntries.forEach { entry ->
					if (zip.getEntry(entry) == null) {
						throw GradleException("${jarFile.name} is missing $entry")
					}
				}

				val forbiddenEntries = listOf(
					"fabric.mod.json",
					"META-INF/mods.toml",
					"META-INF/neoforge.mods.toml"
				).filterNot { it == descriptor }.toMutableList()
				if (loader != "fabric") forbiddenEntries += "no_ticks.accesswidener"
				forbiddenEntries.forEach { entry ->
					if (zip.getEntry(entry) != null) {
						throw GradleException("${jarFile.name} contains metadata for another loader: $entry")
					}
				}

				val metadata = zip.getInputStream(zip.getEntry(descriptor)).bufferedReader(StandardCharsets.UTF_8).use { reader -> reader.readText() }
				val expectedMetadata = if (loader == "fabric") {
					listOf(
						"\"id\": \"$modId\"",
						"\"version\": \"$releaseVersion\"",
						"\"minecraft\": \"$minecraftVersion\""
					)
				} else {
					buildList {
						add("modId=\"$modId\"")
						add("version=\"$releaseVersion\"")
						add("versionRange=\"[$minecraftVersion]\"")
						if (loader == "neoforge") add("modId=\"neoforge\"")
					}
				}
				expectedMetadata.forEach { expected ->
					if (!metadata.contains(expected)) {
						throw GradleException("${jarFile.name} metadata is missing $expected")
					}
				}
			}
		}
	}

	tasks.findByName("check")?.dependsOn(verifyReleaseJar)
}
