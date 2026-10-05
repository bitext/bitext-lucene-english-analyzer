import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
  `java-library`
}

group = "com.bitext.lucene"
version = "1.0.0"
description = "Bitext Lucene English Analyzer"

repositories {
  mavenCentral()
}

subprojects {
  group = rootProject.group
  version = rootProject.version

  tasks.withType<AbstractArchiveTask>().configureEach {
    includeEmptyDirs = false
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    filePermissions {
      unix("rw-r--r--")
    }
    dirPermissions {
      unix("rwxr-xr-x")
    }
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
  withSourcesJar()
}

dependencies {
  api("org.apache.lucene:lucene-core:10.4.0")
  api("org.apache.lucene:lucene-analysis-common:10.4.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  maxHeapSize = "2g"
}

tasks.withType<AbstractArchiveTask>().configureEach {
  includeEmptyDirs = false
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
  filePermissions {
    unix("rw-r--r--")
  }
  dirPermissions {
    unix("rwxr-xr-x")
  }
}

tasks.jar {
  archiveBaseName.set("bitext-lucene-english-analyzer")
  from(layout.projectDirectory.file("LICENSE")) {
    into("META-INF")
  }
  manifest {
    attributes(
      "Implementation-Title" to "Bitext Lucene English Analyzer",
      "Implementation-Version" to project.version,
    )
  }
}

val copyElasticsearchDistribution by tasks.registering(Copy::class) {
  dependsOn(":adapters:elasticsearch:pluginZip")
  from(project(":adapters:elasticsearch").layout.buildDirectory.dir("distributions")) {
    include("bitext-java-english-elasticsearch-${project.version}.zip")
  }
  into(layout.buildDirectory.dir("distributions"))
}

val copyOpenSearchDistribution by tasks.registering(Copy::class) {
  dependsOn(":adapters:opensearch:pluginZip")
  from(project(":adapters:opensearch").layout.buildDirectory.dir("distributions")) {
    include("bitext-java-english-opensearch-${project.version}.zip")
  }
  into(layout.buildDirectory.dir("distributions"))
}

tasks.named("build") {
  dependsOn(copyElasticsearchDistribution)
  dependsOn(copyOpenSearchDistribution)
}

tasks.register<Exec>("integrationTest") {
  group = "verification"
  description = "Runs public Docker integration checks for Elasticsearch, OpenSearch, and Solr."
  dependsOn(tasks.named("build"))
  commandLine("bash", "src/test/scripts/run-public-integrations.sh")
}
