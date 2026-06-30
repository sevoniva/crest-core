package io.crest.substitute.permissions.auth;

import java.util.Set;

/**
 * 平台权限策略，封装当前用户和资源范围的授权判断
 */
public class PlatformPermissionPolicy {

    /**
     * 当前用户是否为系统管理员
     */
    private final boolean systemAdmin;
    /**
     * 当前用户 ID
     */
    private final Long currentUserId;
    /**
     * 直接授予用户的权限集合
     */
    private final Set<String> userGrants;
    /**
     * 用户角色授予的权限集合
     */
    private final Set<String> roleGrants;
    /**
     * 资源所属组织 ID
     */
    private final Long resourceOrgId;

    /**
     * 创建平台权限策略上下文
     */
    public PlatformPermissionPolicy(boolean systemAdmin, Long currentUserId, Set<String> userGrants,
                                    Set<String> roleGrants, Long resourceOrgId) {
        this.systemAdmin = systemAdmin;
        this.currentUserId = currentUserId;
        this.userGrants = userGrants == null ? Set.of() : userGrants;
        this.roleGrants = roleGrants == null ? Set.of() : roleGrants;
        this.resourceOrgId = resourceOrgId;
    }

    /**
     * 判断当前策略下是否允许访问指定动作
     */
    public boolean canAccess(Long creatorId, Set<Long> roleIds, String action) {
        if (systemAdmin) {
            return true;
        }
        if (currentUserId != null && creatorId != null && currentUserId.equals(creatorId)) {
            return true;
        }
        String normalizedAction = normalize(action);
        return hasGrant(userGrants, normalizedAction) || hasGrant(roleGrants, normalizedAction);
    }

    /**
     * 返回资源所属组织 ID
     */
    public Long resourceOrgId() {
        return resourceOrgId;
    }

    /**
     * 判断权限集合是否包含指定动作或管理权限
     */
    private boolean hasGrant(Set<String> grants, String action) {
        return grants.contains("manage") || grants.contains(action);
    }

    /**
     * 标准化权限动作，空动作默认按读取处理
     */
    private String normalize(String action) {
        if (action == null || action.isBlank()) {
            return "read";
        }
        return action.trim().toLowerCase();
    }
}
