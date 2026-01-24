# Protege - AGENTS.md

## Project Context

Protege is a Java-based code generation library for Protocol Buffers. It serves two primary workflows:

1. **Protobuf-to-Java (The Compiler):** Generates enhanced Java source code from `.proto` files. Enhancements include
   Jackson serialization, custom interfaces, Java records, JPA annotation support, and sealed-interface "oneof"
   handling.
2. **Java-to-Protobuf (The Generator):** Generates `.proto` files from Java interfaces annotated with `@GenProto`. These
   files are typically then fed back into the Protobuf-to-Java pipeline to create implementations.

## Project Identity

* **The Name "Protege":** This is a phonetic pun pronounced as **"Proto-J"**. It stands for **Proto**col Buffers for **J
  **ava. When generating documentation or interacting with the project, maintain this branding and recognize that the
  name reflects its primary function.

## Tech Stack

* **Language:** Java 21 (Records, Sealed Classes, Pattern Matching, Switch Expressions).
* **Build System:** Maven (using `maven-invoker-plugin` for IT tests).
* **Parsing:** ANTLR v4 (Grammar: `Protobuf.g4`).
* **Code Generation:** JavaPoet (Palantir fork for modern Java support).
* **Metadata:** Google Protocol Buffers (`FileDescriptorProto`).

## Source Map (Key Paths)

* **Grammar:** `src/main/antlr4/dev/akre/protege/Protobuf.g4`
* **Parsing Logic:** `src/main/java/dev/akre/protege/ProtobufFileDescriptorVisitor.java`
* **Java Generation:** `src/main/java/dev/akre/protege/ProtoCodegen.java`
* **Annotation Processing:** `src/main/java/dev/akre/protege/ProtoCompilerProcessor.java`
* **Integration Tests:** `src/it/`

## Core Components & Agents

### Parsing Phase

* **`MemberTreeVisitor`**: The stage-one parser. It builds a symbol table/graph of messages and enums to resolve type
  dependencies.
* **`ProtobufFileDescriptorVisitor`**: The main parser. Converts ANTLR parse trees into `FileDescriptorProto` objects.
  Entry point: `parseProto`.

### Compilation Phase

* **`ProtoCodegen`**: The primary engine. Generates POJOs, Records, Builders, and Enums from a `FileDescriptor`.
* **`GrpcCodegen`**: Generates gRPC service interfaces, blocking stubs, and async stubs.
* **`ProtoAnnotationProcessor`**: Triggers the Java-to-Proto flow by scanning for `@GenProto`.
* **`ProtoCompilerProcessor`**: A specialized processor that intercepts `.proto` files in the source path to trigger the
  `ProtoCodegen` pipeline.
* **`InterfaceDescriptorFactory`**: The mapping engine that converts Java Reflection types into Protobuf descriptors.

## Development Rules for AI Agents

### 1. Code Style and Versioning

* **Java 21 Strict**: Do not suggest legacy Java syntax. Use records for data carriers and exhaustive `switch`
  expressions for enums/sealed types.
* **Immutability**: Use `dev.akre.util.Cons` (Immutable Linked List) for stack-based traversal during recursion.
* **Statelessness**: Prefer `static` utility methods for transformation logic within visitors and factories.
* **1TBS**: Always put braces around each clause in if-else statements.

### 2. Grammar and AST

* **Visitor Synchronization**: If `Protobuf.g4` changes, you **must** update both `MemberTreeVisitor` and
  `ProtobufFileDescriptorVisitor`.
* **Double-Pass Requirement**: Never attempt to resolve types in a single pass. Always ensure `MemberTreeVisitor` has
  populated the type graph before running the main visitor.

### 3. Code Generation (JavaPoet)

* **No String Templates**: All Java generation must use `JavaPoet` (`TypeSpec`, `MethodSpec`).

### 4. Build and Verification

* **Integration Testing**: Unit tests are insufficient for codegen changes. You must run `mvn verify` to trigger the
  `maven-invoker-plugin` tests in `src/it`.
* **Template Injection**: Do not hardcode versions. The build uses `ProtegeVersion.java` as a template for version
  injection.

### 5. Documentation

* **README Synchronization**: When making changes to the codebase (adding features, changing configuration options,
  etc.), always check `README.md` to see if documentation updates are required to reflect the new functionality or
  changes.

## Common Workflows

* **Adding a feature to generated code**: Modify `ProtoCodegen.java` and add a corresponding test case in `src/it` to
  verify the generated source compiles.
* **Fixing Parsing issues**: Check the ANTLR visitor logic in `ProtobufFileDescriptorVisitor.java`. Use the `Cons` stack
  to debug nested message scoping.