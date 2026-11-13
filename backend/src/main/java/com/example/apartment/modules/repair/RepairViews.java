package com.example.apartment.modules.repair;

import java.time.Instant;
import java.util.List;

public final class RepairViews {
    private RepairViews() {}
    public enum Category { PLUMBING, ELECTRICAL, DOORS, FURNITURE, OTHER }
    public enum Status { PENDING_ASSIGNMENT, PENDING, IN_PROGRESS, COMPLETED, CANCELLED }
    public record Order(long id, long submittedBy, String studentName, long residencyId, long studentId,
            long buildingId, String buildingName, long roomId, String roomNo, long bedId, String bedNo,
            Category category, String description, Status status, Instant createdAt, Instant updatedAt) {}
    public record Image(long id, long orderId, long byteSize) {
        public String getUrl() { return "/api/repairs/" + orderId + "/images/" + id; }
    }
    public record Log(long id, Status fromStatus, Status toStatus, long actorId, String actorName, String note, Instant createdAt) {}
    public record Page(List<Order> items, long total, int page, int size) {}
    public record Detail(@com.fasterxml.jackson.annotation.JsonUnwrapped Order order, List<Image> images, List<Log> logs) {}
}
