import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  catalogD1Statements,
  comMenuCatalog,
  comMenuD1Statements,
} from './import_com_menu';
import { gourmetMenuCatalog } from './import_gourmet_menu';

test('COM importer creates a complete D1-only transaction', () => {
  const statements = comMenuD1Statements('.COM menu');
  const sql = statements.join('\n');

  assert.ok(statements.length > 100);
  assert.match(sql, /INSERT INTO menu/);
  assert.match(sql, /Hamburguesa doble carne/);
  assert.match(sql, /23000/);
  assert.doesNotMatch(sql, /restaurant\.sqlite|DatabaseSync/);
});

test('generic catalog statements preserve nested special categories', () => {
  const statements = catalogD1Statements(gourmetMenuCatalog, 'Gourmet');
  const sql = statements.join('\n');

  assert.ok(statements.length > 100);
  assert.match(sql, /Gourmet/);
  assert.match(sql, /Bebidas/);
  assert.match(sql, /is_special/);
});

test('COM seed still exposes its category hierarchy', () => {
  assert.ok(comMenuCatalog.some((category) => category.name === 'Hamburguesa'));
  assert.ok(comMenuCatalog.some((category) => category.name === 'Promociones'));
});
