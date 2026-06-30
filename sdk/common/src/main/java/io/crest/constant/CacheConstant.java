package io.crest.constant;

/**
 * 系统缓存键常量集合，按业务域拆分不同缓存命名空间
 */
public class CacheConstant {
    /**
     * 用户、登录态、权限和单点登录相关缓存键
     */
    public static class UserCacheConstant {
        /**
         * 用户数量统计缓存键
         */
        public static final String USER_COUNT_CACHE = "crest_v1_user_count";
        /**
         * 用户分层统计缓存键
         */
        public static final String USER_ECHELON_CACHE = "crest_v1_user_echelon";
        /**
         * 登录用户信息缓存键
         */
        public static final String LOGIN_USER_CACHE = "crest_v1_login_user_cache";
        /**
         * 用户角色关系缓存键
         */
        public static final String USER_ROLES_CACHE = "crest_v1_user_roles";
        /**
         * 用户业务权限缓存键
         */
        public static final String USER_BUSI_PERS_CACHE = "crest_v1_user_busi_pers";
        /**
         * 用户交互权限缓存键
         */
        public static final String USER_BUSI_PERS_INTERACTIVE_CACHE = "crest_v1_user_busi_pers_interactive";
        /**
         * 用户社区语言缓存键
         */
        public static final String USER_COMMUNITY_LANGUAGE = "crest_v1_user_community_language";
        /**
         * 数据源加密对称密钥缓存键
         */
        public static final String Symmetric_Key = "crest_datasource_symmetric_key";
        /**
         * 单点登录状态缓存键
         */
        public static final String SSO_STATE_CACHE = "crest_sso_state";
        /**
         * 单点登录票据缓存键
         */
        public static final String SSO_TICKET_CACHE = "crest_sso_ticket";
    }

    /**
     * 角色权限相关缓存键
     */
    public static class RoleCacheConstant {
        /**
         * 角色菜单权限缓存键
         */
        public static final String ROLE_MENU_PERS_CACHE = "crest_v1_role_menu_pers";
        /**
         * 角色业务权限缓存键
         */
        public static final String ROLE_BUSI_PERS_CACHE = "crest_v1_role_busi_pers";
        /**
         * 角色交互权限缓存键
         */
        public static final String ROLE_BUSI_PERS_INTERACTIVE_CACHE = "crest_v1_role_busi_pers_interactive";
    }

    /**
     * 组织资源相关缓存键
     */
    public static class OrgCacheConstant {
        /**
         * 组织全局资源缓存键
         */
        public static final String ORG_GLOBAL_RESOURCE_CACHE = "crest_v1_org_global_resource";
        /**
         * 全量组织资源标识缓存键
         */
        public static final String ALL_OID_FLAG_RESOURCE_CACHE = "crest_v1_all_oid_flag_resource";


    }

    /**
     * 地图、密钥和全局安全配置相关缓存键
     */
    public static class CommonCacheConstant {
        /**
         * 世界地图缓存键
         */
        public static final String WORLD_MAP_CACHE = "crest_v1_world_map";
        /**
         * 自定义地理数据缓存键
         */
        public static final String CUSTOM_GEO_CACHE = "crest_v1_custom_geo";
        /**
         * 非对称密钥缓存键
         */
        public static final String RSA_CACHE = "crest_v1_rsa";
        /**
         * 权限菜单编号缓存键
         */
        public static final String PER_MENU_ID_CACHE = "crest_v1_per_menu_id";
        /**
         * 全局多因子认证配置缓存键
         */
        public static final String GLOBAL_MFA_CACHE = "crest_v1_global_mfa";
        /**
         * 全局消息认证码配置缓存键
         */
        public static final String GLOBAL_HMAC_CACHE = "crest_v1_global_hmac";
    }

}
