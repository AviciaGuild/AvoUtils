package info.avicia.avoutils.core.api;

/** An API reply with an unexpected HTTP status; the message is the API's error text when it gave one. */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
