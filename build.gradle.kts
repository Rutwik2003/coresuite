plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta4"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

// Group and version from gradle.properties
group = project.property("group") as String
version = project.property("version") as String

// Compile the release against the oldest supported Paper API.
val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("1.21-R0.1-SNAPSHOT")
val apiJavaVersion = if (paperApiVersion.get().startsWith("26.")) 25 else 21

// Keep release bytecode compatible with Java 21.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(apiJavaVersion))
    }
}

// Repositories for dependencies
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.luckperms.net/repository/maven-public/")
}

configurations.configureEach {
    if (isCanBeResolved) {
        attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, apiJavaVersion)
    }
}

dependencies {
    // Core Paper API
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    testImplementation("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    
    // Soft dependencies (transitive = false: these are server plugins, we only need compile-time API)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("net.citizensnpcs:citizens-main:2.0.35-SNAPSHOT") { isTransitive = false }
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.4.2") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.11.6") { isTransitive = false }
    compileOnly("net.luckperms:api:5.4")
    
    // Implementations to be shadowed
    implementation("com.zaxxer:HikariCP:6.2.1")
    testImplementation("com.zaxxer:HikariCP:6.2.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.47.1.0")
}

val smokeSourceSet = sourceSets.create("smoke") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
}
tasks.register<Jar>("smokeJar") {
    group = "verification"
    description = "Builds the test-only Paper compatibility probe; never included in the release JAR."
    dependsOn(tasks.named("smokeClasses"))
    from(smokeSourceSet.output)
    archiveFileName.set("CoreSuite-SmokeProbe.jar")
}

tasks {
    register("writeCompatibilityClasspath") {
        doLast {
            val output = layout.buildDirectory.file("compatibility/${paperApiVersion.get()}.classpath").get().asFile
            output.parentFile.mkdirs()
            output.writeText(configurations.compileClasspath.get().files.joinToString("\n") { it.absolutePath })
        }
    }

    test {
        javaLauncher.set(project.extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(apiJavaVersion))
        })
        useJUnitPlatform()
    }

    register<JavaExec>("e2eTest") {
        group = "verification"
        description = "Runs the existing boss regression suite using the declared test classpath."
        dependsOn(testClasses)
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("net.rocky.coresuite.test.E2ETestRunner")
        javaLauncher.set(project.extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(apiJavaVersion))
        })
    }

    named("check") {
        dependsOn("e2eTest")
    }

    // Set encoding to UTF-8
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    // Process resources task to expand version in plugin.yml
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    // ShadowJar configuration
    shadowJar {
        // Relocate HikariCP to avoid classloader conflicts with other plugins
        relocate("com.zaxxer.hikari", "net.rocky.coresuite.libs.hikari")
        
        archiveFileName.set("CoreSuite-${project.version}.jar")
    }

    // Ensure build depends on shadowJar
    build {
        dependsOn(shadowJar)
    }

    // Configure test server
    runServer {
        minecraftVersion("1.21.1")
    }
}
