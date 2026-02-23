# Oliver's Documentation Tasks

## Memories
- Project uses Maven and Java 21.
- Documentation standards are strict: "Why" over "What".
- `dev.akre.util` contains immutable collection utilities (`Cons`).
- Many missing Javadocs in `compiler` and `parser` packages.
- Use `git blame` to determine the age of a TODO, only remove if stale or if the TODO references deleted code 

## Technical Debt / Dark Zones
- `ProtoUtils.java`: Optimize `messageToString` to track current indentation level and pass StringBuilder to avoid string concatenation.
- `ProtoCodegen.java`: Remove this class. Rename `CodegenMetadata` and use that as the entry point.
- `GenProto.java`: Validate that not only is the `.proto` file generated, but the actual Java implementation is generated as well.

## Pending Approval
- None.

## Approved
- [x] Fix malformed Javadoc in `ProtobufFileDescriptorVisitor.visitProto`.
- [x] Add Javadocs to `MemberTreeVisitor` and `MemberNode`.
- [x] Add Javadocs to `CodegenUtils` package-private methods.
- [x] Add Javadocs to `ProtoUtils` public methods.
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
