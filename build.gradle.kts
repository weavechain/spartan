import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer

group = "com.weavechain"
version = "1.0.3"

plugins {
    java
    `maven-publish`
    id("org.jetbrains.dokka") version "1.8.20"
    id("com.github.jk1.dependency-license-report") version "2.4"
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    signing

    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    java

    mavenCentral()
    maven("https://jitpack.io")
    maven("https://dl.cloudsmith.io/public/consensys/maven/maven")
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    implementation("org.slf4j:slf4j-api:2.0.0")

    implementation("com.github.aelstad:keccakj:1.1.0")
    implementation("org.bitcoinj:bitcoinj-core:0.17-rc1")
    implementation("org.msgpack:msgpack-core:0.9.9")
    implementation("io.airlift:aircompressor:0.27")
    implementation("com.github.ben-manes:caffeine:3.1.6") {
        exclude("com.github.ben-manes.caffeine", "simulator")
    }

    implementation("com.weavechain:curve25519-elisabeth:0.1.5")

    implementation("tech.pegasys:jblst:0.3.15")

    implementation("com.google.guava:guava:32.0.0-jre")
    implementation("commons-codec:commons-codec:1.15")
    implementation("com.google.code.gson:gson:2.11.0")

    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")
    testImplementation("org.testng:testng:7.8.0")
    testImplementation("com.google.truth:truth:1.1.4")
}

tasks.withType<Test>().configureEach {
    useTestNG()
}

val sourcesJar by tasks.registering(Jar::class) {
    classifier = "sources"
    from(sourceSets.main.get().allSource)
}

val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
}

publishing {
    repositories {
        maven {
            url = uri("./build/repo")
            name = "Maven"
        }
    }

    publications {
        create<MavenPublication>("Maven") {
            groupId = "com.weavechain"
            artifactId = "spartan"
            version = "1.0.3"
            from(components["java"])
        }
        withType<MavenPublication> {
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set(project.name)
                description.set("Java implementation for Spartan: High-speed zkSNARKs without trusted setup")
                url.set("https://github.com/weavechain/spartan")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/mit")
                    }
                }
                issueManagement {
                    system.set("Github")
                    url.set("https://github.com/weavechain/spartan/issues")
                }
                scm {
                    connection.set("scm:git:git://github.com/weavechain/spartan.git")
                    developerConnection.set("scm:git:git@github.com:weavechain/spartan.git")
                    url.set("https://github.com/weavechain/spartan")
                }
                developers {
                    developer {
                        name.set("Ioan Moldovan")
                        email.set("ioan.moldovan@weavechain.com")
                    }
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(configurations.archives.get())
    sign(publishing.publications["Maven"])
}

tasks {
    sourceSets.getByName("test") {
        java.srcDir("src/test/java")
    }

    val releaseJar by creating(Jar::class) {
        archiveClassifier.set("spartan")

        from(sourceSets.main.get().output.classesDirs)
        include("com/weavechain/**")
    }

    artifacts {
        add("archives", releaseJar)
        add("archives", sourcesJar)
    }
}

tasks.withType<PublishToMavenRepository> {
    dependsOn("checkLicense")
}

licenseReport {
    filters = arrayOf<DependencyFilter>(
            LicenseBundleNormalizer(
                    "$rootDir/normalizer-bundle.json",
                    true
            )
    )
    excludeGroups = arrayOf<String>(
    )
    allowedLicensesFile = File("$rootDir/allowed-licenses.json")
}