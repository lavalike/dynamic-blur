plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.wangzhen.blur"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        renderscriptTargetApi = 33

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

}

sourceSets {
    create("main") {
        java.srcDir("src/main/java")
    }
}

val androidSourceJar by tasks.registering(Jar::class) {
    from(sourceSets["main"].allSource)
    archiveClassifier.set("sources")
}

afterEvaluate {
    publishing.publications {
        create<MavenPublication>("release") {
            artifact(androidSourceJar)
            artifact(tasks.getByName("bundleReleaseAar"))
        }
    }
}