# Protege - Enhanced Java Code Generation for Protocol Buffers

Protege is a library for generating enhanced Java source code from `.proto` files, providing features and flexibility
beyond the standard Google `protoc` compiler. It also facilitates a "Java-first" workflow by generating Protobuf
definitions from Java interfaces.

## Features and Enhancements

Protege improves upon the standard `protoc` output with several specialized features:

* **Jackson Serialization:** Generated messages are compatible with Jackson, enabling native serialization and
  deserialization for text-based formats like JSON, CSV, and YAML.
* **Java Interface Support:** You can specify that generated message classes implement one or more existing Java
  interfaces, allowing for better polymorphism and integration with existing business logic.
* **Enhanced Oneof Handling:** Instead of simple integer tags, Protege generates sealed interfaces and records for
  `oneof` fields, leveraging modern Java pattern matching for type-safe variant handling.
* **Modern Java Syntax:** The generated code targets Java 21+, utilizing records, exhaustive switch expressions, and
  improved collection handling.

## Examples

### Custom Interfaces

Protege allows you to specify that a generated message should implement a specific Java interface. This enables polymorphism and allows you to treat generated messages as implementations of your business domain interfaces.

**`message.proto`**

```proto
syntax = "proto3";
import "protege/options.proto";

message User {
  option (dev.akre.protege.java_implements) = "com.example.Identifiable";

  int64 id = 1;
  string email = 2;
}
```

**`JsonUser.java`**

```java

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public interface JsonUser {
    @JsonProperty("user_id")
    String getId();

    @JsonAlias("email_address")
    String getEmail();
}
```

Your generated `User` and `User.Builder` classes will implement `JsonUser` allowing Jackson JSON serialization.


### Annotation Support

Protege provides the `java_annotation` option to add Java annotations directly to generated classes and fields. This is particularly useful for integration with persistence frameworks like JPA/Hibernate or validation frameworks.

**`persistence.proto`**

```proto
syntax = "proto3";
import "protege/options.proto";

message User {
  option (dev.akre.protege.java_annotation) = "@jakarta.persistence.Entity";
  option (dev.akre.protege.java_annotation) = "@jakarta.persistence.Table(name = \"users\")";

  int64 id = 1 [
    (dev.akre.protege.java_annotation) = "@jakarta.persistence.Id",
    (dev.akre.protege.java_annotation) = "@jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)"
  ];

  string email = 2 [
    (dev.akre.protege.java_annotation) = "@jakarta.persistence.Column(nullable = false, unique = true)"
  ];
}
```

The generated Java code will include the specified annotations, allowing the Protobuf message to function as a JPA entity.

### Enhanced Oneof Support

With `java_enhanced_oneof` enabled, Protege generates sealed interfaces for `oneof` fields, allowing for exhaustive and
type-safe pattern matching using modern Java switch expressions.

**`shape.proto`**

```proto
syntax = "proto3";
import "protege/options.proto";

option (dev.akre.protege.java_enhanced_oneof) = true;

message Thing {
  oneof shape {
    Circle circle = 1;
    Rectangle rectangle = 2;
    Square square = 3;
  }

  message Circle {
    double radius = 1;
  }

  message Rectangle {
    double width = 1;
    double height = 2;
  }

  message Square {
    double side = 1;
  }
}
```

**`AreaCalculator.java`**

```java
public double calculateArea(Thing thing) {
    return switch (thing.getShape()) {
        case Thing.CircleOrBuilder c -> Math.PI * c.getRadius() * c.getRadius();
        case Thing.RectangleOrBuilder r -> r.getWidth() * r.getHeight();
        case Thing.SquareOrBuilder s -> s.getSide() * s.getSide();
        case null -> 0.0;
    };
}
```

## Usage

Protege operates through two primary annotation processors that can be used independently or together depending on your
workflow.

### Annotation Processors

1. **`dev.akre.protege.ProtoAnnotationProcessor` (Java-to-Proto)**
   This processor enables a "Java-first" development cycle. It scans your source code for interfaces annotated with
   `@GenProto` and generates the corresponding `.proto` file definitions. This ensures your Protobuf schemas stay in
   sync with your Java service definitions.
2. **`dev.akre.protege.ProtoCompilerProcessor` (Proto-to-Java)**
   This is the core code generation engine. It identifies `.proto` files in a specified directory and generates enhanced
   Java source code. It handles the mapping of Protobuf types to modern Java constructs and injects the requested
   enhancements (like Jackson support).

### Command Line (javac)

To invoke the processors directly via the Java compiler, include the Protege jar in your classpath and specify the
processors using the `-processor` flag. You must also provide the `protoDir` option to tell the compiler where to find
or place `.proto` files.

```bash
javac -cp protege-1.0-SNAPSHOT.jar \
      -processor dev.akre.protege.ProtoCompilerProcessor,dev.akre.protege.ProtoAnnotationProcessor \
      -AprotoDir=src/main/proto \
      MyInterface.java
```

### Maven Integration

The most common way to use Protege is by adding it to the `maven-compiler-plugin` configuration. This ensures that code
generation happens automatically during the standard build lifecycle.

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>dev.akre</groupId>
                <artifactId>protege</artifactId>
                <version>1.0-SNAPSHOT</version>
            </path>
        </annotationProcessorPaths>
        <annotationProcessors>
            <annotationProcessor>dev.akre.protege.ProtoCompilerProcessor</annotationProcessor>
            <annotationProcessor>dev.akre.protege.ProtoAnnotationProcessor</annotationProcessor>
        </annotationProcessors>
        <compilerArgs>
            <arg>-AprotoDir=${project.basedir}/src/main/proto</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

### Gradle Integration

¯\\\_(ツ)_/¯

## Build Instructions

Protege requires **Java 21** and **Maven**.

To build the library and install it to your local Maven repository:

```bash
mvn clean install
```

This will trigger the ANTLR parser generation, compile the library, and run the test suite.

## Integration Tests

The project includes a robust suite of integration tests located in `src/it`. These tests verify that the generated code
compiles and functions correctly within a standard Maven project environment.

### How They Run

The integration tests utilize the `maven-invoker-plugin`. When the build reaches the `verify` stage:

1. **Isolation:** Each test case in `src/it` is copied to the `target/it` directory.
2. **Environment:** A temporary local Maven repository is created to ensure tests use the current build of Protege.
3. **Execution:** The plugin executes a full Maven lifecycle (`clean verify`) on each test project.
4. **Validation:** The build only passes if the generated code in these sub-projects compiles and passes its own
   internal unit tests.

To run only the integration tests, use:

```bash
mvn invoker:run
```

# FAQ

Q. Why is it called Protege?  
A. So it can be pronounced "Proto-J"

## License

This project is licensed under the Apache License, Version 2.0 - see the [LICENSE](LICENSE) file for details.