package com.example.apartment.support.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Narrow public snapshots for batch governance; never exposes private drafts or unrelated audit records. */
@Service
public class BatchChangeQuery {
    public record Row(long id,long actorId,String action,String beforeData,String afterData,Instant createdAt) {}
    public record Change(long id,long actorId,String action,JsonNode before,JsonNode after,Instant createdAt) {}
    public record Page(List<Change> items,long total,int page,int size) {}
    private final AuditLogMapper logs;
    private final ObjectMapper json;
    public BatchChangeQuery(AuditLogMapper logs,ObjectMapper json){this.logs=logs;this.json=json;}
    @Transactional(readOnly=true)
    public Page list(long id,int page,int size){
        return new Page(logs.batchChanges(id,size,(long)(page-1)*size).stream().map(row -> {
            try { return new Change(row.id(),row.actorId(),row.action(),row.beforeData()==null?null:json.readTree(row.beforeData()),json.readTree(row.afterData()),row.createdAt()); }
            catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new IllegalStateException("Cannot decode batch change",failure);}
        }).toList(),logs.countBatchChanges(id),page,size);
    }
}
