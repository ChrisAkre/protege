# Oliver's Documentation Tasks

## Memories
- Project uses Maven and Java 21.
- Documentation standards are strict: "Why" over "What".
- `dev.akre.util` contains immutable collection utilities (`Cons`).
- Many missing Javadocs in `compiler` and `parser` packages.
- Use `git blame` to determine the age of a TODO, only remove if stale or if the TODO references deleted code 

## Pending Approval
- [ ] Validate that not only is the .proto file generated, but the actual java implementation is generated as well (`GenProto` validation).
- [ ] Remove `ProtoCodegen` class and use `CodegenMetadata` as entry point.

## Approved
- [x] Refactor `ProtoUtils` to use `StringBuilder` and pass indentation level.
- [x] Deprecate `ProtoCodegen` and document replacement `CodegenMetadata`.
- [x] Fix Javadoc format in `ProtobufFileDescriptorVisitor`.
- [x] Remove TODO from `GenProto`.
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
