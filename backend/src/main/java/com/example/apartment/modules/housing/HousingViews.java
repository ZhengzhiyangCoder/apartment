package com.example.apartment.modules.housing;

public final class HousingViews {
    private HousingViews() {}
    public record Building(long id, String name, boolean enabled, long version) {}
    public record Room(long id, long buildingId, String buildingName, boolean buildingEnabled, String roomNo, int floor, boolean enabled, long version) {}
    public record Bed(long id, long roomId, long buildingId, String buildingName, String roomNo, boolean buildingEnabled, boolean roomEnabled, String bedNo, boolean enabled, long version) {}
}
