plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.spring") version "2.1.10"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
    application
}

ktlint {
    version.set("1.6.0")
}

group = "jyk"
version = "0.0.1"

val springCloudAwsVersion = "3.3.0"
val awsSdkVersion = "2.31.63"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Base
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // AWS
    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:$springCloudAwsVersion"))
    implementation("io.awspring.cloud:spring-cloud-aws-starter-secrets-manager")
    implementation("software.amazon.awssdk:bom:$awsSdkVersion")
    implementation("software.amazon.awssdk:netty-nio-client:$awsSdkVersion")
    implementation("software.amazon.awssdk:batch:$awsSdkVersion")
    // Google
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
    implementation("com.google.api-client:google-api-client:2.6.0")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20230815-2.0.0")
    implementation("com.google.http-client:google-http-client-jackson2:1.45.0")
    // Playwright
    implementation("com.microsoft.playwright:playwright:1.50.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers:1.20.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform {}
    systemProperty("spring.profiles.active", "test")
}

tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessTestAot> {
    jvmArgs("-Dspring.profiles.active=test")
}

tasks.register<JavaExec>("playwrightInstall") {
    group = "playwright"
    description = "Install Playwright browsers for local execution."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install")
}

tasks.jar {
    enabled = false
}
