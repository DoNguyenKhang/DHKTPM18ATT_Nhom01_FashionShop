-- Add loyalty points columns to orders table
ALTER TABLE orders
ADD COLUMN IF NOT EXISTS loyalty_points_used INT NOT NULL DEFAULT 0 AFTER coupon_code,
ADD COLUMN IF NOT EXISTS loyalty_points_earned INT NOT NULL DEFAULT 0 AFTER loyalty_points_used;

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_orders_loyalty_points_used ON orders(loyalty_points_used);
CREATE INDEX IF NOT EXISTS idx_orders_loyalty_points_earned ON orders(loyalty_points_earned);

