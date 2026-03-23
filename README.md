# Hold My NATS

This project implements [NATS](https://docs.nats.io/nats-concepts/what-is-nats) but using java instead of original one
that is using golang.

## Technology Stack Used

* Written in `Java 25 LTS`. Version specified inside `.sdkmanrc` file using [sdkman](https://sdkman.io/usage)
* Maven `v3.9.9` with the [wrapper](https://maven.apache.org/wrapper/)
* Compiled to native executable using [GraalVM](https://www.graalvm.org/)
* Uses [virtual threads](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html)
  and [structured concurrency](https://docs.oracle.com/en/java/javase/24/core/structured-concurrency.html)
* Uses [Error Prone](https://errorprone.info/) as an additional compiler to `javac`.
* Uses [Spotless](https://github.com/diffplug/spotless/) for automatic code formatting
  in [Android Open Source Project](https://source.android.com/docs/setup/contribute/code-style) style.
* Uses [grype](https://github.com/anchore/grype) to detect CVEs inside dependencies.

## Build & run

### Standard maven

* Build self-executable jar file

```bash
./mvnw clean package
```

* Run application
  Pay attention that we also need to provide `--enable-preview` during runtime because we have used
  [Structured Concurrency](https://docs.oracle.com/en/java/javase/25/core/structured-concurrency.html) which is in a
  preview mode for java 25.

```bash
./run.sh
```

### Native image

* Build native image using maven `native` profile

If you're using Windows make sure you have [Visual Studio 2022](https://visualstudio.microsoft.com/downloads/)
installed.
It's necessary for the native image compilation.

```bash
./mvnw clean package -Pnative -DskipTests
```

* Run native executable (Windows or Unix)

```bash
./target/hold-my-nats.exe

./target/hold-my-nats
```

## Vulnerability Scan

We will use [grype](https://github.com/anchore/grype) as our vulnerability scanner for CI and locally.

```bash
grype . --name hold-my-nats
```

Expected output:

```
 ✔ Indexed file system                                                                                                                  .
 ✔ Cataloged contents 47be346d20a8cd65a0cf1689c66413d0193e7de65cde1e63a8f12efcc7b7e02f
   ├── ✔ Packages                        [9 packages]
   ├── ✔ File digests                    [0 files]
   ├── ✔ Executables                     [0 executables]
   └── ✔ File metadata                   [0 locations]
 ✔ Scanned for vulnerabilities     [0 vulnerability matches]
   ├── by severity: 0 critical, 0 high, 0 medium, 0 low, 0 negligible
   └── by status:   0 fixed, 0 not-fixed, 0 ignored
No vulnerabilities found
```