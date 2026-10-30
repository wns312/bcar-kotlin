import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.spring") version "2.1.10" apply false
    id("org.springframework.boot") version "3.5.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.graalvm.buildtools.native") version "0.10.6" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0" apply false
    base
}

allprojects {
    group = "jyk"
    version = "0.0.1"
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.6.0")
    }
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.12.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
        "testImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// Keep the documented root commands while the executable artifact belongs to app.
tasks.register("bootJar") {
    dependsOn(":app:bootJar")
}
tasks.register("playwrightInstall") {
    dependsOn(":app:playwrightInstall")
}
tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}
tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}
tasks.named("clean") {
    dependsOn(subprojects.map { "${it.path}:clean" })
}
