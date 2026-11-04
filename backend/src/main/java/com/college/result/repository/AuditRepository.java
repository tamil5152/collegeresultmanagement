package com.college.result.repository;

import com.college.result.common.DbUtil;
import com.college.result.common.Paging;
import com.college.result.dto.AuditDtos.AuditLogResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {

    private static final RowMapper<AuditLogResponse> MAPPER = (rs, i) -> new AuditLogResponse(rs.getLong("id"),
            DbUtil.nullableLong(rs, "actor_user_id"), rs.getString("actor_username"), rs.getString("action"),
            rs.getString("entity_type"), DbUtil.nullableLong(rs, "entity_id"), rs.getString("details"),
            DbUtil.instant(rs, "created_at"));

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Long actorId, String actorUsername, String action, String entityType, Long entityId,
                       String details) {
        jdbc.update("INSERT INTO audit_logs (actor_user_id, actor_username, action, entity_type, entity_id, details) "
                + "VALUES (?, ?, ?, ?, ?, ?)", actorId, actorUsername, action, entityType, entityId, details);
    }

    private static final String WHERE = " WHERE (? = '' OR action = ?) AND (actor_username LIKE ? OR entity_type LIKE ?)";

    public List<AuditLogResponse> search(String action, String search, Paging paging) {
        String like = Paging.like(search);
        String a = action == null ? "" : action;
        return jdbc.query("SELECT id, actor_user_id, actor_username, action, entity_type, entity_id, details, created_at "
                        + "FROM audit_logs" + WHERE + " ORDER BY id DESC LIMIT ? OFFSET ?", MAPPER,
                a, a, like, like, paging.size(), paging.offset());
    }

    public long count(String action, String search) {
        String like = Paging.like(search);
        String a = action == null ? "" : action;
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs" + WHERE, Long.class, a, a, like, like);
        return n == null ? 0 : n;
    }
}
