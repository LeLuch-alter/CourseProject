package kz.kvit.repository;

/** A read or save in Supabase failed. The message is written for the user. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
