package com.example.apartment.modules.housing;

import jakarta.validation.constraints.*;

public final class HousingRequests {
    private HousingRequests() {}
    public record BuildingCreate(@NotBlank @Size(max = 64) String name) {}
    public record BuildingUpdate(@NotBlank @Size(max = 64) String name, @NotNull @PositiveOrZero Long version) {}
    public record Enabled(@NotNull Boolean enabled, @NotNull @PositiveOrZero Long version) {}
    public record RoomCreate(@NotNull @Positive Long buildingId, @NotBlank @Size(max = 32) String roomNo,
            @NotNull @Min(1) @Max(100) Integer floor) {}
    public record RoomUpdate(@NotBlank @Size(max = 32) String roomNo, @NotNull @Min(1) @Max(100) Integer floor,
            @NotNull @PositiveOrZero Long version) {}
    public record BedCreate(@NotNull @Positive Long roomId, @NotBlank @Size(max = 32) String bedNo) {}
    public record BedUpdate(@NotBlank @Size(max = 32) String bedNo, @NotNull @PositiveOrZero Long version) {}
}
