-- Repair seed text imported with an incorrect client character set in early OB Oracle builds.
UPDATE sys_setting
SET pval = '统一身份认证'
WHERE pkey = 'sso.providerName'
  AND DBMS_LOB.SUBSTR(pval, 4000, 1) = 'ç»Ÿä¸€èº«ä»½è®¤è¯';

UPDATE auth_sso_provider
SET name = '统一身份认证'
WHERE provider_key = 'default'
  AND name = 'ç»Ÿä¸€èº«ä»½è®¤è¯';

UPDATE iam_role
SET name = '系统管理员',
    description = '拥有全部系统管理和资源管理权限'
WHERE code = 'system_admin'
  AND (name = 'ç³»ç»Ÿç®¡ç†å‘˜'
    OR description = 'æ‹¥æœ‰å…¨éƒ¨ç³»ç»Ÿç®¡ç†å’Œèµ„æºç®¡ç†æƒé™');

UPDATE iam_role
SET name = '普通用户',
    description = '默认业务使用角色'
WHERE code = 'member'
  AND (name = 'æ™®é€šç”¨æˆ·'
    OR description = 'é»˜è®¤ä¸šåŠ¡ä½¿ç”¨è§’è‰²');

UPDATE iam_role
SET name = '审计只读',
    description = '面向审计和巡检场景的只读角色'
WHERE code = 'auditor'
  AND (name = 'å®¡è®¡åªè¯»'
    OR description = 'é¢å‘å®¡è®¡å’Œå·¡æ£€åœºæ™¯çš„åªè¯»è§’è‰²');

UPDATE visualization_watermark
SET setting_content = REPLACE(setting_content, 'æ°´å°', '水印')
WHERE id = 'system_default'
  AND DBMS_LOB.INSTR(setting_content, 'æ°´å°') > 0;

UPDATE audit_log
SET operator_name = '管理员'
WHERE operator_account = 'admin'
  AND operator_name = 'ç®¡ç†å‘˜';
