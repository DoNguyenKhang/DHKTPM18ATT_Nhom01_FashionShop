-- Migration: Add CANCELLED status support to payments table
-- Date: 2025-11-24
-- Purpose: Support cancelling old payments when changing payment method
-- Fix: "Data truncated for column 'status'" error

-- ==============================================================
-- IMPORTANT: This fixes the error when updating payment status to CANCELLED
-- ==============================================================

-- Backup current status values (for reference)
-- SELECT DISTINCT status FROM payments;

-- Modify status column to ensure it can accept CANCELLED value
-- Change from VARCHAR(20) or ENUM to VARCHAR(50) for safety
ALTER TABLE payments
MODIFY COLUMN status VARCHAR(50) NOT NULL
COMMENT 'Payment status: PENDING, COMPLETED, FAILED, REFUNDED, CANCELLED';

-- Verify the change
-- SHOW FULL COLUMNS FROM payments WHERE Field = 'status';

-- Note: VARCHAR(50) is safer than ENUM because:
-- 1. No need to alter when adding new status values
-- 2. Compatible with JPA @Enumerated(EnumType.STRING)
-- 3. More flexible for future expansion


