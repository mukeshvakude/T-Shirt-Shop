// Removes smoke-test accounts (default e2e+%@example.com) and everything they created: their orders
// (stock taken is put back, held stock released, coupon uses returned), carts, designs and files,
// addresses and contact messages. Re-running is safe.
//
//   node e2e/cleanup.mjs ["email LIKE pattern"]
//
// Reads DB_USERNAME / DB_PASSWORD from the repository .env and passes the password to the mysql
// client through the MYSQL_PWD environment variable (never on the command line). Set MYSQL_BIN if
// mysql is not on the PATH.
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const pattern = process.argv[2] ?? 'e2e+%@example.com';
if (!/^[A-Za-z0-9+._%-]+@[A-Za-z0-9.%-]+$/.test(pattern)) {
  console.error('Unsafe pattern');
  process.exit(2);
}
const env = Object.fromEntries(fs.readFileSync(path.join(root, '.env'), 'utf8').split(/\r?\n/)
  .filter((l) => /^[A-Z_]+=/.test(l)).map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const mysql = process.env.MYSQL_BIN ?? 'mysql';
const db = (sql) => execFileSync(mysql, ['-h127.0.0.1', '-u' + env.DB_USERNAME, '-N', 'tshirt_shop', '-e', sql],
  { env: { ...process.env, MYSQL_PWD: env.DB_PASSWORD }, encoding: 'utf8' });

const users = `(SELECT id FROM users WHERE email LIKE '${pattern}')`;
// Stored artwork and previews of the test accounts
for (const key of db(`SELECT storage_key FROM design_assets WHERE user_id IN ${users};
  SELECT preview_front_key FROM custom_designs WHERE user_id IN ${users} AND preview_front_key IS NOT NULL;
  SELECT preview_back_key FROM custom_designs WHERE user_id IN ${users} AND preview_back_key IS NOT NULL;`).split(/\r?\n/).filter(Boolean)) {
  if (/^(designs|previews)\/[A-Za-z0-9-]+\.(png|jpg)$/.test(key)) fs.rmSync(path.join(root, 'backend', 'uploads', key), { force: true });
}
db(`SET @p = '${pattern}';
UPDATE inventory i JOIN (SELECT r.inventory_id, SUM(r.quantity) q FROM inventory_reservations r JOIN orders o ON o.id = r.order_id
  JOIN users u ON u.id = o.user_id WHERE u.email LIKE @p AND r.status = 'COMMITTED' GROUP BY r.inventory_id) x
  ON x.inventory_id = i.id SET i.quantity_on_hand = i.quantity_on_hand + x.q;
UPDATE inventory i JOIN (SELECT r.inventory_id, SUM(r.quantity) q FROM inventory_reservations r JOIN orders o ON o.id = r.order_id
  JOIN users u ON u.id = o.user_id WHERE u.email LIKE @p AND r.status = 'ACTIVE' GROUP BY r.inventory_id) x
  ON x.inventory_id = i.id SET i.quantity_reserved = GREATEST(0, i.quantity_reserved - x.q);
UPDATE inventory_reservations r JOIN orders o ON o.id = r.order_id JOIN users u ON u.id = o.user_id
  SET r.status = 'RELEASED' WHERE u.email LIKE @p AND r.status IN ('ACTIVE', 'COMMITTED');
UPDATE coupons c JOIN (SELECT cr.coupon_id, COUNT(*) n FROM coupon_redemptions cr JOIN users u ON u.id = cr.user_id
  WHERE u.email LIKE @p GROUP BY cr.coupon_id) x ON x.coupon_id = c.id SET c.used_count = GREATEST(0, c.used_count - x.n);
DELETE cr FROM coupon_redemptions cr JOIN users u ON u.id = cr.user_id WHERE u.email LIKE @p;
DELETE pt FROM payment_transactions pt JOIN payments p ON p.id = pt.payment_id JOIN orders o ON o.id = p.order_id JOIN users u ON u.id = o.user_id WHERE u.email LIKE @p;
DELETE p FROM payments p JOIN orders o ON o.id = p.order_id JOIN users u ON u.id = o.user_id WHERE u.email LIKE @p;
DELETE o FROM orders o JOIN users u ON u.id = o.user_id WHERE u.email LIKE @p;
DELETE ci FROM cart_items ci JOIN carts c ON c.id = ci.cart_id JOIN users u ON u.id = c.user_id WHERE u.email LIKE @p;
DELETE c FROM carts c JOIN users u ON u.id = c.user_id WHERE u.email LIKE @p;
DELETE a FROM design_assets a JOIN users u ON u.id = a.user_id WHERE u.email LIKE @p;
DELETE d FROM custom_designs d JOIN users u ON u.id = d.user_id WHERE u.email LIKE @p;
DELETE ad FROM addresses ad JOIN users u ON u.id = ad.user_id WHERE u.email LIKE @p;
DELETE w FROM wishlists w JOIN users u ON u.id = w.user_id WHERE u.email LIKE @p;
DELETE t FROM refresh_tokens t JOIN users u ON u.id = t.user_id WHERE u.email LIKE @p;
DELETE t FROM password_reset_tokens t JOIN users u ON u.id = t.user_id WHERE u.email LIKE @p;
DELETE r FROM user_roles r JOIN users u ON u.id = r.user_id WHERE u.email LIKE @p;
DELETE FROM contact_messages WHERE email LIKE @p;
DELETE FROM users WHERE email LIKE @p;`);
console.log(`Removed test accounts matching ${pattern}: ${db(`SELECT COUNT(*) FROM users WHERE email LIKE '${pattern}'`).trim()} left`);
