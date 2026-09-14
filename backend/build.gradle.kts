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

// Apple drift detection (ADR-0037): real Apple calls, run by the nightly apple-drift workflow and never by check
val liveTest = sourceSets.create("liveTest") {
	compileClasspath += sourceSets.main.get().output
	runtimeClasspath += sourceSets.main.get().output
}

configurations[liveTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[liveTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("liveTest") {
	description = "Runs the live Apple drift tests (at most 3 real Apple calls)."
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	testClassesDirs = liveTest.output.classesDirs
	classpath = liveTest.runtimeClasspath
	useJUnitPlatform {
		includeTags("live")
	}
	// the result depends on Apple, not on our inputs
	outputs.upToDateWhen { false }
}

// check compiles the live tests without running them, so a refactoring can't silently break the nightly job
tasks.check {
	dependsOn(tasks.named(liveTest.classesTaskName))
}
