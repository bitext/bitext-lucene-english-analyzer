plugins {
  java
}

description = "OpenSearch plugin adapter for the Bitext Lucene English Analyzer"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(project(":"))
  compileOnly("org.opensearch:opensearch:3.4.0")
  compileOnly("org.apache.lucene:lucene-analysis-common:10.1.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
  testImplementation("org.opensearch:opensearch:3.4.0")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

val pluginZip by tasks.registering(Zip::class) {
  archiveBaseName.set("bitext-java-english-opensearch")
  destinationDirectory.set(layout.buildDirectory.dir("distributions"))
  dependsOn(tasks.jar, project(":").tasks.jar)
  from(tasks.jar.flatMap { it.archiveFile })
  from(project(":").tasks.jar.flatMap { it.archiveFile })
  from(rootProject.layout.projectDirectory.file("LICENSE"))
  from(layout.projectDirectory.dir("src/main/resources/plugin-metadata")) {
    expand("version" to project.version)
  }
}
