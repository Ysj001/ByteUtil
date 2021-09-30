plugins {
    id("groovy")
    id("java-library")
    id("kotlin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    google()
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$KOTLIN_VERSION")
    compileOnly("com.android.tools.build:gradle:$ANDROID_GRADLE_VERSION")
    implementation("$LIB_GROUP_ID:bytecodeutil-modifier:$LIB_VERSION")
}

// 如果要发布本 demo 则打开注释
//mavenPublish()