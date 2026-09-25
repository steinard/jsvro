import org.apache.avro.Schema
import org.apache.avro.compiler.specific.SpecificCompiler
import org.apache.avro.generic.GenericData

buildscript {
    dependencies {
        classpath(libs.avro.compiler)
    }
}

plugins {
    `java-library`
    `maven-publish`
}

abstract class GenerateAvro : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemas: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun generate() {
        val destination = output.get().asFile
        destination.deleteRecursively()
        destination.mkdirs()
        schemas.asFileTree.matching { include("**/*.avsc") }.files.sorted().forEach { file ->
            val compiler = SpecificCompiler(Schema.Parser().parse(file))
            compiler.setStringType(GenericData.StringType.String)
            compiler.setEnableDecimalLogicalType(true)
            compiler.compileToDestination(file, destination)
        }
    }
}

val generateTestAvro by tasks.registering(GenerateAvro::class) {
    description = "Generates Java classes from the Avro schemas used by the benchmark."
    schemas = layout.projectDirectory.dir("src/test/avro")
    output = layout.buildDirectory.dir("generated/sources/avro/test/java")
}

sourceSets.test {
    java.srcDir(generateTestAvro)
}

dependencies {
    api(libs.jackson.databind)

    testImplementation(libs.avro)
    testImplementation(libs.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-params")
}

tasks.withType<Test>().configureEach {
    systemProperty("org.apache.avro.SERIALIZABLE_PACKAGES", "dev.jsvro.core.avro")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("benchmark")
    }
}

tasks.register<Test>("benchmark") {
    description = "Compares JSVRO with plain Jackson JSON and Avro for size, time, CPU and memory."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("benchmark")
    }
    maxHeapSize = "6g"
    listOf("plan", "sizes", "rowBudget").forEach { name ->
        providers.gradleProperty("benchmark.$name").orNull?.let { systemProperty("jsvro.benchmark.$name", it) }
    }
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
    }
}

abstract class PublishBenchmarkReport : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val report: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val summary: RegularFileProperty

    @get:OutputFile
    abstract val publishedReport: RegularFileProperty

    @get:Internal
    abstract val readme: RegularFileProperty

    @TaskAction
    fun publish() {
        val target = publishedReport.get().asFile
        target.parentFile.mkdirs()
        report.get().asFile.copyTo(target, overwrite = true)

        val start = "<!-- benchmark-summary:start -->"
        val end = "<!-- benchmark-summary:end -->"
        val readmeFile = readme.get().asFile
        val text = readmeFile.readText()
        val from = text.indexOf(start)
        val to = text.indexOf(end)
        require(from >= 0 && to > from) { "README.md must contain $start and $end" }
        val table = summary.get().asFile.readText().trim()
        readmeFile.writeText(text.substring(0, from + start.length) + "\n" + table + "\n" + text.substring(to))
    }
}

tasks.register<PublishBenchmarkReport>("publishBenchmarkReport") {
    description = "Copies the last benchmark report to docs/benchmark and updates the summary table in README.md."
    group = "documentation"
    report = layout.buildDirectory.file("reports/jsvro-benchmark/index.html")
    summary = layout.buildDirectory.file("reports/jsvro-benchmark/summary.md")
    publishedReport = rootProject.layout.projectDirectory.file("docs/benchmark/index.html")
    readme = rootProject.layout.projectDirectory.file("README.md")
    outputs.upToDateWhen { false }
}
