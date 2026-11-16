-- mysqldump writes string defaults with backslash escapes
CREATE TABLE `exported_default` (
  `id` BIGINT NOT NULL,
  `note` VARCHAR(64) NOT NULL DEFAULT 'it\'s ok' COMMENT 'it\'s a flag',
  `path` VARCHAR(64) NOT NULL DEFAULT 'a\\b',
  `doubled` VARCHAR(64) NOT NULL DEFAULT 'it''s ok',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
