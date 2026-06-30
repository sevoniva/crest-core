package io.crest.share.manage;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
// 封装当前业务的持久化和查询逻辑
public class ShareLinkAccessManage {

    private static final String INNER_JUMP_TARGET_COUNT_SQL = """
            SELECT COUNT(1)
            FROM (
                SELECT i.id
                FROM core_visualization_jump j
                JOIN core_visualization_jump_action i ON i.link_jump_id = j.id
                WHERE j.source_dv_id = ?
                  AND i.target_dv_id = ?
                  AND COALESCE(j.checked, 0) = 1
                  AND COALESCE(i.checked, 0) = 1
                  AND i.link_type = 'inner'
                UNION ALL
                SELECT i.id
                FROM core_visualization_jump_snapshot j
                JOIN core_visualization_jump_action_snapshot i ON i.link_jump_id = j.id
                WHERE j.source_dv_id = ?
                  AND i.target_dv_id = ?
                  AND COALESCE(j.checked, 0) = 1
                  AND COALESCE(i.checked, 0) = 1
                  AND i.link_type = 'inner'
            ) t
            """;

    private final JdbcTemplate jdbcTemplate;

    public ShareLinkAccessManage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean canAccessWithShareResource(Long shareResourceId, Long requestResourceId) {
        if (ObjectUtils.anyNull(shareResourceId, requestResourceId)) {
            return false;
        }
        if (shareResourceId.equals(requestResourceId)) {
            return true;
        }
        Long count = jdbcTemplate.queryForObject(
                INNER_JUMP_TARGET_COUNT_SQL,
                Long.class,
                shareResourceId,
                requestResourceId,
                shareResourceId,
                requestResourceId
        );
        return count != null && count > 0;
    }
}
