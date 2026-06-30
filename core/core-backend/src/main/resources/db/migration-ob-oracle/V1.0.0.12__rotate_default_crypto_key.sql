-- 清理早期初始化脚本写入的固定系统密钥。服务启动时会自动生成部署专属密钥。
DELETE FROM core_crypto_key
WHERE id = 1
  AND create_time = 1779664249927;
