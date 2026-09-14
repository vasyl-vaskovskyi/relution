plugins {
	// Lets the Java 25 toolchain be downloaded on machines without a local JDK 25
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "appstore"
