plugins {
  java
}

description = "Elasticsearch stable-plugin adapter for the Bitext Lucene English Analyzer"

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
  compileOnly("org.elasticsearch.plugin:elasticsearch-plugin-api:9.5.4")
  compileOnly("org.elasticsearch.plugin:elasticsearch-plugin-analysis-api:9.5.4")
  compileOnly("org.apache.lucene:lucene-analysis-common:10.4.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
  testImplementation("org.elasticsearch.plugin:elasticsearch-plugin-api:9.5.4")
  testImplementation("org.elasticsearch.plugin:elasticsearch-plugin-analysis-api:9.5.4")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

val pluginZip by tasks.registering(Zip::class) {
  archiveBaseName.set("bitext-java-english-elasticsearch")
  destinationDirectory.set(layout.buildDirectory.dir("distributions"))
  dependsOn(tasks.jar, project(":").tasks.jar)
  from(tasks.jar.flatMap { it.archiveFile })
  from(project(":").tasks.jar.flatMap { it.archiveFile })
  from(rootProject.layout.projectDirectory.file("LICENSE"))
  from(layout.projectDirectory.dir("src/main/resources/plugin-metadata")) {
    expand("version" to project.version)
  }
}
