plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.7"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("com.github.docker-java:docker-java:3.5.1")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.5.1")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.example.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("ContainerNursery")
    archiveClassifier.set("all")
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }
    from(project.configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
