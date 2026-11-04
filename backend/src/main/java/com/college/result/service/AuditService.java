package com.college.result.service;

import com.college.result.repository.AuditRepository;
import com.college.result.security.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Writes audit rows. Called inside the caller's transaction so the audit entry commits or rolls back with the change. */
@Service
public class AuditService {

    private final AuditRepository repository;
    private final ObjectMapper mapper;

    public AuditService(AuditRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public void log(AuthUser actor, String action, String entityType, Long entityId, Object details) {
        repository.insert(actor == null ? null : actor.userId(), actor == null ? null : actor.username(),
                action, entityType, entityId, toJson(details));
    }

    public String toJson(Object details) {
        if (details == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise audit details", e);
        }
    }
}
