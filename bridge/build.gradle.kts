plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.m1xamm.mxbridge"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.m1xamm.mxtube"
        minSdk = 26
        targetSdk = 37
        versionCode = 32
        versionName = "migration"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
