plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.0"))
    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:3.3.0"))
    implementation(platform("software.amazon.awssdk:bom:2.31.63"))
    implementation(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-secrets-manager")
    implementation("software.amazon.awssdk:batch")
    implementation("software.amazon.awssdk:dynamodb")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
    implementation("com.google.api-client:google-api-client:2.6.0")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20230815-2.0.0")
    implementation("com.google.http-client:google-http-client-jackson2:1.45.0")
    implementation("com.microsoft.playwright:playwright:1.50.0")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jsoup:jsoup:1.21.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
