package com.example.apartment.support.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditWriter {
    private final AuditLogMapper logs;
    private final ObjectMapper json;
    public AuditWriter(AuditLogMapper logs, ObjectMapper json) { this.logs = logs; this.json = json; }

    // Callers supply explicit public snapshots, never entities containing credentials.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long actor, String action, String type, long target, Object before, Object after) {
        try {
            logs.insert(actor, action, type, target, before == null ? null : json.writeValueAsString(before), json.writeValueAsString(after));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("审计记录无法生成");
        }
    }
}
