-- Grant for Oracle Continuous Query Notification (CQN)
GRANT CHANGE NOTIFICATION TO <user_name>;

-- Check if the privilege is granted
SELECT privilege
FROM user_sys_privs
WHERE privilege = 'CHANGE NOTIFICATION';

-- Check the Oracle Database version
SELECT version_full
FROM product_component_version;

-- Check the CQN settings
SELECT *
FROM user_change_notification_regs;
