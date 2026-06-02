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
        // ALAMAT BARU: Menyuruh server mencari pustaka grafik MPAndroidChart di sini
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "SmartFinanceTracker"
include(":app")
