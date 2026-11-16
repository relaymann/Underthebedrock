plugins { java }

group = "dev.underthebedrock"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.5.1")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    from("bettermodel/models") {
        include("*.bbmodel")
        into("under-the-bedrock/models")
    }
}

tasks.test { useJUnitPlatform() }
tasks.jar { archiveBaseName.set("UnderTheBedrock") }
