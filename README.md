# Hold My NATS

`hold-my-nats` is a Java implementation of
[NATS](https://docs.nats.io/nats-concepts/what-is-nats), inspired by the original Go-based server.

## Tech stack

- **Java 25 LTS**
  - Version is pinned in `.sdkmanrc`
  - Managed with [SDKMAN](https://sdkman.io/usage)
- **Maven 3.9.9** via the [Maven Wrapper](https://maven.apache.org/wrapper/)
- **GraalVM** for building a native executable
- Java concurrency features:
  - [Virtual Threads](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html)
  - [Structured Concurrency](https://docs.oracle.com/en/java/javase/24/core/structured-concurrency.html)
- Quality and tooling:
  - [Error Prone](https://errorprone.info/) for additional compile-time checks
  - [Spotless](https://github.com/diffplug/spotless/) with
    [AOSP style](https://source.android.com/docs/setup/contribute/code-style)
  - [grype](https://github.com/anchore/grype) for dependency vulnerability scanning

## Build and run

### 1) Build runnable JAR (standard Maven build)

```bash
./mvnw clean package
```

### 2) Run the application

Use the provided script:

```bash
./run.sh
```

> **Why preview flags are needed:**
> This project uses Structured Concurrency, which is a preview feature in Java 25.
> Runtime must include `--enable-preview` (already handled by `run.sh`).

## Build native image

Build with the Maven `native` profile:

```bash
./mvnw clean package -Pnative -DskipTests
```

### Windows note

On Windows, native-image compilation requires
[Visual Studio 2022](https://visualstudio.microsoft.com/downloads/) to be installed.

### Run native executable

Windows:

```bash
./target/hold-my-nats.exe
```

Unix-like systems:

```bash
./target/hold-my-nats
```

## Vulnerability scan

Run [grype](https://github.com/anchore/grype) locally (same approach as CI):

```bash
grype . --name hold-my-nats
```

Expected result should report no vulnerabilities, for example:

```
✔ Scanned for vulnerabilities [0 vulnerability matches]
No vulnerabilities found
```