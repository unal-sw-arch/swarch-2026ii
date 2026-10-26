import type {
  CategoryRow,
  IngredientRow,
  MenuAssignmentRow,
  MenuRow,
  ProductRow,
} from '../../types';

type IdRelation = { ownerId: number; valueId: number };
type IngredientCategoryRow = { id: number; name: string };

async function rows<T>(statement: D1PreparedStatement): Promise<T[]> {
  return (await statement.all<T>()).results;
}

function valuesFor(relations: IdRelation[], ownerId: number): number[] {
  return relations
    .filter((relation) => relation.ownerId === ownerId)
    .map((relation) => relation.valueId);
}

export async function readCatalog(database: D1Database, onlyMenuId?: number) {
  const menuFilter = onlyMenuId === undefined ? '' : ' WHERE id = ?';
  const relatedFilter = onlyMenuId === undefined ? '' : ' WHERE menu_id = ?';
  const menuStatement = database.prepare(
    `SELECT id, name FROM menu${menuFilter} ORDER BY name COLLATE NOCASE, id`,
  );
  const assignmentStatement = database.prepare(
    `SELECT menu_id AS menuId, hall_id AS hallId, is_primary AS isPrimary
     FROM menu_halls${relatedFilter} ORDER BY hall_id, menu_id`,
  );
  const categoryStatement = database.prepare(
    `SELECT id, menu_id AS menuId, name,
            parent_category_id AS parentCategoryId,
            is_special AS isSpecial, position
     FROM menu_categories${relatedFilter}
     ORDER BY menu_id, parent_category_id IS NOT NULL, parent_category_id,
              position, name COLLATE NOCASE, id`,
  );
  const productStatement = database.prepare(
    `SELECT p.id, p.name, p.description, p.value, p.menu_id AS menuId,
            p.category_id AS categoryId, p.is_active AS isActive,
            ordering.position
     FROM products p
     LEFT JOIN category_product_positions ordering
       ON ordering.category_id = p.category_id
      AND ordering.product_id = p.id
     ${onlyMenuId === undefined ? '' : 'WHERE p.menu_id = ?'}
     ORDER BY p.menu_id, p.category_id, ordering.position IS NULL,
              ordering.position, p.name COLLATE NOCASE, p.id`,
  );
  const ingredientRelationStatement = database.prepare(
    `SELECT pi.product_id AS ownerId, pi.ingredient_id AS valueId
     FROM product_ingredients pi
     JOIN products p ON p.id = pi.product_id
     ${onlyMenuId === undefined ? '' : 'WHERE p.menu_id = ?'}
     ORDER BY pi.product_id, pi.ingredient_id`,
  );
  const hallRelationStatement = database.prepare(
    `SELECT ph.product_id AS ownerId, ph.hall_id AS valueId
     FROM product_halls ph
     JOIN products p ON p.id = ph.product_id
     ${onlyMenuId === undefined ? '' : 'WHERE p.menu_id = ?'}
     ORDER BY ph.product_id, ph.hall_id`,
  );

  const bindMenu = <T extends D1PreparedStatement>(statement: T): T =>
    (onlyMenuId === undefined ? statement : statement.bind(onlyMenuId)) as T;

  const [menus, assignments, categories, products, productIngredients,
    productHalls, ingredients, ingredientCategories] = await Promise.all([
    rows<MenuRow>(bindMenu(menuStatement)),
    rows<MenuAssignmentRow>(bindMenu(assignmentStatement)),
    rows<CategoryRow>(bindMenu(categoryStatement)),
    rows<ProductRow>(bindMenu(productStatement)),
    rows<IdRelation>(bindMenu(ingredientRelationStatement)),
    rows<IdRelation>(bindMenu(hallRelationStatement)),
    rows<IngredientRow>(database.prepare(
      `SELECT id, name, description, category_id AS categoryId
       FROM ingredients ORDER BY name COLLATE NOCASE, id`,
    )),
    rows<IngredientCategoryRow>(database.prepare(
      'SELECT id, name FROM ingredient_categories ORDER BY name COLLATE NOCASE, id',
    )),
  ]);

  const productFor = (product: ProductRow) => ({
    id: product.id,
    name: product.name,
    description: product.description,
    value: product.value,
    menuId: product.menuId,
    categoryId: product.categoryId,
    isActive: product.isActive === 1,
    ingredientIds: valuesFor(productIngredients, product.id),
    hallIds: valuesFor(productHalls, product.id),
  });

  const menusWithRelations = menus.map((menu) => {
    const menuCategories = categories.filter((category) => category.menuId === menu.id);
    const buildCategory = (category: CategoryRow): Record<string, unknown> => ({
      id: category.id,
      name: category.name,
      menuId: menu.id,
      parentCategoryId: category.parentCategoryId,
      isSpecial: category.isSpecial === 1,
      products: products
        .filter((product) => product.categoryId === category.id && product.isActive === 1)
        .map(productFor),
      subcategories: menuCategories
        .filter((candidate) => candidate.parentCategoryId === category.id)
        .map(buildCategory),
    });

    return {
      id: menu.id,
      name: menu.name,
      hallAssignments: assignments
        .filter((assignment) => assignment.menuId === menu.id)
        .map((assignment) => ({
          hallId: assignment.hallId,
          isPrimary: assignment.isPrimary === 1,
        })),
      categories: menuCategories
        .filter((category) => category.parentCategoryId === null)
        .map(buildCategory),
    };
  });

  return {
    menus: menusWithRelations,
    ingredients,
    ingredientCategories: ingredientCategories.map((category) => ({
      ...category,
      ingredients: ingredients.filter((ingredient) => ingredient.categoryId === category.id),
    })),
  };
}

export async function readHalls(database: D1Database, employeeId?: number) {
  if (employeeId !== undefined) {
    return rows<{
      id: number;
      name: string;
      tableCount: number;
    }>(database.prepare(
      `SELECT h.id, h.name, COUNT(t.id) AS tableCount
       FROM employee_halls eh
       JOIN hall h ON h.id = eh.hall_id
       LEFT JOIN hall_tables t ON t.hall_id = h.id
       WHERE eh.user_id = ?
       GROUP BY h.id, h.name
       ORDER BY h.name COLLATE NOCASE, h.id`,
    ).bind(employeeId));
  }
  return rows<{
    id: number;
    name: string;
    tableCount: number;
    orderCount: number;
    totalSales: number;
    averageSale: number;
  }>(database.prepare(
    `SELECT h.id, h.name,
            (SELECT COUNT(*) FROM hall_tables ht WHERE ht.hall_id = h.id) AS tableCount,
            COALESCE(metrics.order_count, 0) AS orderCount,
            COALESCE(metrics.total_sales, 0) AS totalSales,
            CASE WHEN COALESCE(metrics.order_count, 0) = 0 THEN 0
              ELSE ROUND(metrics.total_sales * 1.0 / metrics.order_count) END AS averageSale
     FROM hall h LEFT JOIN hall_sales_totals metrics ON metrics.hall_id = h.id
     ORDER BY h.name COLLATE NOCASE, h.id`,
  ));
}

export async function readLayout(database: D1Database, hallId: number) {
  const room = await database.prepare(
    'SELECT id, name FROM hall WHERE id = ?',
  ).bind(hallId).first<{ id: number; name: string }>();
  if (!room) return undefined;

  const [tables, walls, groups, members] = await Promise.all([
    rows(database.prepare(
      `SELECT id, identifier, x, y, width, height, rotation, status
       FROM hall_tables WHERE hall_id = ? ORDER BY id`,
    ).bind(hallId)),
    rows(database.prepare(
      `SELECT id, x, y, width, height, rotation
       FROM hall_walls WHERE hall_id = ? ORDER BY id`,
    ).bind(hallId)),
    rows<{ id: number; identifier: string }>(database.prepare(
      `SELECT id, visible_identifier AS identifier
       FROM table_groups WHERE hall_id = ? ORDER BY id`,
    ).bind(hallId)),
    rows<{ groupId: number; tableId: number }>(database.prepare(
      `SELECT m.group_id AS groupId, m.table_id AS tableId
       FROM table_group_members m
       JOIN table_groups g ON g.id = m.group_id
       WHERE g.hall_id = ? ORDER BY m.group_id, m.table_id`,
    ).bind(hallId)),
  ]);

  return {
    room,
    tables,
    walls,
    groups: groups.map((group) => ({
      ...group,
      tableIds: members
        .filter((member) => member.groupId === group.id)
        .map((member) => member.tableId),
    })),
  };
}
