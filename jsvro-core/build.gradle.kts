plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api(libs.jackson.databind)

    testImplementation(libs.junit.jupiter)
}
