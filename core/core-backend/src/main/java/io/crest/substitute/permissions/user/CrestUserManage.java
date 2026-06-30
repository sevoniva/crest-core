package io.crest.substitute.permissions.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
import io.crest.api.permissions.user.dto.EnableSwitchRequest;
import io.crest.api.permissions.user.dto.UserCreator;
import io.crest.api.permissions.user.dto.UserEditor;
import io.crest.api.permissions.user.dto.UserGridRequest;
import io.crest.api.permissions.user.vo.CurUserVO;
import io.crest.api.permissions.user.vo.UserFormVO;
import io.crest.api.permissions.user.vo.UserGridVO;
import io.crest.api.permissions.user.vo.UserGridRoleItem;
import io.crest.api.permissions.user.vo.UserImportVO;
import io.crest.api.permissions.user.vo.UserItem;
import io.crest.constant.SystemSettingConstants;
import io.crest.exception.CrestException;
import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.substitute.permissions.user.model.CrestUser;
import io.crest.substitute.permissions.user.model.SsoUserProfile;
import io.crest.substitute.permissions.user.model.UserImportRow;
import io.crest.system.manage.SysParameterManage;
import io.crest.system.sso.SsoIdentityAction;
import io.crest.system.sso.SsoIdentityDecision;
import io.crest.system.sso.SsoIdentityProfile;
import io.crest.utils.AuthUtils;
import io.crest.utils.IDUtils;
import io.crest.utils.Md5Utils;
import io.crest.utils.PasswordEncoder;
import io.crest.utils.PasswordValidator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// 内置用户管理服务，负责本地用户、单点登录用户和权限绑定的持久化操作
@Component("crestUserManage")
public class CrestUserManage {

    public static final String AUTH_TYPE_LOCAL = "LOCAL";
    public static final String AUTH_TYPE_SSO = "SSO";

    private static final String INITIAL_PASSWORD_PROPERTY = "crest.user.initial-password";
    private static final String UNINITIALIZED_ADMIN_PASSWORD_HASH = "{CREST_INITIAL_PASSWORD_REQUIRED}";
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z0-9._@-]{1,64}$");
    private static final Pattern UNSAFE_DISPLAY_NAME_PATTERN = Pattern.compile("[<>\\p{Cntrl}]");
    private static final String USER_GRID_COLUMNS = """
            id, account, name, email, phone_prefix, phone, enable, origin,
            auth_type, external_id, last_login_time, create_time, update_time
            """;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private Environment environment;

    @Resource
    private PlatformPermissionManage platformPermissionManage;

    @Resource
    private SysParameterManage sysParameterManage;

    @Value("${crest.user.initial-password:}")
    private String configuredInitialPassword;

    // 用户表行映射器，集中维护数据库字段到领域模型的转换规则
    private final RowMapper<CrestUser> rowMapper = (rs, rowNum) -> {
        CrestUser user = new CrestUser();
        user.setId(rs.getLong("id"));
        user.setAccount(rs.getString("account"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setPhonePrefix(rs.getString("phone_prefix"));
        user.setPhone(rs.getString("phone"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setEnable(rs.getBoolean("enable"));
        user.setAdmin(rs.getBoolean("is_admin"));
        user.setOrigin(rs.getInt("origin"));
        user.setAuthType(rs.getString("auth_type"));
        user.setExternalId(rs.getString("external_id"));
        long lastLoginTime = rs.getLong("last_login_time");
        user.setLastLoginTime(rs.wasNull() ? null : lastLoginTime);
        user.setCreateTime(rs.getLong("create_time"));
        user.setUpdateTime(rs.getLong("update_time"));
        return user;
    };

    private final RowMapper<CrestUser> gridRowMapper = (rs, rowNum) -> {
        CrestUser user = new CrestUser();
        user.setId(rs.getLong("id"));
        user.setAccount(rs.getString("account"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setPhonePrefix(rs.getString("phone_prefix"));
        user.setPhone(rs.getString("phone"));
        user.setEnable(rs.getBoolean("enable"));
        user.setOrigin(rs.getInt("origin"));
        user.setAuthType(rs.getString("auth_type"));
        user.setExternalId(rs.getString("external_id"));
        long lastLoginTime = rs.getLong("last_login_time");
        user.setLastLoginTime(rs.wasNull() ? null : lastLoginTime);
        user.setCreateTime(rs.getLong("create_time"));
        user.setUpdateTime(rs.getLong("update_time"));
        return user;
    };

    // 初始化内置管理员，首次启动创建账号，并替换初始化 SQL 中不可登录的哨兵密码。
    @PostConstruct
    public void initAdmin() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM core_iam_user WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            long now = System.currentTimeMillis();
            jdbcTemplate.update("""
                    INSERT INTO core_iam_user(id, account, name, password_hash, enable, is_admin, origin, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, 1L, "admin", "管理员", PasswordEncoder.encode(initialPassword()), true, true, 0, now, now);
            platformPermissionManage.bindUserToOrg(1L, PlatformPermissionManage.ROOT_ORG_ID, true);
            platformPermissionManage.bindUserToRole(1L, PlatformPermissionManage.ROOT_ORG_ID, PlatformPermissionManage.SYSTEM_ADMIN_ROLE_ID);
        } else {
            String passwordHash = jdbcTemplate.queryForObject(
                    "SELECT password_hash FROM core_iam_user WHERE id = 1", String.class);
            if (Strings.CS.equals(passwordHash, UNINITIALIZED_ADMIN_PASSWORD_HASH)) {
                jdbcTemplate.update("UPDATE core_iam_user SET password_hash = ?, is_admin = 1, enable = 1, update_time = ? WHERE id = 1",
                        PasswordEncoder.encode(initialPassword()), System.currentTimeMillis());
            } else {
                jdbcTemplate.update("UPDATE core_iam_user SET is_admin = 1, enable = 1 WHERE id = 1");
            }
        }
    }

    // 按账号查询单个用户，登录和账号唯一性校验共用此入口
    public CrestUser queryByAccount(String account) {
        List<CrestUser> users = jdbcTemplate.query(limitOne("SELECT * FROM core_iam_user WHERE account = ?"), rowMapper, account);
        return users.isEmpty() ? null : users.get(0);
    }

    // 按用户 ID 查询单个用户
    public CrestUser queryById(Long id) {
        List<CrestUser> users = jdbcTemplate.query(limitOne("SELECT * FROM core_iam_user WHERE id = ?"), rowMapper, id);
        return users.isEmpty() ? null : users.get(0);
    }

    // 按认证类型和外部身份 ID 查询单点登录用户
    public CrestUser queryByExternalId(String authType, String externalId) {
        List<CrestUser> users = jdbcTemplate.query("""
                SELECT * FROM core_iam_user
                WHERE auth_type = ? AND external_id = ?
                """.transform(this::limitOne), rowMapper, authType, externalId);
        return users.isEmpty() ? null : users.get(0);
    }

    // 用户查询单条限制由元数据库方言生成，避免硬编码 LIMIT
    private String limitOne(String sql) {
        return dialect().limitOne(sql.stripTrailing());
    }

    // 返回用户当前密码密文，供认证流程读取凭据版本
    public String secretByUid(Long uid) {
        CrestUser user = queryById(uid);
        return user == null ? null : user.getPasswordHash();
    }

    // 校验本地密码，兼容旧 MD5 密文，并在成功登录后升级为新密文格式
    public boolean passwordMatches(CrestUser user, String rawPassword) {
        if (user == null || rawPassword == null) {
            return false;
        }

        String storedHash = user.getPasswordHash();

        // 优先尝试新格式密码密文
        if (storedHash != null && storedHash.contains(":")) {
            return PasswordEncoder.matches(rawPassword, storedHash);
        }

        // 兼容旧格式 MD5 密文
        boolean matches = Strings.CS.equals(storedHash, Md5Utils.md5(rawPassword));

        // 旧密文校验成功后立即重写为新格式，减少后续兼容面
        if (matches && PasswordEncoder.needsReEncoding(storedHash)) {
            String newHash = PasswordEncoder.encode(rawPassword);
            jdbcTemplate.update("UPDATE core_iam_user SET password_hash = ? WHERE id = ?",
                    newHash, user.getId());
        }

        return matches;
    }

    // 判断指定用户是否为管理员
    public boolean isAdmin(Long uid) {
        CrestUser user = queryById(uid);
        return user != null && Boolean.TRUE.equals(user.getAdmin());
    }

    // 创建或更新单点登录用户，并按自动创建开关决定是否补齐本地用户记录
    @Transactional
    public CrestUser createOrUpdateSsoUser(SsoUserProfile profile, boolean autoCreateUser) {
        if (profile == null || StringUtils.isBlank(profile.getExternalId())) {
            CrestException.throwException("单点登录用户唯一标识不能为空");
        }
        validate(profile.getAccount(), StringUtils.defaultIfBlank(profile.getName(), profile.getAccount()));
        String account = profile.getAccount().trim();
        String name = StringUtils.defaultIfBlank(profile.getName(), account).trim();
        String email = StringUtils.trimToNull(profile.getEmail());
        long now = System.currentTimeMillis();

        CrestUser user = queryByExternalId(AUTH_TYPE_SSO, profile.getExternalId());
        if (user == null) {
            user = queryByAccount(account);
        }
        if (user == null) {
            if (!autoCreateUser) {
                CrestException.throwException("用户不存在，且未启用自动创建用户");
            }
            long id = IDUtils.snowID();
            jdbcTemplate.update("""
                    INSERT INTO core_iam_user(id, account, name, email, phone_prefix, phone, password_hash, enable, is_admin,
                        origin, auth_type, external_id, last_login_time, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, account, name, email, null, null, Md5Utils.md5(IDUtils.randomID(32)),
                    true, false, 2, AUTH_TYPE_SSO, profile.getExternalId(), now, now, now);
            platformPermissionManage.bindUserToOrg(id, PlatformPermissionManage.ROOT_ORG_ID, true);
            platformPermissionManage.bindUserToRole(id, PlatformPermissionManage.ROOT_ORG_ID, PlatformPermissionManage.MEMBER_ROLE_ID);
            return queryById(id);
        }
        if (Boolean.FALSE.equals(user.getEnable())) {
            CrestException.throwException("用户已停用");
        }
        CrestUser sameAccount = queryByAccount(account);
        if (sameAccount != null && !sameAccount.getId().equals(user.getId())) {
            CrestException.throwException("单点登录账号已被其他用户占用");
        }
        jdbcTemplate.update("""
                UPDATE core_iam_user
                SET account = ?, name = ?, email = ?, auth_type = ?, external_id = ?, origin = ?, last_login_time = ?, update_time = ?
                WHERE id = ?
                """, account, name, email, AUTH_TYPE_SSO, profile.getExternalId(), 2, now, now, user.getId());
        return queryById(user.getId());
    }

    // 应用单点登录身份决策，支持创建新用户或更新既有 SSO 用户资料
    @Transactional
    public CrestUser applySsoIdentity(SsoIdentityDecision decision) {
        SsoIdentityProfile profile = decision.getProfile();
        String account = profile.getAccount();
        String name = profile.getName();
        String email = StringUtils.trimToNull(profile.getEmail());
        long now = System.currentTimeMillis();
        if (SsoIdentityAction.CREATE_USER.equals(decision.getAction())) {
            long id = IDUtils.snowID();
            jdbcTemplate.update("""
                    INSERT INTO core_iam_user(id, account, name, email, phone_prefix, phone, password_hash, enable, is_admin,
                        origin, auth_type, external_id, last_login_time, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, account, name, email, null, null, Md5Utils.md5(IDUtils.randomID(32)),
                    true, false, 2, AUTH_TYPE_SSO, profile.getExternalSubject(), now, now, now);
            platformPermissionManage.bindUserToOrg(id, PlatformPermissionManage.ROOT_ORG_ID, true);
            platformPermissionManage.bindUserToRole(id, PlatformPermissionManage.ROOT_ORG_ID, PlatformPermissionManage.MEMBER_ROLE_ID);
            return queryById(id);
        }
        Long userId = decision.getUserId();
        CrestUser user = queryById(userId);
        if (user == null) {
            CrestException.throwException("用户不存在");
        }
        if (!AUTH_TYPE_SSO.equalsIgnoreCase(user.getAuthType())) {
            markLoginSuccess(userId);
            return queryById(userId);
        }
        jdbcTemplate.update("""
                UPDATE core_iam_user
                SET account = ?, name = ?, email = ?, auth_type = ?, external_id = ?, origin = ?, last_login_time = ?, update_time = ?
                WHERE id = ?
                """, account, name, email, AUTH_TYPE_SSO, profile.getExternalSubject(), 2, now, now, userId);
        return queryById(userId);
    }

    // 记录用户登录成功时间
    @Transactional
    public void markLoginSuccess(Long id) {
        if (id == null) return;
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE core_iam_user SET last_login_time = ?, update_time = ? WHERE id = ?", now, now, id);
    }

    // 分页查询用户列表，支持关键字、状态和组织范围过滤
    public IPage<UserGridVO> pager(int goPage, int pageSize, UserGridRequest request) {
        String keyword = request == null ? null : request.getKeyword();
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (StringUtils.isNotBlank(keyword)) {
            where.append(" AND (account LIKE ? OR name LIKE ? OR email LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (request != null && request.getStatusList() != null && !request.getStatusList().isEmpty()) {
            where.append(" AND enable IN (");
            for (int i = 0; i < request.getStatusList().size(); i++) {
                if (i > 0) where.append(",");
                where.append("?");
                args.add(request.getStatusList().get(i));
            }
            where.append(")");
        }
        if (request != null && request.getOid() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM core_iam_user_org uo WHERE uo.")
                    .append(uidColumn()).append(" = core_iam_user.id AND uo.oid = ?)");
            args.add(request.getOid());
        }
        Object[] queryArgs = args.toArray();
        String countSql = "SELECT COUNT(1) FROM core_iam_user" + where;
        Long total = queryArgs.length == 0
                ? jdbcTemplate.queryForObject(countSql, Long.class)
                : jdbcTemplate.queryForObject(countSql, Long.class, queryArgs);
        String order = request != null && Boolean.FALSE.equals(request.getTimeDesc()) ? " ASC" : " DESC";
        int offset = Math.max((goPage - 1) * pageSize, 0);
        String listSql = dialect().limitOffset("SELECT " + USER_GRID_COLUMNS + " FROM core_iam_user" + where + " ORDER BY create_time" + order,
                pageSize, offset);
        List<CrestUser> users = queryArgs.length == 0
                ? jdbcTemplate.query(listSql, gridRowMapper)
                : jdbcTemplate.query(listSql, gridRowMapper, queryArgs);
        Page<UserGridVO> page = new Page<>(goPage, pageSize);
        page.setTotal(total == null ? 0 : total);
        page.setRecords(users.stream().map(this::toGrid).toList());
        return page;
    }

    // 查询当前组织下可选用户，用于授权和成员选择控件
    public List<UserItem> usersByCurrentOrg(String keyword) {
        Long oid = AuthUtils.getUser() == null ? PlatformPermissionManage.ROOT_ORG_ID : AuthUtils.getUser().getDefaultOid();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.name, u.account
                FROM core_iam_user u
                INNER JOIN core_iam_user_org uo ON uo.%s = u.id
                WHERE uo.oid = ? AND u.enable = 1
                """.formatted(uidColumn()));
        args.add(oid);
        if (StringUtils.isNotBlank(keyword)) {
            sql.append(" AND (u.name LIKE ? OR u.account LIKE ? OR u.email LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY u.create_time DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            UserItem item = new UserItem();
            item.setId(rs.getLong("id"));
            item.setName(rs.getString("name"));
            item.setAccount(rs.getString("account"));
            return item;
        }, args.toArray());
    }

    // 批量导入用户，逐行统计成功和失败数量
    @Transactional
    public UserImportVO importUsers(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            CrestException.throwException("导入文件不能为空");
        }
        List<UserImportRow> rows = readImportRows(file);
        int success = 0;
        int failed = 0;
        for (UserImportRow row : rows) {
            if (row == null || StringUtils.isBlank(row.getAccount()) || StringUtils.isBlank(row.getName())) {
                failed++;
                continue;
            }
            try {
                UserCreator creator = new UserCreator();
                creator.setAccount(row.getAccount().trim());
                creator.setName(row.getName().trim());
                creator.setEmail(StringUtils.trimToNull(row.getEmail()));
                creator.setPhone(StringUtils.trimToNull(row.getPhone()));
                creator.setEnable(true);
                creator.setOid(PlatformPermissionManage.ROOT_ORG_ID);
                creator.setRoleIds(List.of(PlatformPermissionManage.MEMBER_ROLE_ID));
                create(creator);
                success++;
            } catch (Exception ignored) {
                failed++;
            }
        }
        return new UserImportVO("user-import", success, failed);
    }

    // 读取 Excel 或 CSV 导入文件并转换为用户导入行
    private List<UserImportRow> readImportRows(MultipartFile file) throws Exception {
        String filename = StringUtils.defaultString(file.getOriginalFilename()).toLowerCase();
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return EasyExcel.read(file.getInputStream())
                    .head(UserImportRow.class)
                    .sheet()
                    .doReadSync();
        }
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        String[] rows = text.replace("\r", "").split("\n");
        List<UserImportRow> result = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i].trim();
            if (StringUtils.isBlank(row) || (i == 0 && row.contains("账号"))) {
                continue;
            }
            String[] cols = row.split(",", -1);
            UserImportRow importRow = new UserImportRow();
            importRow.setAccount(cols.length > 0 ? StringUtils.trimToNull(cols[0]) : null);
            importRow.setName(cols.length > 1 ? StringUtils.trimToNull(cols[1]) : null);
            importRow.setEmail(cols.length > 2 ? StringUtils.trimToNull(cols[2]) : null);
            importRow.setPhone(cols.length > 3 ? StringUtils.trimToNull(cols[3]) : null);
            result.add(importRow);
        }
        return result;
    }

    // 创建本地用户，并写入默认组织和角色绑定
    @Transactional
    public Long create(UserCreator creator) {
        validate(creator.getAccount(), creator.getName());
        if (queryByAccount(creator.getAccount()) != null) {
            CrestException.throwException("账号已存在");
        }

        // 权限校验：只有管理员可以创建管理员账户
        boolean requestAdminRole = hasAdminRole(creator.getRoleIds());
        if (requestAdminRole) {
            Long currentUserId = AuthUtils.getUser() != null ? AuthUtils.getUser().getUserId() : null;
            if (currentUserId == null || !isAdmin(currentUserId)) {
                CrestException.throwException("只有管理员可以创建管理员账户");
            }
        }

        // 使用传入密码或默认初始密码，并执行统一密码策略校验
        String password = StringUtils.isNotBlank(creator.getPassword()) ? creator.getPassword().trim() : initialPassword();
        PasswordValidator.validate(password);

        long id = IDUtils.snowID();
        long now = System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO core_iam_user(id, account, name, email, phone_prefix, phone, password_hash, enable, is_admin,
                    origin, auth_type, external_id, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, creator.getAccount().trim(), creator.getName().trim(), creator.getEmail(),
                creator.getPhonePrefix(), creator.getPhone(), PasswordEncoder.encode(password),
                creator.getEnable() == null || creator.getEnable(), hasAdminRole(creator.getRoleIds()),
                0, AUTH_TYPE_LOCAL, null, now, now);
        Long oid = creator.getOid() == null ? PlatformPermissionManage.ROOT_ORG_ID : creator.getOid();
        platformPermissionManage.bindUserToOrg(id, oid, true);
        platformPermissionManage.replaceUserRoles(id, oid, creator.getRoleIds());
        return id;
    }

    // 编辑本地用户基础信息、启用状态、默认组织、角色和可选密码
    @Transactional
    public void edit(UserEditor editor) {
        CrestUser user = queryById(editor.getId());
        if (user == null) {
            CrestException.throwException("用户不存在");
        }

        // 权限校验：编辑管理员账户需要管理员权限，编辑内置管理员需要系统管理员权限
        if (editor.getId() == 1L) {
            Long currentUserId = AuthUtils.getUser() != null ? AuthUtils.getUser().getUserId() : null;
            if (currentUserId == null || !AuthUtils.isSysAdmin(currentUserId)) {
                CrestException.throwException("只有系统管理员可以编辑系统管理员账户");
            }
        } else if (Boolean.TRUE.equals(user.getAdmin()) || hasAdminRole(editor.getRoleIds())) {
            Long currentUserId = AuthUtils.getUser() != null ? AuthUtils.getUser().getUserId() : null;
            if (currentUserId == null || !isAdmin(currentUserId)) {
                CrestException.throwException("只有管理员可以编辑管理员账户");
            }
        }

        validate(editor.getAccount(), editor.getName());
        if (AUTH_TYPE_SSO.equalsIgnoreCase(user.getAuthType()) && !Strings.CS.equals(user.getAccount(), editor.getAccount().trim())) {
            CrestException.throwException("单点登录用户账号由身份提供方维护");
        }
        String newPassword = StringUtils.trimToNull(editor.getPassword());
        if (newPassword != null && AUTH_TYPE_SSO.equalsIgnoreCase(user.getAuthType())) {
            CrestException.throwException("单点登录用户不支持设置本地密码");
        }
        CrestUser sameAccount = queryByAccount(editor.getAccount());
        if (sameAccount != null && !sameAccount.getId().equals(editor.getId())) {
            CrestException.throwException("账号已存在");
        }
        // 仅在传入新密码时执行密码策略校验和密文更新
        if (newPassword != null) {
            PasswordValidator.validate(newPassword);
        }
        if (newPassword != null) {
            jdbcTemplate.update("""
                    UPDATE core_iam_user
                    SET account = ?, name = ?, email = ?, phone_prefix = ?, phone = ?, enable = ?, is_admin = ?, password_hash = ?, update_time = ?
                    WHERE id = ?
                    """, editor.getAccount().trim(), editor.getName().trim(), editor.getEmail(),
                    editor.getPhonePrefix(), editor.getPhone(), editor.getEnable() == null || editor.getEnable(),
                    editor.getId() == 1L || hasAdminRole(editor.getRoleIds()), PasswordEncoder.encode(newPassword), System.currentTimeMillis(), editor.getId());
        } else {
            jdbcTemplate.update("""
                    UPDATE core_iam_user
                    SET account = ?, name = ?, email = ?, phone_prefix = ?, phone = ?, enable = ?, is_admin = ?, update_time = ?
                    WHERE id = ?
                    """, editor.getAccount().trim(), editor.getName().trim(), editor.getEmail(),
                    editor.getPhonePrefix(), editor.getPhone(), editor.getEnable() == null || editor.getEnable(),
                    editor.getId() == 1L || hasAdminRole(editor.getRoleIds()), System.currentTimeMillis(), editor.getId());
        }
        Long oid = editor.getOid() == null ? platformPermissionManage.defaultOrgId(editor.getId()) : editor.getOid();
        platformPermissionManage.replaceUserDefaultOrg(editor.getId(), oid);
        platformPermissionManage.replaceUserRoles(editor.getId(), oid, editor.getRoleIds());
    }

    // 删除用户及其组织、角色绑定，内置管理员不允许删除
    @Transactional
    public void delete(Long id) {
        if (id == 1L) {
            CrestException.throwException("内置管理员不能删除");
        }

        // 权限校验：删除管理员账户需要当前用户具备管理员权限
        CrestUser targetUser = queryById(id);
        if (targetUser != null && Boolean.TRUE.equals(targetUser.getAdmin())) {
            Long currentUserId = AuthUtils.getUser() != null ? AuthUtils.getUser().getUserId() : null;
            if (currentUserId == null || !isAdmin(currentUserId)) {
                CrestException.throwException("只有管理员可以删除管理员账户");
            }
        }

        jdbcTemplate.update("DELETE FROM core_iam_user WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM core_iam_user_org WHERE " + uidColumn() + " = ?", id);
        jdbcTemplate.update("DELETE FROM core_iam_user_role WHERE " + uidColumn() + " = ?", id);
    }

    // 切换用户启用状态，内置管理员不允许停用
    @Transactional
    public void enable(EnableSwitchRequest request) {
        if (request.getId() == 1L && Boolean.FALSE.equals(request.getEnable())) {
            CrestException.throwException("内置管理员不能停用");
        }

        // 权限校验：修改管理员状态需要当前用户具备管理员权限
        CrestUser targetUser = queryById(request.getId());
        if (targetUser != null && Boolean.TRUE.equals(targetUser.getAdmin())) {
            Long currentUserId = AuthUtils.getUser() != null ? AuthUtils.getUser().getUserId() : null;
            if (currentUserId == null || !isAdmin(currentUserId)) {
                CrestException.throwException("只有管理员可以修改管理员状态");
            }
        }

        jdbcTemplate.update("UPDATE core_iam_user SET enable = ?, update_time = ? WHERE id = ?",
                request.getEnable(), System.currentTimeMillis(), request.getId());
    }

    // 重置本地用户密码并返回新生成的临时密码
    @Transactional
    public String resetPwd(Long id) {
        CrestUser user = queryById(id);
        if (user == null) {
            CrestException.throwException("用户不存在");
        }
        if (AUTH_TYPE_SSO.equalsIgnoreCase(user.getAuthType())) {
            CrestException.throwException("单点登录用户不支持重置本地密码");
        }
        String password = generateSecurePassword();
        jdbcTemplate.update("UPDATE core_iam_user SET password_hash = ?, update_time = ? WHERE id = ?",
                PasswordEncoder.encode(password), System.currentTimeMillis(), id);
        return password;
    }

    // 校验旧密码后修改当前用户本地密码
    @Transactional
    public void modifyPwd(Long id, String oldPwd, String newPwd) {
        CrestUser user = queryById(id);
        if (user == null) {
            CrestException.throwException("用户不存在");
        }
        if (AUTH_TYPE_SSO.equalsIgnoreCase(user.getAuthType())) {
            CrestException.throwException("单点登录用户不支持修改本地密码");
        }
        if (!passwordMatches(user, oldPwd)) {
            CrestException.throwException("原密码不正确");
        }

        // 修改密码同样复用统一密码策略
        PasswordValidator.validate(newPwd);

        jdbcTemplate.update("UPDATE core_iam_user SET password_hash = ?, update_time = ? WHERE id = ?",
                PasswordEncoder.encode(newPwd), System.currentTimeMillis(), id);
    }

    // 将用户模型转换为表单详情视图对象
    public UserFormVO toForm(CrestUser user) {
        if (user == null) return null;
        UserFormVO vo = new UserFormVO();
        vo.setId(user.getId());
        vo.setAccount(user.getAccount());
        vo.setName(user.getName());
        vo.setEmail(user.getEmail());
        vo.setPhonePrefix(user.getPhonePrefix());
        vo.setPhone(user.getPhone());
        vo.setEnable(user.getEnable());
        vo.setOrigin(user.getOrigin());
        vo.setAuthType(user.getAuthType());
        vo.setExternalId(user.getExternalId());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setModel(AUTH_TYPE_SSO.equalsIgnoreCase(user.getAuthType()) ? "sso" : "local");
        vo.setRoleIds(platformPermissionManage.userRoleIdStrings(user.getId()));
        Long oid = platformPermissionManage.defaultOrgId(user.getId());
        vo.setOid(oid);
        vo.setOrgName(platformPermissionManage.orgName(oid));
        return vo;
    }

    // 将用户模型转换为列表视图对象
    public UserGridVO toGrid(CrestUser user) {
        UserGridVO vo = new UserGridVO();
        vo.setId(user.getId());
        vo.setAccount(user.getAccount());
        vo.setName(user.getName());
        vo.setEmail(user.getEmail());
        vo.setPhonePrefix(user.getPhonePrefix());
        vo.setPhone(user.getPhone());
        vo.setEnable(user.getEnable());
        vo.setOrigin(user.getOrigin());
        vo.setAuthType(user.getAuthType());
        vo.setExternalId(user.getExternalId());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoleItems(platformPermissionManage.userRoleItems(user.getId()));
        Long oid = platformPermissionManage.defaultOrgId(user.getId());
        vo.setOid(oid);
        vo.setOrgName(platformPermissionManage.orgName(oid));
        return vo;
    }

    // 将用户模型转换为当前登录用户上下文视图对象
    public CurUserVO toCurrent(CrestUser user) {
        if (user == null) return null;
        CurUserVO vo = new CurUserVO();
        vo.setId(user.getId());
        vo.setName(user.getName());
        vo.setOid(platformPermissionManage.defaultOrgId(user.getId()));
        vo.setLanguage("zh-CN");
        boolean admin = Boolean.TRUE.equals(user.getAdmin()) || platformPermissionManage.isSystemAdmin(user.getId());
        vo.setAdmin(admin);
        vo.setBackendAccess(admin);
        return vo;
    }

    // 校验账号和展示名称的必填、长度和安全字符约束
    private void validate(String account, String name) {
        if (StringUtils.isBlank(account)) {
            CrestException.throwException("账号不能为空");
        }
        if (!ACCOUNT_PATTERN.matcher(account.trim()).matches()) {
            CrestException.throwException("账号只支持 64 位以内的字母、数字、点、下划线、横线和 @");
        }
        if (StringUtils.isBlank(name)) {
            CrestException.throwException("姓名不能为空");
        }
        String displayName = name.trim();
        if (displayName.length() > 64) {
            CrestException.throwException("姓名不能超过 64 个字符");
        }
        if (UNSAFE_DISPLAY_NAME_PATTERN.matcher(displayName).find()) {
            CrestException.throwException("姓名不能包含 HTML 标签或控制字符");
        }
    }

    // 判断角色列表中是否包含管理员角色
    private boolean hasAdminRole(List<Long> roleIds) {
        return roleIds != null && roleIds.stream().anyMatch(roleId -> Long.valueOf(1L).equals(roleId));
    }

    // 返回当前系统初始密码配置
    public String defaultPwd() {
        return initialPassword();
    }

    // 优先读取系统参数中的初始密码，其次使用配置文件兜底
    private String initialPassword() {
        String settingPassword = sysParameterManage == null
                ? null
                : sysParameterManage.singleVal(SystemSettingConstants.INITIAL_PASSWORD);
        String password = StringUtils.defaultIfBlank(settingPassword, configuredInitialPassword);
        if (StringUtils.isBlank(password)) {
            throw new IllegalStateException(INITIAL_PASSWORD_PROPERTY + " must be configured");
        }
        return password;
    }

    // 元数据库方言只影响系统库 SQL，MySQL 默认仍保持原有分页和标识符规则。
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }

    // UID 在 OB Oracle 中是保留字，用户 SQL 中统一走方言引用
    private String uidColumn() {
        return dialect().quoteIdentifier("UID");
    }

    // 生成满足复杂度要求的临时密码，并避免连续三位字符相同
    private String generateSecurePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder sb = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        sb.append("A");
        sb.append("a");
        sb.append("1");
        sb.append("!");
        for (int i = 0; i < 8; i++) {
            char next;
            do {
                next = chars.charAt(random.nextInt(chars.length()));
            } while (sb.length() >= 2
                    && sb.charAt(sb.length() - 1) == next
                    && sb.charAt(sb.length() - 2) == next);
            sb.append(next);
        }
        return sb.toString();
    }
}
