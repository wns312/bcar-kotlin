plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.graalvm.buildtools.native")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":infrastructure"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Application context tests replace these external boundaries with mocks.
    testImplementation(platform("software.amazon.awssdk:bom:2.31.63"))
    testImplementation("software.amazon.awssdk:auth")
    testImplementation("software.amazon.awssdk:regions")
    testImplementation("com.google.apis:google-api-services-sheets:v4-rev20230815-2.0.0")
}

tasks.withType<Test>().configureEach {
    systemProperty("spring.profiles.active", "test")
}

tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessTestAot>().configureEach {
    jvmArgs("-Dspring.profiles.active=test")
}

tasks.register<JavaExec>("playwrightInstall") {
    group = "playwright"
    description = "Install Playwright browsers for local execution."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install")
}

tasks.bootJar {
    archiveBaseName.set("bcar")
}

tasks.jar {
    enabled = false
}
