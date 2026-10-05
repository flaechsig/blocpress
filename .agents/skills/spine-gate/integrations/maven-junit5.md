---
name: maven-junit5
title: Maven with JUnit 5
detect: pom.xml
keywords: [Java, Maven, JUnit]
test_command: mvn verify
test_reports: [target/surefire-reports]
requires: [JDK, Maven 3.8 or later, Python 3.9 or later]
tested_with: [Maven 3.8.7, Surefire 3.5.4, exec-maven-plugin 3.5.0, JUnit 5.11 and 5.13]
---

# Integration: Maven with JUnit 5

Fulfils the integration contract in `.docspine/STANDARD.md` section 11: test results as
JUnit XML reports with the requirement ID in the test name, and `check` in the build
after the tests. `render` stays outside the build.

## Test results

**The requirement ID in the display name:**

```java
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HelloWorldTest {

    @Test
    @DisplayName("REQ-0001: prints \"Hello World!\" followed by a line break to standard output")
    void printsGreeting() {
        // …
    }
}
```

A display name on the class applies to every test in it. A test may name several
requirements: `@DisplayName("REQ-0001 REQ-0002 …")`. `@Tag("REQ-0001")` does not work:
Surefire does not write tags into the report.

**Surefire writes display names into the report.** In `pom.xml`, under `<build>` →
`<plugins>`:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.5.4</version>
  <configuration>
    <statelessTestsetReporter implementation="org.apache.maven.plugin.surefire.extensions.junit5.JUnit5Xml30StatelessReporter">
      <usePhrasedTestCaseMethodName>true</usePhrasedTestCaseMethodName>
    </statelessTestsetReporter>
  </configuration>
</plugin>
```

Without this setting, the report contains the method name instead of the display name.
The display name of the class is written either way.

**The profile names the reports.** In `.docspine/PROFILE.md`:

```yaml
test_reports: [target/surefire-reports]
```

## Check in the build

`check` runs in the phase `verify`, after the tests. An error fails the build.

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>3.5.0</version>
  <executions>
    <execution>
      <id>docspine-check</id>
      <phase>verify</phase>
      <goals><goal>exec</goal></goals>
      <configuration>
        <executable>python3</executable>
        <workingDirectory>${project.basedir}</workingDirectory>
        <arguments>
          <argument>.docspine/docspine.pyz</argument>
          <argument>check</argument>
        </arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Workflow:

```
python3 .docspine/docspine.pyz render
mvn verify
```

Verified: with correct documentation the build succeeds and `check` reports `OK`; an
unknown epic (error 3) and a hand-edited generated region (error 11) each fail the build.

Optional, for the console: the line "Running …" shows the class's display name with

```xml
<statelessTestsetInfoReporter implementation="org.apache.maven.plugin.surefire.extensions.junit5.JUnit5StatelessTestsetInfoReporter">
  <usePhrasedClassNameInRunning>true</usePhrasedClassNameInRunning>
</statelessTestsetInfoReporter>
```

## New build

For a project without a build yet: a minimal `pom.xml` that already contains the
settings above. Replace `GROUP_ID`, `ARTIFACT_ID` and `JAVA_VERSION` (for example `21`).
Code goes under `src/main/java/`, tests under `src/test/java/`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>GROUP_ID</groupId>
  <artifactId>ARTIFACT_ID</artifactId>
  <version>1.0.0-SNAPSHOT</version>

  <properties>
    <maven.compiler.release>JAVA_VERSION</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.13.4</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
      </plugin>
      <!-- docspine: display names with the requirement ID go into the test reports -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.4</version>
        <configuration>
          <statelessTestsetReporter implementation="org.apache.maven.plugin.surefire.extensions.junit5.JUnit5Xml30StatelessReporter">
            <usePhrasedTestCaseMethodName>true</usePhrasedTestCaseMethodName>
          </statelessTestsetReporter>
        </configuration>
      </plugin>
      <!-- docspine: check the documentation after the tests; an error fails the build -->
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.0</version>
        <executions>
          <execution>
            <id>docspine-check</id>
            <phase>verify</phase>
            <goals><goal>exec</goal></goals>
            <configuration>
              <executable>python3</executable>
              <workingDirectory>${project.basedir}</workingDirectory>
              <arguments>
                <argument>.docspine/docspine.pyz</argument>
                <argument>check</argument>
              </arguments>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

Verified: with a test named `REQ-0001: …` the report contains the ID, the check in
`verify` reports `OK`, and the build succeeds.

## Prerequisites

- JDK and Maven 3.8 or later
- Python 3.9 or later for the checker; the build calls `python3`
