package com.example.apartment.modules.housing;

import java.time.Instant;

public record ResidencyView(long id, long studentId, String studentNo, String fullName,
        long buildingId, String buildingName, long roomId, String roomNo, long bedId, String bedNo,
        Instant startedAt, Instant endedAt, String endReason, String endNote) {}
