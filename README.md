# dynamic-parser

A generic parser for Java whose grammar can be modified while parsing.

The project is at an early stage: the build is set up, but there is no parser functionality yet.

## Requirements

- JDK 21 or newer

Maven does not need to be installed. The Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows) downloads the required
Maven version on first use.

## Building

```bash
./mvnw verify
```

This compiles the sources, runs the tests and builds the JAR in `target/`.

## Code formatting

The Java sources are formatted with the Eclipse formatter: the built-in Eclipse profile, indentation with tabs and a
maximum line length of 120. The profile is stored in [`eclipse-formatter.xml`](eclipse-formatter.xml).

Format the sources before committing:

```bash
./mvnw spotless:apply
```

In Eclipse, import the profile under *Preferences > Java > Code Style > Formatter > Import...* and format as usual.
The build uses the formatter of Eclipse 4.41 (2026-09); other Eclipse versions may format a few edge cases
differently.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
