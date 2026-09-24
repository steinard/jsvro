# JSVRO

Fast, compact, JSON-like serialization format.

> **Status:** experimental `0.1.0-SNAPSHOT`. The v1 wire contract is documented in [SPEC.md](SPEC.md).

**JSON Schema + Values, Row-Oriented**: a compact, streaming, human-readable wire format for JVM/Spring applications.

The JSVRO header is its own small wire schema; it is **not** the JSON Schema specification.

JSVRO writes the object shape once and then sends positional JSON rows. It keeps the useful properties of JSON — readable values, native JSON scalars, easy `curl` debugging — while avoiding repeated field names.

```java
public record Address(String street, String city, String country) {}
public record Person(String name, int age, Address address) {}
```

becomes:

```json
{"jsvro":"1","columns":[{"name":"name","type":"string"},{"name":"age","type":"integer"},{"name":"address","type":"object","columns":[{"name":"street","type":"string"},{"name":"city","type":"string"},{"name":"country","type":"string"}]}]}
["Alice",30,["Main Street 1","Oslo","NO"]]
["Bob",40,["Parkveien 4","Bergen","NO"]]
```

## Goals

- compact without becoming binary
- fast streaming writes with no per-row tree model
- one schema record followed by positional rows
- recursive objects and arrays
- normal JSON values, including `null`
- Jackson's property model is the source of truth
- no annotations required for ordinary Java records/beans
- drop-in Spring MVC content negotiation
- Java 21 bytecode, usable from Java 21 through Java 25+ JVMs

JSVRO deliberately does **not** use dictionary IDs, null bitmaps, delta encoding, or binary packing. Those techniques save more bytes but make terminal inspection substantially worse; if that level of optimization is needed, use a binary schema format such as Avro instead.

## Modules

- `jsvro-core` — schema derivation, cached codecs, streaming encoder, row-at-a-time decoder
- `jsvro-spring-boot-starter` — Spring Boot 4 MVC auto-configuration and `HttpMessageConverter`
- `jsvro-example` — tiny runnable controller demonstrating zero controller boilerplate

## Spring Boot usage

Add the starter:

```kotlin
dependencies {
    implementation("dev.jsvro:jsvro-spring-boot-starter:0.1.0")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
}
```

Controller code stays normal:

```java
public record Person(String name, int age) {}

@GetMapping("/people")
public List<Person> people() {
    return service.people();
}
```

Normal JSON remains the default:

```bash
curl http://localhost:8080/people
```

Request JSVRO explicitly:

```bash
curl -H 'Accept: application/vnd.jsvro' http://localhost:8080/people
```

No `@JsonProperty`, JSVRO wrapper, schema declaration, or controller-specific serialization code is required.

Disable auto-configuration if needed:

```properties
jsvro.enabled=false
```

## Wire format v1

A stream consists of independent JSON values separated by newlines.

The first value is the schema:

```json
{"jsvro":"1","columns":[...]}
```

Every following value is one row. Object fields are represented positionally in schema order:

```json
["Alice",30]
["Bob",40]
```

Nested objects are recursively positional:

```json
["Alice",30,["Main Street 1","Oslo","NO"]]
```

Arrays remain arrays. Arrays of objects use the nested object item's positional schema. Maps retain ordinary JSON object form because map keys are data rather than statically-known field names.

### Types

JSVRO v1 uses a small language-neutral schema vocabulary:

`string`, `integer`, `number`, `decimal`, `boolean`, `date`, `datetime`, `uuid`, `binary`, `object`, `array`, `map`.

Java implementation class names are never written to the wire.

## Jackson behavior

JSVRO derives properties through Jackson serialization introspection, rather than `Class.getRecordComponents()`. This means it follows the configured Jackson property names, ordering and ignored properties. Plain records require no annotations:

```java
public record Person(String name, int age) {}
```

Schema/codec derivation is cached by Jackson `JavaType`; it is not repeated per row. The hot encoding path writes directly to Jackson's `JsonGenerator` and does not construct `JsonNode` trees.

The v1 decoder validates the incoming schema, then buffers **one row at a time** as named Jackson tokens and binds them to the target Java type. `readStream` decodes lazily; `readList` collects every row.

## Current constraints

- root rows must be object/record-like types
- recursive/cyclic object schemas are rejected in v1
- polymorphic, abstract, `@JsonUnwrapped`, `@JsonAnyGetter` and untyped (`Object`) properties are rejected when the schema is derived
- Spring request-body decoding currently targets `List<T>`
- Spring response encoding supports `Iterable<T>`, `Stream<T>`, and arrays
- custom Jackson serializers must describe their JSON shape through `acceptJsonFormatVisitor`, otherwise the schema cannot be derived

## Build

The project targets Java 21 bytecode. It can be consumed by Java 21, 22, 23, 24, 25 and newer compatible JVMs.

```bash
./gradlew build
```

The Gradle wrapper pins Gradle 9.3.0. A GitHub Actions workflow runs the build on pushes to `main` and on pull requests.

## Why not just gzip JSON?

You often should enable HTTP compression as well. Repeated JSON field names compress efficiently, but JSVRO still avoids generating, parsing and matching those names in the first place. Treat gzip/zstd as a transport optimization and JSVRO as a representation optimization. Benchmark both wire size and CPU for your workload.

## License

Apache License 2.0.
