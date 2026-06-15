pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        
        // 🔥 Ini "kode java" murni yang lu ingat! Paling kebal error.
        maven { 
            url = java.net.URI("https://jitpack.io") 
        }
    }
}

rootProject.name = "SmartFinanceTracker"
include(":app")
