#!/usr/bin/env bash
# Applies all Flyway SQL migrations to a throwaway database and checks that
# key constraints accept valid data and reject invalid data.
#
# Usage:  MYSQL_PWD=<root password> ./database/verify-migrations.sh [mysql-binary] [user]
# The scratch database "tshirt_validate" is dropped and recreated.
set -u

MYSQL="${1:-mysql}"
USER_NAME="${2:-root}"
DB="tshirt_validate"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MIG="$ROOT_DIR/backend/src/main/resources/db"
PASS=0
FAIL=0

sql()  { "$MYSQL" -u"$USER_NAME" -N -B "$DB" -e "$1" 2>&1; }

expect_ok() {
  local name="$1" stmt="$2" out
  if out=$(sql "$stmt"); then echo "PASS  $name"; PASS=$((PASS+1));
  else echo "FAIL  $name -> $out"; FAIL=$((FAIL+1)); fi
}

expect_error() {
  local name="$1" stmt="$2" out
  if out=$(sql "$stmt"); then echo "FAIL  $name (statement was accepted)"; FAIL=$((FAIL+1));
  else echo "PASS  $name (rejected: ${out##*: })"; PASS=$((PASS+1)); fi
}

expect_value() {
  local name="$1" stmt="$2" want="$3" got
  got=$(sql "$stmt")
  if [ "$got" = "$want" ]; then echo "PASS  $name = $got"; PASS=$((PASS+1));
  else echo "FAIL  $name expected '$want' got '$got'"; FAIL=$((FAIL+1)); fi
}

"$MYSQL" -u"$USER_NAME" -e "DROP DATABASE IF EXISTS $DB; CREATE DATABASE $DB CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" || exit 2
for f in "$MIG"/migration/V*.sql "$MIG"/dev-data/V*.sql; do
  "$MYSQL" -u"$USER_NAME" "$DB" < "$f" || { echo "FAIL  applying $(basename "$f")"; exit 1; }
  echo "OK    applied $(basename "$f")"
done

echo "--- reference data"
expect_value "required categories" "SELECT GROUP_CONCAT(code ORDER BY sort_order) FROM categories" "TSHIRTS,SHIRTS,PANTS,CUSTOM_TSHIRTS"
expect_value "roles" "SELECT GROUP_CONCAT(name ORDER BY name) FROM roles" "ROLE_ADMIN,ROLE_CUSTOMER"
expect_value "tshirt types" "SELECT GROUP_CONCAT(code ORDER BY sort_order) FROM customization_options WHERE option_type='TSHIRT_TYPE'" "ROUND_NECK,V_NECK,POLO,OVERSIZED"
expect_value "print placements" "SELECT GROUP_CONCAT(code ORDER BY sort_order) FROM customization_options WHERE option_type='PRINT_PLACEMENT'" "FRONT,BACK,LEFT_CHEST,FULL_FRONT,FULL_BACK"
expect_value "every base T-Shirt type has a product" "SELECT COUNT(*) FROM customization_options o JOIN products p ON p.slug = JSON_UNQUOTE(o.metadata->'\$.baseProductSlug') WHERE o.option_type='TSHIRT_TYPE'" "4"
expect_value "brief pricing example 299+FRONT+BACK+TEXT" "SELECT p.base_price + SUM(ch.amount) FROM products p, customization_charges ch WHERE p.slug='custom-round-neck-t-shirt' AND (ch.charge_type, ch.code) IN (('TSHIRT_TYPE','ROUND_NECK'),('PRINT_PLACEMENT','FRONT'),('PRINT_PLACEMENT','BACK'),('TEXT','CUSTOM_TEXT')) GROUP BY p.base_price" "429.00"
expect_value "oversized charge" "SELECT amount FROM customization_charges WHERE charge_type='TSHIRT_TYPE' AND code='OVERSIZED'" "80.00"

echo "--- fixtures"
expect_ok "insert customer" "INSERT INTO users (email, phone, password_hash, full_name) VALUES ('test@example.com','9876543210','\$2a\$12\$abcdefghijklmnopqrstuv','Test User')"
expect_error "duplicate email" "INSERT INTO users (email, password_hash, full_name) VALUES ('test@example.com','x','Dup')"
expect_error "invalid pincode" "INSERT INTO addresses (user_id, full_name, phone, line1, city, state, pincode) SELECT id,'A','9876543210','L1','Pune','MH','012345' FROM users LIMIT 1"
expect_ok "valid address" "INSERT INTO addresses (user_id, full_name, phone, line1, city, state, pincode, is_default) SELECT id,'A','9876543210','L1','Pune','MH','411001',TRUE FROM users LIMIT 1"

echo "--- catalog and inventory"
expect_error "discount above base price" "UPDATE products SET discount_price = base_price + 1 WHERE slug='essential-crew-neck-tee'"
expect_error "reserved above on-hand" "UPDATE inventory SET quantity_reserved = quantity_on_hand + 1 WHERE id = (SELECT id FROM (SELECT MIN(id) id FROM inventory) t)"
expect_error "negative stock" "UPDATE inventory SET quantity_on_hand = -1 WHERE id = (SELECT id FROM (SELECT MIN(id) id FROM inventory) t)"
expect_error "duplicate variant" "INSERT INTO inventory (product_id,size_id,color_id,sku) SELECT product_id,size_id,color_id,'DUP-SKU' FROM inventory LIMIT 1"
expect_error "bad colour hex" "INSERT INTO product_colors (code,name,hex) VALUES ('BAD','Bad','red')"
expect_error "coupon percent over 100" "INSERT INTO coupons (code,discount_type,discount_value,valid_from,valid_to) VALUES ('BAD',  'PERCENT',150,'2026-01-01','2026-12-31')"

echo "--- orders"
ORDER_COLS="order_number,user_id,payment_method,subtotal,customization_total,shipping_charge,discount_total,grand_total,contact_name,contact_email,contact_phone,shipping_address"
expect_error "grand total mismatch" "INSERT INTO orders ($ORDER_COLS) SELECT 'TS-T-1',id,'RAZORPAY',299,130,49,0,400,'A','a@b.c','9876543210',JSON_OBJECT() FROM users LIMIT 1"
expect_ok    "valid order 299+130+49=478" "INSERT INTO orders ($ORDER_COLS) SELECT 'TS-T-2',id,'RAZORPAY',299,130,49,0,478,'A','a@b.c','9876543210',JSON_OBJECT('city','Pune') FROM users LIMIT 1"
expect_error "invalid order status" "UPDATE orders SET order_status='LOST' WHERE order_number='TS-T-2'"
expect_error "invalid payment status" "UPDATE orders SET payment_status='PAID' WHERE order_number='TS-T-2'"
expect_error "line total mismatch" "INSERT INTO order_items (order_id,product_id,inventory_id,product_name,sku,size_code,color_code,quantity,unit_base_price,unit_customization_charge,line_total) SELECT o.id,i.product_id,i.id,'X',i.sku,'M','WHITE',2,299,130,500 FROM orders o, inventory i WHERE o.order_number='TS-T-2' LIMIT 1"
expect_ok    "valid line (299+130)x1" "INSERT INTO order_items (order_id,product_id,inventory_id,product_name,sku,size_code,color_code,quantity,unit_base_price,unit_customization_charge,line_total) SELECT o.id,i.product_id,i.id,'X',i.sku,'M','WHITE',1,299,130,429 FROM orders o, inventory i WHERE o.order_number='TS-T-2' LIMIT 1"
expect_error "bad reservation status" "INSERT INTO inventory_reservations (inventory_id,order_id,quantity,status,expires_at) SELECT i.id,o.id,1,'HELD',NOW() FROM orders o, inventory i LIMIT 1"

echo "--- payments and webhooks"
expect_ok    "razorpay payment row" "INSERT INTO payments (order_id,provider,attempt_no,razorpay_order_id,amount) SELECT id,'RAZORPAY',1,'order_TEST1',478 FROM orders WHERE order_number='TS-T-2'"
expect_ok    "retry attempt with new razorpay order" "INSERT INTO payments (order_id,provider,attempt_no,razorpay_order_id,amount) SELECT id,'RAZORPAY',2,'order_TEST2',478 FROM orders WHERE order_number='TS-T-2'"
expect_error "duplicate razorpay order id" "INSERT INTO payments (order_id,provider,attempt_no,razorpay_order_id,amount) SELECT id,'RAZORPAY',3,'order_TEST1',478 FROM orders WHERE order_number='TS-T-2'"
expect_ok    "COD rows may have NULL gateway ids" "INSERT INTO payments (order_id,provider,attempt_no,amount) SELECT id,'COD',3,478 FROM orders WHERE order_number='TS-T-2'"
expect_ok    "capture payment id" "UPDATE payments SET razorpay_payment_id='pay_TEST1', status='SUCCESS' WHERE razorpay_order_id='order_TEST1'"
expect_error "same payment id on two rows" "UPDATE payments SET razorpay_payment_id='pay_TEST1' WHERE razorpay_order_id='order_TEST2'"
expect_error "refund above amount" "UPDATE payments SET refunded_amount = amount + 1 WHERE razorpay_order_id='order_TEST1'"
expect_ok    "webhook event stored" "INSERT INTO payment_webhook_events (event_id,event_type,payload) VALUES ('evt_1','payment.captured',JSON_OBJECT())"
expect_error "duplicate webhook event ignored" "INSERT INTO payment_webhook_events (event_id,event_type,payload) VALUES ('evt_1','payment.captured',JSON_OBJECT())"

echo "--- designs, cart, reviews"
expect_error "design asset gif rejected" "INSERT INTO design_assets (user_id,storage_key,original_filename,mime_type,size_bytes,sha256) SELECT id,'k1','a.gif','image/gif',10,REPEAT('a',64) FROM users LIMIT 1"
expect_error "cart quantity 0" "INSERT INTO carts (user_id) SELECT id FROM users LIMIT 1; INSERT INTO cart_items (cart_id,inventory_id,quantity) SELECT c.id,(SELECT MIN(id) FROM inventory),0 FROM carts c LIMIT 1"
expect_error "rating 6" "INSERT INTO reviews (user_id,product_id,rating) SELECT u.id,p.id,6 FROM users u, products p LIMIT 1"
expect_error "duplicate wishlist" "INSERT INTO wishlists (user_id,product_id) SELECT u.id,p.id FROM users u, products p LIMIT 1; INSERT INTO wishlists (user_id,product_id) SELECT u.id,p.id FROM users u, products p LIMIT 1"

echo "--- result: $PASS passed, $FAIL failed"
"$MYSQL" -u"$USER_NAME" -e "DROP DATABASE IF EXISTS $DB;"
[ "$FAIL" -eq 0 ]
