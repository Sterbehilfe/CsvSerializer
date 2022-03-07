package csvserializer.exceptions;

/**
 * Thrown if a field has an unserializable or undeserializable type.
 */
public class UnserializableTypeException extends Exception {

    /**
     * The basic constructor that takes in a custom exception message.
     *
     * @param message the custom message
     */
    public UnserializableTypeException(String message) {
        super(message);
    }
}
