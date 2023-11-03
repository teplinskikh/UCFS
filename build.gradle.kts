plugins {
  java
  application
  kotlin("jvm") version "1.9.20"
}

group = "hollowcoder"
version = "1.0.0"

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
  //testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(11) }
application { mainClass.set("org.srcgll.MainKt") }

configure<SourceSetContainer> {
  named("main") {
    java.srcDir("src/main/kotlin")
  }
}

//tasks.withType<Jar> {
//  dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources"))
//  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//  manifest { attributes(mapOf("Main-Class" to application.mainClass)) }
//  val sourcesMain = sourceSets.main.get()
//  val contents =
//      configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } +
//          sourcesMain.output
//  from(contents)
//}