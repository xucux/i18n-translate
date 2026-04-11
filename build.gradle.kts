plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "com.github.xucux"
version = "0.0.3-231"

repositories {
    maven("https://maven.aliyun.com/repository/gradle-plugin")
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/central/")
    maven("https://mirrors.huaweicloud.com/repository/maven")
    maven("https://maven.aliyun.com/repository/jetbrains-intellij-releases")
    maven("https://maven.aliyun.com/repository/jetbrains-public")
    maven("https://repo.huaweicloud.com/repository/jetbrains-intellij-releases")
    maven("https://repo.huaweicloud.com/repository/jetbrains-public")
    mavenCentral()
}

intellij {
    version.set("2023.1.5")
    type.set("IC")
    plugins.set(listOf())
    plugins.set(listOf())
    sandboxDir.set(layout.buildDirectory.dir("idea-sandbox-i18n-translate").get().asFile.absolutePath)
}

dependencies {
    // 翻译走 HTTP API + JDK 签名；不再使用阿里云 / 腾讯云 Java SDK，避免 bcprov 等大体积传递依赖
    // implementation("com.aliyun:alimt20181012:1.5.2")
    // implementation("com.tencentcloudapi:tencentcloud-sdk-java-tmt:3.1.1436")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
    // implementation("org.yaml:snakeyaml:2.2")
    
    // SwingX for JXMultiSplitPane
    // implementation("org.swinglabs.swingx:swingx-all:1.6.5-1")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("261.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
