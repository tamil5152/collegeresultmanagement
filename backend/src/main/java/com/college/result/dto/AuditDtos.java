package com.college.result.dto;

import java.time.Instant;

public final class AuditDtos {

    private AuditDtos() {}

    public record AuditLogResponse(long id, Long actorUserId, String actorUsername, String action, String entityType,
                                   Long entityId, String details, Instant createdAt) {}
}
