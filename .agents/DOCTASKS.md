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
- [x] Fix double semicolon bug in `OuterClassCodegen.java` and update `CanaryTest`.
- [x] Refactor `CodegenUtils.getOrBuilderType` to use `ClassName.peerClass`.
- [x] Document "Dark Zones" in `CodegenUtils.java` (`getWriteCondition`, `relativeToProtoPackage`).
- [ ] Remove commented out code in `CodegenUtils.java`, `CodegenMethods.java`, `OneofCodegen.java`, `ProtobufFileDescriptorVisitor.java`.
- [ ] Add/Improve Javadoc for `OneofCodegen.java` and `CodegenMethods.java`.
- [ ] Fix confusing comments and Javadoc in `GenProto.java`.
- [ ] Add Javadoc to `ProtoCodegen.java` (entry point).
- [ ] Add Javadoc to `ProtobufFileDescriptorVisitor.java` (scope handling).
