-- DEVELOPMENT ONLY: empty database for the automated tests (they wipe and reload it on every test).
CREATE DATABASE IF NOT EXISTS crms_test CHARACTER SET utf8mb4;
GRANT ALL PRIVILEGES ON crms_test.* TO 'crms'@'%';
FLUSH PRIVILEGES;
