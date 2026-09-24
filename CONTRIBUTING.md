# Contributing

Contributions are welcome.

## Local checks

Use Java 21 or newer and the Gradle wrapper:

```bash
./gradlew build
```

Before changing the wire format, update `SPEC.md` and add compatibility tests in `jsvro-core`.

## Compatibility principles

- Java bytecode target remains 21 unless a major release intentionally changes it.
- Existing JSVRO v1 wire output should not change accidentally.
- Normal Spring JSON behavior must remain the default unless a client explicitly requests JSVRO.
- Per-row encoding should avoid intermediate `JsonNode` trees.
