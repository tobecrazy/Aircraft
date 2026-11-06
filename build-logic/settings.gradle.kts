dependencyResolutionManagement {
    repositories { google(); gradlePluginPortal(); maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }; maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }; mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "build-logic"
include(":convention")
