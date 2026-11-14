package kz.kvit.repository;

/** A read or save in Supabase failed. The message is written for the user. */
public class DataAccessException extends RuntimeException {
    // PostgREST / Postgres error code such as "PGRST205" (table missing); empty when unknown.
    private final String code;

    public DataAccessException(String message) {
        this(message, "");
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
        this.code = "";
    }

    public DataAccessException(String message, String code) {
        super(message);
        this.code = code == null ? "" : code;
    }

    public String getCode() {
        return code;
    }

    /** The table or column is not in the database yet (a newer SQL script was not run). */
    public boolean isMissingSchema() {
        return code.equals("PGRST205") || code.equals("PGRST204") || code.equals("42703") || code.equals("42P01");
    }
}
