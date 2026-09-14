plugins {
	java
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
	alias(libs.plugins.spotless)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "App Store search and details service"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

springBoot {
	buildInfo()
}

spotless {
	java {
		palantirJavaFormat(libs.versions.palantir.java.format.get())
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
}

dependencies {
	implementation(libs.boot.starter.actuator)
	implementation(libs.boot.starter.restclient)
	implementation(libs.boot.starter.validation)
	implementation(libs.boot.starter.webmvc)
	implementation(libs.caffeine)
	implementation(libs.micrometer.context.propagation)
	implementation(libs.springdoc.webmvc.ui)
	runtimeOnly(libs.micrometer.registry.prometheus)
	testImplementation(libs.boot.test.actuator)
	testImplementation(libs.boot.test.restclient)
	testImplementation(libs.boot.test.validation)
	testImplementation(libs.boot.test.webmvc)
	testImplementation(libs.wiremock.spring.boot)
	testImplementation(libs.archunit.junit6)
	testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
	useJUnitPlatform()
}
