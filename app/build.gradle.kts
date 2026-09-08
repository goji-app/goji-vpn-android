import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Ключ подписи релиза — файл лежит рядом (app/keystore.properties), не в системе контроля
// версий (проект не под git). Если файла нет (например, свежий чекаут без ключа) — релизная
// сборка просто останется неподписанной, а не упадёт с ошибкой конфигурации.
val keystorePropsFile = rootProject.file("app/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "xyz.gojihub.vpn"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.gojihub.vpn"
        minSdk = 24 // VpnService + Reality нормально живут с 24+, но проверьте охват вашей аудитории
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.21"

        // libXray.aar тянет нативные .so сразу под 4 ABI — реальные телефоны это почти
        // всегда arm64-v8a (и изредка armeabi-v7a на старых). x86/x86_64 нужны только
        // для эмуляторов и раздувают APK в ~2 раза без всякой пользы для реальных устройств.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // minSdk 24 не имеет java.time нативно (появился в API 26) — десахаринг добавляет его.
        isCoreLibraryDesugaringEnabled = true
    }
    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        jniLibs {
            // true = .so сжимаются внутри APK (меньше вес файла для раздачи вручную).
            // Если будете публиковать в Play Store как AAB — можно вернуть false,
            // Play сам решает паковку и предпочитает несжатые/выровненные библиотеки.
            useLegacyPackaging = true
        }
    }

    // Отдельный APK на каждую архитектуру — внутри только свой .so, а не все сразу.
    // Итоговые имена файлов переопределены ниже (androidComponents.onVariants) на
    // Goji-<версия>-<abi>.apk вместо стандартных app-<abi>-<buildType>.apk.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
}

// Переименование итоговых APK: Goji-<versionName>-<abi|universal>.apk вместо стандартного
// app-<abi>-<buildType>.apk — так собранные файлы сразу узнаваемы среди прочего в папке
// outputs, без необходимости заглядывать в имя buildType/module.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier ?: "universal"
            output.outputFileName.set("Goji-${android.defaultConfig.versionName}-$abi-${variant.buildType}.apk")
        }
    }
}

dependencies {
    // ── Compose ──────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // ── Hilt (DI) ────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Сеть: Remnawave API ──────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── Хранение токенов ─────────────────────────────────
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Фоновое обновление подписки/пинга раз в час (работает и когда приложение
    // свёрнуто) — WorkManager с Hilt-инъекцией зависимостей в Worker ──
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // ── Xray-core / libXray ──────────────────────────────
    // Соберите libbox.aar по инструкции XTLS/libXray (python3 build/main.py android)
    // и положите в app/libs/, либо подключите как maven-артефакт, если решите
    // публиковать свою сборку в приватный репозиторий.
    implementation(files("libs/libXray.aar"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.browser:browser:1.8.0") // Custom Tabs для native OAuth
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation("junit:junit:4.13.2")
}
