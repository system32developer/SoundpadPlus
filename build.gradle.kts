plugins {
    kotlin("jvm") version "2.4.10"
    application
}

// Bump on every release: Windows only upgrades to a higher version, and the in-app updater compares it with the GitHub release tag.
val appVersion = "1.0.10"

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

// The YouTube plugin jar travels inside the app and is copied to the plugins folder on start (PluginLoader.installBundled).
val bundledPlugins = tasks.register<Copy>("bundledPlugins") {
    dependsOn(":plugins:youtube:jar")
    from(project(":plugins:youtube").layout.buildDirectory.dir("libs")) { include("YouTube.jar") }
    into(layout.buildDirectory.dir("generated/bundled/plugins"))
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated/bundled")) }

tasks.processResources {
    dependsOn(bundledPlugins)
    inputs.property("version", appVersion)
    filesMatching("version.properties") { expand("version" to appVersion) }
}

tasks.named<JavaExec>("run") {
    systemProperty("soundpadplus.home", layout.projectDirectory.dir("run-data").asFile.absolutePath)
}

// jpackage bundles its own trimmed JRE into an app-image folder.
val distDir = layout.buildDirectory.dir("dist").get().asFile
tasks.register<Exec>("packageApp") {
    dependsOn("installDist")
    val libs = layout.buildDirectory.dir("install/${project.name}/lib").get().asFile
    doFirst { File(distDir, "SoundpadPlus").deleteRecursively() }
    commandLine(
        "jpackage", "--type", "app-image", "--name", "SoundpadPlus", "--app-version", appVersion,
        "--input", libs, "--main-jar", "${project.name}-$version.jar", "--main-class", "com.emprecio.soundpadplus.MainKt",
        "--add-modules", "java.desktop,java.logging,java.naming,java.management,java.net.http,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported",
        "--jlink-options", "--strip-native-commands --strip-debug --no-man-pages --no-header-files --compress zip-9",
        "--dest", distDir
    )
}

// The installer is made with Inno Setup 6 (language choice, "open the program" checkbox). It installs per-user in
// %LOCALAPPDATA%\SoundpadPlus so the app can write its settings, plugins and tools next to the exe without admin rights.
tasks.register<Exec>("packageInstaller") {
    dependsOn("packageApp")
    val iscc = listOfNotNull(System.getenv("LOCALAPPDATA"), System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"))
        .map { File(it, if (it == System.getenv("LOCALAPPDATA")) "Programs/Inno Setup 6/ISCC.exe" else "Inno Setup 6/ISCC.exe") }
        .firstOrNull { it.exists() } ?: File("ISCC.exe")
    commandLine(
        iscc, "/Qp", "/DAppVersion=$appVersion", "/DOutputDir=$distDir", "/DSourceDir=${File(distDir, "SoundpadPlus")}",
        "/DExamplesDir=${file("examples/json-source")}", file("installer/SoundpadPlus.iss")
    )
}

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
