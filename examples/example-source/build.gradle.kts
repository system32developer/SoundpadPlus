plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project(":"))
}

kotlin {
    jvmToolchain(21)
}
