package com.example.apartment.modules.hygiene;

import jakarta.validation.constraints.*;

public final class BatchRequests {
    private BatchRequests() {}
    public enum Action { CLOSE, REOPEN, SUSPEND, RESTORE }
    public record State(@NotNull @Min(0) Long version, @NotNull Action action, @NotBlank @Size(max=500) String reason) {}
    public record Roster(@NotNull @Min(0) Long version, @NotBlank @Size(max=500) String reason) {}
}
