import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
//	kotlin("jvm") version "2.1.0"
	id("org.springframework.boot") version "3.4.0"
	id("io.spring.dependency-management") version "1.1.6"
	kotlin("jvm") version "1.9.22"
	kotlin("plugin.spring") version "1.9.22"
	kotlin("plugin.serialization") version "1.9.22"
//	kotlin("plugin.spring") version "2.1.0"
//	kotlin("plugin.serialization") version "2.1.0"
}

repositories {
	gradlePluginPortal()
	mavenCentral()
	google()
}


group = "com.muditsahni"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}



dependencies {
	// Spring Boot core dependencies
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:org.springframework.boot.gradle.plugin:3.4.0")
	implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.7")

	// Server-Sent Events specific
	implementation("org.springframework.boot:spring-boot-starter-integration")
	implementation("org.springframework.integration:spring-integration-webflux")
	implementation("io.projectreactor:reactor-core")

	// Kotlin specific dependencies
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("com.fasterxml.jackson.module:jackson-module-jsonSchema")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.security:spring-security-oauth2-jose")
	implementation(platform("com.google.cloud:libraries-bom:26.52.0"))
	// Google Cloud dependencies - no versions needed
	implementation("com.google.cloud:google-cloud-storage")
	implementation("com.google.cloud:google-cloud-tasks")
	implementation("com.google.cloud:google-cloud-firestore")
	implementation("com.google.cloud:google-cloud-secretmanager")
	implementation("com.google.cloud:google-cloud-pubsub")
	implementation("com.google.cloud:google-cloud-logging")

	// Firebase Admin is not part of the BOM, so keep its version
	implementation("com.google.firebase:firebase-admin:9.2.0")

	// HTTP Client for API calls (OpenAI, Anthropic)
	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
	implementation("com.squareup.retrofit2:retrofit:2.11.0")
	implementation("com.squareup.retrofit2:converter-jackson:2.11.0")

	// Concurrent and reactive programming
	implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

	// Logging
	implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
	// Make sure you also have a logging implementation:
	implementation("ch.qos.logback:logback-classic:1.4.11")

	// Swagger/OpenAPI
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
	implementation("org.springdoc:springdoc-openapi-starter-common:2.3.0")
	runtimeOnly("org.springdoc:springdoc-openapi-kotlin:1.8.0")

	// Security
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("io.jsonwebtoken:jjwt-api:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

	// Configuration processor
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// Testing dependencies
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
	testImplementation("io.mockk:mockk:1.13.13")


}

tasks.withType<KotlinCompile> {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_21)
		freeCompilerArgs.add("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
