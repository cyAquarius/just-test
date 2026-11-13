-- exported comment keeps UNSIGNED and COLLATE utf8mb4_unicode_ci
CREATE TABLE `exported_flag` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'it\'s a flag',
  `collate_flag` VARCHAR(64) NOT NULL DEFAULT 'is UNSIGNED ok',
  `doubled_quote` VARCHAR(64) NOT NULL DEFAULT 'collate utf8' COMMENT 'it''s doubled',
  `enabled` BIT(1) NOT NULL DEFAULT b'0',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='owner''s table';
