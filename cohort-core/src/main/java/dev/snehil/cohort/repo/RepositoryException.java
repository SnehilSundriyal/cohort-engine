package dev.snehil.cohort.repo;

/** Unchecked wrapper so callers above the repository don't deal with SQLException. */
public class RepositoryException extends RuntimeException {
    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
