plugins {
    kotlin("jvm") version "1.9.22"
    application
    // Shadow plugin disabled for test execution in this environment
    // id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation("org.eclipse.jetty:jetty-server:11.0.17")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.17")
    implementation("commons-cli:commons-cli:1.6.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}

application {
    mainClass.set("org.example.ContainerNurseryServer")
}

tasks.test {
    useJUnitPlatform()
}

// ShadowJar packaging is not required for tests
