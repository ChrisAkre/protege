# Oliver's Documentation Tasks

## Memories
- Project uses Maven and Java 21.
- Documentation standards are strict: "Why" over "What".
- `dev.akre.util` contains immutable collection utilities (`Cons`).
- Many missing Javadocs in `compiler` and `parser` packages.
- Use `git blame` to determine the age of a TODO, only remove if stale or if the TODO references deleted code 

## Technical Debt / Dark Zones
- `ProtoCodegen.java`: Remove `ProtoCodegen` class. Rename `CodegenMetadata` and use that as the entry point, the builder will take the file descriptor and the options.
- `GenProto.java`: Validate that not only is the `.proto` file generated, but the actual java implementation is generated as well.
- `ProtoUtils.java`: Optimize string generation by refactoring `messageToString` to track current indentation level and passing the string builder and indentation level to `enumToString` and `fieldToString`.

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
- [x] Add checkstyle plugin config to `pom.xml`.
- [x] Read `.agents/DOCTASKS.md` and add missing categories: `Technical Debt / Dark Zones`, `Pending Approval`, and `Approved`.
- [x] Remove `TODO` in `src/main/java/dev/akre/protege/compiler/ProtoCodegen.java`, log in `DOCTASKS.md`.
- [x] Remove `TODO` in `src/main/java/dev/akre/protege/GenProto.java`, log in `DOCTASKS.md`.
- [x] Remove `TODO` in `src/main/java/dev/akre/protege/ProtoUtils.java`, log in `DOCTASKS.md`.
- [x] Rephrase and format the Javadoc associated with the TODO on line 81 of `ProtobufFileDescriptorVisitor.java` and remove the TODO comment.
