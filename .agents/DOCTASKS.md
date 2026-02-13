# Oliver's Documentation Tasks

## Memories
- Project uses Maven and Java 21.
- Documentation standards are strict: "Why" over "What".
- `dev.akre.util` contains immutable collection utilities (`Cons`).
- Many missing Javadocs in `compiler` and `parser` packages.
- Use `git blame` to determine the age of a TODO, only remove if stale or if the TODO references deleted code 
- `ProtoUtils.indent` uses inefficient `lines().collect()` stream processing.
- `InterfaceDescriptorFactory` contains complex recursive logic for type mapping ("Dark Zone").
- `ProtoCodegen` is slated for removal/refactoring but currently acts as the main entry point.

## Pending Approval
- [ ] Refactor `ProtoCodegen` to use `CodegenMetadata` directly (Technical Debt).
- [ ] Validate generated Java implementation in `GenProto` (Feature Request).

## Approved
- [x] Fix Javadoc error in `CodegenUtils.java`.
- [x] Add Javadocs to `dev.akre.util` package.
- [x] Add Javadocs to `dev.akre.protege` root package.
- [x] Add Javadocs to `dev.akre.protege.compiler` package.
- [x] Add Javadocs to `dev.akre.protege.parser` package.
- [x] Refactor `CodegenUtils.getOrBuilderType` to use `ClassName.peerClass`.
- [x] Document "Dark Zones" in `CodegenUtils.java` (`getWriteCondition`, `relativeToProtoPackage`).
- [x] Remove commented out code in `CodegenUtils.java`, `CodegenMethods.java`, `OneofCodegen.java`, `ProtobufFileDescriptorVisitor.java`.
- [x] Add/Improve Javadoc for `OneofCodegen.java` and `CodegenMethods.java`.
- [x] Add Javadocs to `CodegenConfig.java` and `CodegenMetadata.java`.
- [x] Optimize `ProtoUtils.messageToString` to avoid string concatenation and stream overhead.
- [x] Fix Javadoc format in `ProtobufFileDescriptorVisitor.visitProto`.
- [x] Document `ProtoCodegen` deprecation status.
- [x] Document `InterfaceDescriptorFactory` complex recursive methods (`processType`, `fillMessageBuilder`).
