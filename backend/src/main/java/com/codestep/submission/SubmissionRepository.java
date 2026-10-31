package com.codestep.submission;

import com.codestep.judge.SubmissionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SubmissionRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Submission> rowMapper = this::mapRow;

    public SubmissionRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public Optional<Submission> createIfUnderLimit(String clientId, String problemSlug, String code,
                                                   boolean samplesOnly, int maxInFlight) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:clientId))")
                .param("clientId", clientId)
                .query()
                .singleRow();
        if (countInFlight(clientId) >= maxInFlight) {
            return Optional.empty();
        }
        return Optional.of(create(clientId, problemSlug, code, samplesOnly));
    }

    public Submission create(String clientId, String problemSlug, String code, boolean samplesOnly) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO submissions (id, client_id, problem_slug, code, samples_only, status)
                        VALUES (:id, :clientId, :slug, :code, :samplesOnly, 'QUEUED')
                        """)
                .param("id", id)
                .param("clientId", clientId)
                .param("slug", problemSlug)
                .param("code", code)
                .param("samplesOnly", samplesOnly)
                .update();
        return findById(id).orElseThrow();
    }

    public Optional<Submission> findById(UUID id) {
        return jdbc.sql("SELECT * FROM submissions WHERE id = :id")
                .param("id", id)
                .query(rowMapper)
                .optional();
    }

    public List<Submission> findHistory(String clientId, String problemSlug, int limit) {
        return jdbc.sql("""
                        SELECT * FROM submissions
                        WHERE client_id = :clientId AND problem_slug = :slug
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                .param("clientId", clientId)
                .param("slug", problemSlug)
                .param("limit", limit)
                .query(rowMapper)
                .list();
    }

    public int countInFlight(String clientId) {
        return jdbc.sql("SELECT count(*) FROM submissions WHERE client_id = :clientId AND status <> 'DONE'")
                .param("clientId", clientId)
                .query(Integer.class)
                .single();
    }

    public int countByStatus(SubmissionStatus status) {
        return jdbc.sql("SELECT count(*) FROM submissions WHERE status = :status")
                .param("status", status.name())
                .query(Integer.class)
                .single();
    }

    public boolean claimForJudging(UUID id, Duration staleAfter) {
        return jdbc.sql("""
                        UPDATE submissions SET status = 'RUNNING', started_at = now()
                        WHERE id = :id
                          AND (status = 'QUEUED' OR (status = 'RUNNING' AND started_at < :staleBefore))
                        """)
                .param("id", id)
                .param("staleBefore", Timestamp.from(Instant.now().minus(staleAfter)))
                .update() == 1;
    }

    public void complete(UUID id, SubmissionResult result) {
        jdbc.sql("""
                        UPDATE submissions
                        SET status = 'DONE', verdict = :verdict, passed = :passed, total = :total,
                            result = CAST(:result AS jsonb), finished_at = now()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("verdict", result.verdict().name())
                .param("passed", result.passed())
                .param("total", result.total())
                .param("result", toJson(result))
                .update();
    }

    public List<UUID> findStuck(Duration olderThan) {
        return jdbc.sql("""
                        SELECT id FROM submissions
                        WHERE (status = 'QUEUED' AND created_at < :before)
                           OR (status = 'RUNNING' AND started_at < :before)
                        ORDER BY created_at
                        LIMIT 50
                        """)
                .param("before", Timestamp.from(Instant.now().minus(olderThan)))
                .query(UUID.class)
                .list();
    }

    private Submission mapRow(ResultSet rs, int rowNum) throws SQLException {
        String result = rs.getString("result");
        return new Submission(
                rs.getObject("id", UUID.class),
                rs.getString("client_id"),
                rs.getString("problem_slug"),
                rs.getString("code"),
                rs.getBoolean("samples_only"),
                SubmissionStatus.valueOf(rs.getString("status")),
                result == null ? null : fromJson(result),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("finished_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String toJson(SubmissionResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private SubmissionResult fromJson(String json) {
        try {
            return mapper.readValue(json, SubmissionResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
