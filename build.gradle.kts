plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta4"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

// Group and version from gradle.properties
group = project.property("group") as String
version = project.property("version") as String

// Configure Java toolchain to use Java 21
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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

dependencies {
    // Core Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    
    // Soft dependencies (transitive = false: these are server plugins, we only need compile-time API)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("net.citizensnpcs:citizens-main:2.0.35-SNAPSHOT") { isTransitive = false }
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.4.2") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.11.6") { isTransitive = false }
    compileOnly("net.luckperms:api:5.4")
    
    // Implementations to be shadowed
    implementation("com.zaxxer:HikariCP:6.2.1")
}

tasks {
    // Set encoding to UTF-8
    withType<JavaCompile> {
        options.encoding = "UTF-8"
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
