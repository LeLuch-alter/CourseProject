package kz.kvit.service;

/** Thrown when user input breaks a business rule. The message is shown to the user as is. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
