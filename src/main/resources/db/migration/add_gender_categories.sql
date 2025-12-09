-- Add gender categories for filtering products by gender
-- Run this migration to add "Thời trang Nam" and "Thời trang Nữ" categories

-- Check and insert "Thời trang Nam" category if not exists
INSERT INTO `categories` (`name`, `slug`, `description`, `parent_id`, `image`, `is_active`)
SELECT 'Thời trang Nam', 'nam', 'Thời trang dành cho nam giới - Lịch lãm & Hiện đại', NULL, NULL, b'1'
WHERE NOT EXISTS (SELECT 1 FROM `categories` WHERE `slug` = 'nam');

-- Check and insert "Thời trang Nữ" category if not exists
INSERT INTO `categories` (`name`, `slug`, `description`, `parent_id`, `image`, `is_active`)
SELECT 'Thời trang Nữ', 'nu', 'Thời trang dành cho nữ giới - Quyến rũ & Thanh lịch', NULL, NULL, b'1'
WHERE NOT EXISTS (SELECT 1 FROM `categories` WHERE `slug` = 'nu');

-- Check and insert "Trẻ em" category if not exists
INSERT INTO `categories` (`name`, `slug`, `description`, `parent_id`, `image`, `is_active`)
SELECT 'Trẻ em', 'tre-em', 'Thời trang dành cho trẻ em', NULL, NULL, b'1'
WHERE NOT EXISTS (SELECT 1 FROM `categories` WHERE `slug` = 'tre-em');

