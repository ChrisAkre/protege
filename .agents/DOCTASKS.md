# Oliver's Documentation Tasks

## Memories
- Project uses Maven and Java 21.
- Documentation standards are strict: "Why" over "What".
- `dev.akre.util` contains immutable collection utilities (`Cons`).
- Many missing Javadocs in `compiler` and `parser` packages.
- Use `git blame` to determine the age of a TODO, only remove if stale or if the TODO references deleted code 

## Technical Debt / Dark Zones
- `ProtoCodegen.java`: Refactor to merge into `CodegenMetadata` as the new entry point.
- `ProtoUtils.java`: Optimize `messageToString` to avoid recursive string splitting/indentation.
- `GenProto.java`: Validate that `@GenProto` generates not just the `.proto` file but also the Java implementation.

## Pending Approval
- None.

## Approved
- [x] Fix Javadoc in `ProtobufFileDescriptorVisitor.java`.
