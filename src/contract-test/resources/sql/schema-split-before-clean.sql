# 行注释里允许 ; 和 '
CREATE TABLE escaped_boundary (
    id INT PRIMARY KEY,
    unsigned_qty INT UNSIGNED,
    path VARCHAR(16) DEFAULT '\\',
    note VARCHAR(32) DEFAULT 'a; -- # b'
) DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin;
INSERT INTO escaped_boundary (id, unsigned_qty) VALUES (1, 3);
