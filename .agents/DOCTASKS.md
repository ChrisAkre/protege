# Oliver's Documentation Tasks

## Memories
- Project uses Maven and Java 21.
- Documentation standards are strict: "Why" over "What".
- `dev.akre.util` contains immutable collection utilities (`Cons`).
- Use `git blame` to determine the age of a TODO, only remove if stale or if the TODO references deleted code 
- `GrpcCodegen` has incomplete implementations for client-streaming and bidirectional streaming methods.

## Technical Debt / Dark Zones
- [ ] `ProtoCodegen.java`: Refactor to merge into `CodegenMetadata` (make it the entry point).
- [ ] `GenProto.java`: Add validation to ensure Java implementation is generated along with `.proto` file.
- [ ] `ProtoUtils.java`: Optimize `messageToString` to track indentation level.
- [ ] `GrpcCodegen.java`: Implement Client Streaming and Bidi Streaming support.

## Pending Approval
- None.

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
- [x] Fix informal Javadoc in `ProtobufFileDescriptorVisitor.java`.
- [x] Add Javadocs to `GrpcCodegen.java` explaining current limitations.
- [x] Remove zombies (commented out code) in `CodegenConfig.java`.
- [x] Fix missing Javadoc params/returns in `CodegenConfig.java`, `CodegenUtils.java`, `Cons.java`, `UnmodifiableCons.java`, `EnumCodegen.java`, `Field.java`.
