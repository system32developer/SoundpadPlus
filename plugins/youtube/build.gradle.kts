// Bundled inside the app (see the root build) and copied to the plugins folder as YouTube.jar.
plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // The app's own classes, not its jar: the root build packs this jar into the app, so depending on that jar would be a cycle.
    compileOnly(files(rootProject.layout.buildDirectory.dir("classes/kotlin/main")))
}

tasks.compileKotlin {
    dependsOn(":compileKotlin")
}

tasks.jar {
    archiveFileName.set("YouTube.jar")
}

kotlin {
    jvmToolchain(21)
}
