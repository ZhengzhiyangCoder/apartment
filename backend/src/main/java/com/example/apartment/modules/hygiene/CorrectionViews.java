package com.example.apartment.modules.hygiene;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public final class CorrectionViews {
    private CorrectionViews() {}
    public record Row(long id,long scoreId,long version,long actorId,String actorName,String reason,String beforeData,String afterData,Instant createdAt) {}
    public record Entry(long id,long scoreId,long version,long actorId,String actorName,String reason,JsonNode before,JsonNode after,Instant createdAt) {}
    public record Page(List<Entry> items,long total,int page,int size) {}
}
