import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort

plugins {
    java
    jacoco
    checkstyle
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.2.1"
    id("com.github.spotbugs") version "6.2.5"
}

group = "com.relay"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val resilience4jVersion = "2.3.0"
val bouncyCastleVersion = "1.81"
val testcontainersVersion = "1.21.4"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
    }
}

dependencies {
    // --- web / api
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux") // WebClient for the AI sidecar
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server") // RS256 JWT (Nimbus)
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion") // ML-KEM, ML-DSA, SHA3, HKDF
    implementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion")

    // --- resilience
    implementation("io.github.resilience4j:resilience4j-spring-boot3:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-bulkhead:$resilience4jVersion")

    // --- engine
    implementation("com.networknt:json-schema-validator:1.5.8")

    // --- observability
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("io.micrometer:micrometer-registry-prometheus")

    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.4")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.9.4")

    // --- tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.wiremock:wiremock-standalone:3.13.1")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------------
// The catalog, seeds and sample payloads are NOT hand-copied into resources.
// They are copied from the read-only /data fixtures at build time so the
// classpath copy can never drift from the canonical file.
// ---------------------------------------------------------------------------
val fixturesDir = providers.gradleProperty("relay.fixturesDir")
    .orElse(layout.projectDirectory.dir("../data").asFile.absolutePath)

tasks.processResources {
    from(fixturesDir) {
        include("node_catalog.json")
        into("catalog")
    }
    from(fixturesDir) {
        include("seed_workflows.json", "sample_payloads.jsonl", "nl_eval.jsonl")
        into("seed")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
}

tasks.test {
    useJUnitPlatform()
    // Testcontainers-backed tests are tagged; `-PskipIntegration` runs only unit/slice tests.
    if (providers.gradleProperty("skipIntegration").isPresent) {
        useJUnitPlatform { excludeTags("integration") }
    }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports { xml.required = true; html.required = true }
}

tasks.bootJar {
    archiveFileName = "relay.jar"
}

// --- code style --------------------------------------------------------------
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.28.0")
        removeUnusedImports()
    }
    // Formatting is enforced by `make lint` / CI (`spotlessCheck`), not by every `check`.
    isEnforceCheck = false
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

spotbugs {
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = file("config/spotbugs/exclude.xml")
    // Report-only until the first full CI run establishes a baseline; CI passes -PstrictQuality.
    ignoreFailures = !providers.gradleProperty("strictQuality").isPresent
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
}
