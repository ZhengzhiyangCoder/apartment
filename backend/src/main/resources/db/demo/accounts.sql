-- Explicit simulated accounts only; repeated loading preserves existing credentials.
INSERT INTO sys_user (username, password_hash, display_name, role) VALUES
('system_admin', '$2a$12$pZo.lr6D8DgZSCbKseJ.ren1u.Nu01i1Sc5H5gGigB1dE/DzBYPry', '模拟系统管理员', 'SYSTEM_ADMIN'),
('dorm_manager', '$2a$12$pZo.lr6D8DgZSCbKseJ.ren1u.Nu01i1Sc5H5gGigB1dE/DzBYPry', '模拟宿管成员', 'DORM_MANAGER'),
('student', '$2a$12$pZo.lr6D8DgZSCbKseJ.ren1u.Nu01i1Sc5H5gGigB1dE/DzBYPry', '模拟学生', 'STUDENT'),
('maintainer', '$2a$12$pZo.lr6D8DgZSCbKseJ.ren1u.Nu01i1Sc5H5gGigB1dE/DzBYPry', '模拟维修人员', 'MAINTAINER')
ON DUPLICATE KEY UPDATE username = sys_user.username;
