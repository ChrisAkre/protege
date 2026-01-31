# Oliver's Documentation Tasks

## Memories
- Project uses Maven and Java 21.
- Documentation standards are strict: "Why" over "What".
- `dev.akre.util` contains immutable collection utilities (`Cons`).
- Many missing Javadocs in `compiler` and `parser` packages.

## Pending Approval
- None.

## Approved
- [x] Fix Javadoc error in `CodegenUtils.java`.
- [x] Add Javadocs to `dev.akre.util` package.
- [x] Add Javadocs to `dev.akre.protege` root package.
- [x] Add Javadocs to `dev.akre.protege.compiler` package.
- [x] Add Javadocs to `dev.akre.protege.parser` package.
- [ ] Remove commented out code in `CodegenUtils.java`, `CodegenMethods.java`, `OneofCodegen.java`, `ProtobufFileDescriptorVisitor.java`.
- [ ] Add/Improve Javadoc for `OneofCodegen.java` and `CodegenMethods.java`.
- [ ] Fix double semicolon bug in `OuterClassCodegen.java` and update `CanaryTest`.
- [ ] Refactor `CodegenUtils.getOrBuilderType` to use `ClassName.peerClass`.
- [ ] Fix confusing comments and Javadoc in `GenProto.java`.
- [ ] Add Javadoc to `ProtoCodegen.java` (entry point).
- [ ] Add Javadoc to `ProtobufFileDescriptorVisitor.java` (scope handling).
- [ ] Document "Dark Zones" in `CodegenUtils.java` (`getWriteCondition`, `relativeToProtoPackage`).
