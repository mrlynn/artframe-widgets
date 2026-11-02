// Runs the TV's own widget parser (vendored in src/main/kotlin/vendor) on a desktop JVM,
// so a widget this repo accepts is one the TV will load.
plugins {
    kotlin("jvm") version "2.0.21"
    application
}

kotlin { jvmToolchain(17) }

application { mainClass.set("CheckKt") }
