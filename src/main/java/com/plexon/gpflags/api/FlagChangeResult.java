package com.plexon.gpflags.api;

public record FlagChangeResult(Status status, long claimId, String flagId, Boolean effectiveValue,
                               String transactionId, String eventId, String message) {
    public enum Status { SUCCESS, NO_CHANGE, INVALID_CLAIM, INVALID_FLAG, NOT_AUTHORIZED, PERSISTENCE_FAILED, NOT_PRIMARY_THREAD }
    public boolean success() { return status == Status.SUCCESS || status == Status.NO_CHANGE; }
}
