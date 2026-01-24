package dev.akre.protege.parser;

public class TypeNotFoundException extends RuntimeException {
    private final String name;
    private String filename;

    public TypeNotFoundException(String name) {
        super("Type '%s' not found".formatted(name));
        this.name = name;
        this.filename = null;
    }

    public TypeNotFoundException(String name, String filename, Throwable cause) {
        super("Type '%s' not found in '%s'".formatted(name, filename), cause);
        this.name = name;
        this.filename = filename;
    }

    public TypeNotFoundException in(String filename) {
        return new TypeNotFoundException(name, filename, this);
    }

    @Override
    public String getMessage() {
        return filename == null ? "Type '%s' not found".formatted(name) : "Type '%s' not found in '%s'".formatted(name, filename);
    }

}
