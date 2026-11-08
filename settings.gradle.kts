plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "SoundpadPlus"
include("examples:example-source")
include("plugins:youtube")
