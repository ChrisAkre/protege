# Oliver's Documentation Tasks

## Memories
- Project uses Maven and Java 21.
- Documentation standards are strict: "Why" over "What".
- `dev.akre.util` contains immutable collection utilities (`Cons`).
- Many missing Javadocs in `compiler` and `parser` packages.
- Use `git blame` to determine the age of a TODO, only remove if stale or if the TODO references deleted code 

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
- [x] Add Javadocs (param/return) to default methods in `CodegenConfig.java`.
- [x] Add Javadoc to `MessageConfig` interface and remove commented-out code in `CodegenConfig.java`.
- [x] Add Javadocs to `CodegenUtils.java` (`PROTO_TYPE_TO_TYPE_NAME`, `relativeToProtoPackage`, `generateClearOneofCode`).
- [x] Add Javadocs to `Cons.java` (constructor, `nil()`, `of()`, `reversed()`, `descendingStream()`).
- [x] Add Javadocs to `UnmodifiableCons.java` (`reversed()`, `descendingIterator()`).
- [x] Add Javadocs to `EnumCodegen.java` (`getEnumName`, `outerClassName`, `parentNames`).
- [x] Add Javadocs to `Field.java` (`ProtoFieldType` enum).
