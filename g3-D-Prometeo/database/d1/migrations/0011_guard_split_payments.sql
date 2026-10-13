-- A repeated charge request must fail atomically instead of recording a
-- second payment or incrementing sales metrics again.
CREATE TRIGGER order_split_accounts_pay_once
BEFORE UPDATE OF status ON order_split_accounts
WHEN NEW.status = 'paid' AND OLD.status <> 'draft'
BEGIN
  SELECT RAISE(ABORT, 'split account already paid');
END;
