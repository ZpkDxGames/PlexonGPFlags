package net.plexon.claimflags.api;

/** @deprecated Use com.plexon.gpflags.api.FlagChangeResult. */
@Deprecated(forRemoval = false)
public record FlagChangeResult(Status status, long claimId, String flagId, Boolean effectiveValue,
                               String transactionId, String eventId, String message) {
    public enum Status { SUCCESS, NO_CHANGE, NOT_PRIMARY_THREAD, INVALID_CLAIM, INVALID_FLAG, NOT_AUTHORIZED, PERSISTENCE_FAILED }
    public boolean successful() { return status == Status.SUCCESS; }
    public boolean accepted() { return status == Status.SUCCESS || status == Status.NO_CHANGE; }
}
