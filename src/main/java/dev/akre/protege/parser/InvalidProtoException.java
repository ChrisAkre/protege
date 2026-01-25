package dev.akre.protege.parser;

public class InvalidProtoException extends RuntimeException {
    private String filename;

    public InvalidProtoException(String message) {
        super(message);
        this.filename = null;
    }

    public InvalidProtoException(String message, String filename, Throwable cause) {
        super(message);
        this.filename = filename;
    }

    public InvalidProtoException(String message, Throwable cause) {
        super(message, cause);
        this.filename = null;
    }

    public InvalidProtoException in(String filename) {
        InvalidProtoException result = new InvalidProtoException(getMessage(), filename, this);
        result.setStackTrace(this.getStackTrace());
        return result;
    }

    @Override
    public String getMessage() {
        return filename == null ? super.getMessage() : "'%s' when processing %s".formatted(super.getMessage(), filename);
    }
}
