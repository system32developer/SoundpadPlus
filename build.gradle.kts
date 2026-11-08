plugins {
    kotlin("jvm") version "2.4.10"
    application
}

// Bump on every release: Windows only upgrades to a higher version, and the in-app updater compares it with the GitHub release tag.
val appVersion = "1.0.8"

application {
    mainClass.set("com.emprecio.soundpadplus.MainKt")
}

group = "com.emprecio"
version = appVersion

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("javazoom:jlayer:1.0.1") // mp3 preview
    testImplementation(kotlin("test"))
}

tasks.processResources {
    inputs.property("version", appVersion)
    filesMatching("version.properties") { expand("version" to appVersion) }
}

tasks.named<JavaExec>("run") {
    systemProperty("soundpadplus.home", layout.projectDirectory.dir("run-data").asFile.absolutePath)
}

// jpackage bundles its own trimmed JRE. "exe" needs WiX 3 installed; "app-image" does not.
// The installer is per-user (%LOCALAPPDATA%) so the app can write its settings, plugins and tools next to the exe without admin rights.
fun jpackageTask(name: String, type: String) = tasks.register<Exec>(name) {
    dependsOn("installDist")
    val libs = layout.buildDirectory.dir("install/${project.name}/lib").get().asFile
    val dest = layout.buildDirectory.dir("dist").get().asFile
    // portable WiX 3 (no admin needed): unzip wix314-binaries.zip into %LOCALAPPDATA%\wix3
    System.getenv("LOCALAPPDATA")?.let { environment("PATH", "$it\\wix3;" + System.getenv("PATH")) }
    doFirst { File(dest, if (type == "exe") "SoundpadPlus-$appVersion.exe" else "SoundpadPlus").deleteRecursively() }
    commandLine(
        "jpackage", "--type", type, "--name", "SoundpadPlus", "--app-version", appVersion,
        "--input", libs, "--main-jar", "${project.name}-$version.jar", "--main-class", "com.emprecio.soundpadplus.MainKt",
        "--add-modules", "java.desktop,java.logging,java.naming,java.management,java.net.http,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported",
        "--jlink-options", "--strip-native-commands --strip-debug --no-man-pages --no-header-files --compress zip-9",
        "--dest", dest
    ).also {
        if (type == "exe") args(
            "--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-per-user-install",
            "--win-upgrade-uuid", "6f1d2c1e-8a3b-4c57-9d0e-5b7a2f4e9c10"
        )
    }
}
jpackageTask("packageApp", "app-image")
jpackageTask("packageInstaller", "exe")

kotlin {
    jvmToolchain(21)
}

tasks.test {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.deleteRecursively() }
    systemProperty("soundpadplus.home", testHome.path)
    dependsOn(":examples:example-source:jar")
    useJUnitPlatform()
}
