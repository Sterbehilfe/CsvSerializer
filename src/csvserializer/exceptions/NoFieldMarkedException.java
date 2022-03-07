package csvserializer.exceptions;

/**
 * Thrown if no field is marked in the item class.
 */
public class NoFieldMarkedException extends Exception {

    /**
     * The basic constructor that takes in a custom exception message.
     *
     * @param message the custom message
     */
    public NoFieldMarkedException(String message) {
        super(message);
    }
}
