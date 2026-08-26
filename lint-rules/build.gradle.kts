plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:31.6.0")
    compileOnly("com.android.tools.lint:lint-checks:31.6.0")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.22")
}

tasks.jar {
    manifest {
        attributes(
            "Lint-Registry-v2" to "com.example.lint.ProjectIssueRegistry",
            "Lint-Registry" to "com.example.lint.ProjectIssueRegistry",
        )
    }
}
