package dev.akre.protege.parser;

/**
 * Thrown when the Protobuf parser or generator encounters an invalid state or input.
 * <p>
 * This exception carries an optional filename to help locate the error source.
 */
public class InvalidProtoException extends RuntimeException {
    private String filename;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message The detail message.
     */
    public InvalidProtoException(String message) {
        super(message);
        this.filename = null;
    }

    /**
     * Constructs a new exception with the specified detail message, filename, and cause.
     *
     * @param message  The detail message.
     * @param filename The filename where the error occurred.
     * @param cause    The cause.
     */
    public InvalidProtoException(String message, String filename, Throwable cause) {
        super(message);
        this.filename = filename;
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message The detail message.
     * @param cause   The cause.
     */
    public InvalidProtoException(String message, Throwable cause) {
        super(message, cause);
        this.filename = null;
    }

    /**
     * Returns a new exception with the specified filename added.
     *
     * @param filename The filename.
     * @return A new exception.
     */
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
