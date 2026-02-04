# Oliver's Doc Tasks

## Memories
* Project is "Protege" (Proto-J).
* Uses Java 21, Maven, ANTLR v4, JavaPoet.
* Two main flows: Proto -> Java (Compiler), Java -> Proto (Generator).
* `src/it` has integration tests via `maven-invoker-plugin`.

## Pending Approval
* [ ] Refactor ProtoUtils.messageToString to use a single StringBuilder and pass indentation level. (Optimization)
* [ ] Refactor ProtoCodegen: Evaluate merging functionality into CodegenMetadata. (Refactoring)
* [ ] Verify generated code correctness for @GenProto interfaces. (Verification)

## Approved
* [x] Review and update `README.md`
* [x] Review and update `AGENTS.md`
* [x] Scan for and prune "Zombies" (TODOs, commented out code)
* [x] Identify and illuminate "Dark Zones"
* [ ] Verify Javadoc `mvn javadoc:javadoc`
