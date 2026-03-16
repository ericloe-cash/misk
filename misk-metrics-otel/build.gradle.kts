import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":misk-metrics"))
  api(project(":misk-inject"))
  api(libs.opentelemetryApi)
  implementation(libs.opentelemetrySdk)
  implementation(libs.opentelemetrySdkMetrics)
  implementation(libs.guice)
  implementation(libs.jakartaInject)
  implementation(libs.kotlinStdLibJdk8)

  // For bridge mode: Prometheus exporter from OTel + Prometheus client
  implementation(libs.prometheusClient)
  implementation(project(":misk-prometheus"))

  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-metrics")))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.opentelemetrySdkTesting)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
