pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        mavenCentral()
    }
}

rootProject.name = "Aircraft"
include(":app", ":richtexteditor", ":supperbanner", ":developtools")
