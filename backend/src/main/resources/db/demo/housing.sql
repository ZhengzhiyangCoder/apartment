-- Synthetic, additive fixtures. Do not overwrite user-maintained names, states or profiles.
INSERT INTO dorm_building(name)
SELECT '模拟 A 栋' WHERE NOT EXISTS (SELECT 1 FROM dorm_building WHERE name = '模拟 A 栋');
INSERT INTO dorm_building(name)
SELECT '模拟 B 栋' WHERE NOT EXISTS (SELECT 1 FROM dorm_building WHERE name = '模拟 B 栋');

INSERT INTO dorm_room(building_id, room_no, floor)
SELECT b.id, fixture.room_no, fixture.floor FROM dorm_building b
JOIN (SELECT '模拟 A 栋' AS building_name, 'A101' AS room_no, 1 AS floor
      UNION ALL SELECT '模拟 A 栋', 'A102', 1
      UNION ALL SELECT '模拟 B 栋', 'B201', 2) fixture ON fixture.building_name = b.name
WHERE b.enabled = TRUE AND NOT EXISTS (SELECT 1 FROM dorm_room r WHERE r.building_id = b.id AND r.room_no = fixture.room_no);

INSERT INTO dorm_bed(room_id, bed_no)
SELECT r.id, fixture.bed_no FROM dorm_room r JOIN dorm_building b ON b.id = r.building_id
CROSS JOIN (SELECT '01' AS bed_no UNION ALL SELECT '02') fixture
WHERE b.enabled = TRUE AND r.enabled = TRUE
  AND ((b.name = '模拟 A 栋' AND r.room_no IN ('A101', 'A102')) OR (b.name = '模拟 B 栋' AND r.room_no = 'B201'))
  AND NOT EXISTS (SELECT 1 FROM dorm_bed d WHERE d.room_id = r.id AND d.bed_no = fixture.bed_no);

INSERT INTO student_profile(user_id, student_no, full_name, college, major, class_name)
SELECT u.id, 'DEMO20260001', '模拟学生', '模拟计算机学院', '软件工程', '2026 软件一班'
FROM sys_user u WHERE u.username = 'student' AND u.role = 'STUDENT' AND u.enabled = TRUE
  AND NOT EXISTS (SELECT 1 FROM student_profile s WHERE s.user_id = u.id OR s.student_no = 'DEMO20260001');
