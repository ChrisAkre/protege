# Oliver's Documentation Tasks

## Memories
- Project uses Maven and Java 21.
- Documentation standards are strict: "Why" over "What".
- `dev.akre.util` contains immutable collection utilities (`Cons`).
- Many missing Javadocs in `compiler` and `parser` packages.
- Use `git blame` to determine the age of a TODO, only remove if stale or if the TODO references deleted code 

## Technical Debt / Dark Zones
- Refactoring `ProtoCodegen` to merge into `CodegenMetadata` (making it the new entry point).
- Validating that `@GenProto` correctly generates the Java implementation (not just the `.proto` file).
- Optimize `messageToString` in `ProtoUtils` to track current indentation level and pass it directly to `enumToString` and `fieldToString`.

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
- [x] Remove stale TODOs in `GenProto.java`, `ProtoCodegen.java`, `ProtoUtils.java` and log them as technical debt.
- [x] Fix Javadoc warnings across `Field.java`, `EnumCodegen.java`, `FieldCodegen.java`, `MessageCodegen.java`, `Cons.java`, `UnmodifiableCons.java` and other classes.
