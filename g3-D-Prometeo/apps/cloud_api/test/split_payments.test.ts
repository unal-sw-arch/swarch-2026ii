import assert from 'node:assert/strict';
import { test } from 'node:test';
import { splitChargeSubtotal } from '../features/orders/split_payments';

test('two half-unit charges sum to an odd peso price', () => {
  const first = splitChargeSubtotal([
    { unitValue: 1001, paidHalfUnits: 0, halfUnits: 1 },
  ]);
  const second = splitChargeSubtotal([
    { unitValue: 1001, paidHalfUnits: 1, halfUnits: 1 },
  ]);
  assert.equal(first, 500);
  assert.equal(second, 501);
  assert.equal(first + second, 1001);
});

test('one whole unit and two halves reconcile across separate products', () => {
  const first = splitChargeSubtotal([
    { unitValue: 1001, paidHalfUnits: 0, halfUnits: 2 },
    { unitValue: 5999, paidHalfUnits: 0, halfUnits: 1 },
  ]);
  const second = splitChargeSubtotal([
    { unitValue: 5999, paidHalfUnits: 1, halfUnits: 1 },
  ]);
  assert.equal(first + second, 7000);
});
