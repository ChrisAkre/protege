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
- [x] Add Javadocs to `dev.akre.util` package (`Cons`, `UnmodifiableCons`).
- [x] Add Javadocs to `dev.akre.protege` root package (`Field`).
- [x] Add Javadocs to `dev.akre.protege.compiler` package (`CodegenConfig`, `CodegenUtils`, `EnumCodegen`).
- [x] Add Javadocs to `dev.akre.protege.parser` package.
- [x] Refactor `CodegenUtils.getOrBuilderType` to use `ClassName.peerClass`.
- [x] Document "Dark Zones" in `CodegenUtils.java` (`getWriteCondition`, `relativeToProtoPackage`).
- [x] Remove commented out code in `CodegenUtils.java`, `CodegenMethods.java`, `OneofCodegen.java`, `ProtobufFileDescriptorVisitor.java`.
- [x] Add/Improve Javadoc for `OneofCodegen.java` and `CodegenMethods.java`.
- [x] Add Javadocs to `CodegenConfig.java` and `CodegenMetadata.java`.
- [ ] Add Javadocs to `FieldCodegen.java`.
- [ ] Add Javadocs to `GrpcCodegen.java`.
- [ ] Add Javadocs to `InterfaceDescriptorFactory.java`.
- [ ] Add Javadocs to `InvalidProtoException.java`.
- [ ] Add Javadocs to `MemberTreeVisitor.java`.
- [ ] Add Javadocs to `MessageCodegen.java`.
- [ ] Add Javadocs to `MessageMethods.java`.
