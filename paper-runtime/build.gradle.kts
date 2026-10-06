plugins { java }
group = "dev.paperexport"
version = "1.0.1"
repositories { maven("https://repo.papermc.io/repository/maven-public/"); mavenCentral() }
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("com.google.code.gson:gson:2.13.2")
    testImplementation("org.joml:joml:1.10.8")
}
tasks.withType<JavaCompile> { options.release.set(21); options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }
tasks.jar { archiveFileName.set("PaperExport-Paper-1.0.1.jar") }
tasks.register<Jar>("apiJar") {
    archiveFileName.set("PaperExport-API-1.0.1.jar")
    from(sourceSets.main.get().output)
    include("dev/paperexport/api/**")
    dependsOn(tasks.classes)
}
