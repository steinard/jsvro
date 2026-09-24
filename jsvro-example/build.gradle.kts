plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":jsvro-spring-boot-starter"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
}
