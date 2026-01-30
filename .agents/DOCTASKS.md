# Oliver's Documentation Tasks

## Memories
- Project uses Maven and `mvn javadoc:javadoc` for verification.
- `GenProto` interface can cause name conflicts if `value()` matches the interface name.
- `CodegenUtils` has some manual string manipulation that can be optimized with JavaPoet.
- `ProtoUtils` is a central utility class needing better documentation.

## Pending Approval
- [ ] Fix Javadoc warnings in `CodegenConfig` and `CodegenMetadata` (Large task, observed 100+ warnings).

## Approved Tasks (Session 1)
- [x] Refactor `CodegenUtils.java` to use `ClassName.peerClass`.
- [x] Remove commented-out code in `CodegenConfig.java`.
- [x] Update `GenProto.java` Javadocs to warn about name conflicts.
- [x] Add Javadocs to `ProtoUtils.java`.
