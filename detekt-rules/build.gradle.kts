// Standalone pure-JVM Kotlin module holding Pantry Tracker's custom detekt
// rules (SR-78). Kept OUT of the Android :app module on purpose: detekt loads
// rule sets off the `detektPlugins` configuration as a normal JVM artifact via
// ServiceLoader. Compiling the rules into :app's unit-test source set and
// pointing detektPlugins at AGP's internal intermediates dirs (the pre-SR-78
// wiring) was both fragile and INERT — detekt never actually fired the rule.
// A real Gradle module wired with `detektPlugins(project(":detekt-rules"))`
// produces a proper jar (classes + META-INF/services) that detekt loads.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // detekt's rule API is compile-only: it is provided by the detekt runtime
    // that loads this jar, not bundled into it.
    compileOnly(libs.detekt.api)

    // Rule unit-testing harness — lint() / compileAndLintWithContext().
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit)
    // detekt-test pulls in assertj transitively (assertThat); declare it
    // explicitly so the assertion DSL is on the test compile classpath.
    testImplementation(kotlin("test"))
}

// detekt-test runs on JUnit 4 (Platform launcher). The :app module already uses
// JUnit 4; keep this module consistent.
tasks.withType<Test>().configureEach {
    useJUnit()
}
