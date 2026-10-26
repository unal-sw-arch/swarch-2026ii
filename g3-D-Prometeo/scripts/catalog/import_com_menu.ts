import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

export type ProductSeed = readonly [name: string, value: number];
export type CategorySeed = {
  readonly name: string;
  readonly special?: boolean;
  readonly products?: readonly ProductSeed[];
  readonly children?: readonly CategorySeed[];
};

const D1_BATCH_SIZE = 20;
const promotionRenames = <const>[
  ['Perro sencillo · papas mixtas · limonada 12 oz', 'Perro sencillo'],
  [
    'Hamburguesa doble carne · papas mixtas · limonada 12 oz',
    'Hamburguesa doble carne',
  ],
  [
    'Hamburguesa crunchy tocineta · papas mixtas · limonada 12 oz',
    'Hamburguesa crunchy tocineta',
  ],
  ['Sándwich BBQ costillita personal', 'Costillita personal'],
  ['Sándwich BBQ costillita dúo', 'Costillita dúo'],
  ['Sándwich BBQ carne burger personal', 'Carne burger personal'],
  ['Sándwich BBQ carne burger dúo', 'Carne burger dúo'],
];

function sqlText(value: string): string {
  return `'${value.replaceAll("'", "''")}'`;
}

function d1MenuId(menuName: string): string {
  return `(SELECT id FROM menu WHERE name = ${sqlText(menuName)} ORDER BY id LIMIT 1)`;
}

function d1CategoryId(
  menuName: string,
  path: readonly CategorySeed[],
): string {
  const category = path.at(-1);
  if (!category) throw new Error('La ruta de categoría está vacía.');
  const parentId = path.length === 1
    ? 'NULL'
    : d1CategoryId(menuName, path.slice(0, -1));
  const parentPredicate = path.length === 1
    ? 'parent_category_id IS NULL'
    : `parent_category_id = ${parentId}`;
  return `(SELECT id FROM menu_categories
    WHERE menu_id = ${d1MenuId(menuName)}
      AND name = ${sqlText(category.name)}
      AND ${parentPredicate}
      AND is_special = ${category.special ? 1 : 0}
    ORDER BY id LIMIT 1)`;
}

function d1ProductId(
  menuName: string,
  path: readonly CategorySeed[],
  productName: string,
): string {
  return `(SELECT id FROM products
    WHERE menu_id = ${d1MenuId(menuName)}
      AND category_id = ${d1CategoryId(menuName, path)}
      AND name = ${sqlText(productName)}
    ORDER BY id LIMIT 1)`;
}

export function comMenuD1Statements(menuName = '.COM menu'): string[] {
  const promotionCategory = d1CategoryId(menuName, [
    { name: 'Promociones', special: true },
  ]);
  return [
    ...promotionRenames.map(([previous, next]) =>
      `UPDATE products SET name = ${sqlText(next)},
         is_active = CASE WHEN EXISTS (
           SELECT 1 FROM products current
           WHERE current.menu_id = products.menu_id
             AND current.category_id = products.category_id
             AND current.name = ${sqlText(next)}
             AND current.id != products.id
         ) THEN 0 ELSE is_active END
       WHERE menu_id = ${d1MenuId(menuName)}
         AND category_id = ${promotionCategory}
         AND name = ${sqlText(previous)}`),
    ...catalogD1Statements(comMenuCatalog, menuName),
  ];
}

export function catalogD1Statements(
  catalog: readonly CategorySeed[],
  menuName: string,
): string[] {
  const normalizedName = menuName.trim();
  if (!normalizedName) throw new Error('El nombre del menú no puede estar vacío.');
  const statements = [
    `INSERT INTO menu (name)
     SELECT ${sqlText(normalizedName)}
     WHERE NOT EXISTS (SELECT 1 FROM menu WHERE name = ${sqlText(normalizedName)})`,
  ];

  const addCategories = (
    categories: readonly CategorySeed[],
    parentPath: readonly CategorySeed[],
  ) => {
    categories.forEach((category, categoryPosition) => {
      const path = [...parentPath, category];
      const categoryId = d1CategoryId(normalizedName, path);
      const parentId = parentPath.length === 0
        ? 'NULL'
        : d1CategoryId(normalizedName, parentPath);
      const parentExists = parentPath.length === 0
        ? ''
        : ` AND ${parentId} IS NOT NULL`;
      statements.push(
        `INSERT INTO menu_categories
          (menu_id, name, parent_category_id, is_special, position)
         SELECT ${d1MenuId(normalizedName)}, ${sqlText(category.name)},
           ${parentId}, ${category.special ? 1 : 0}, ${categoryPosition}
         WHERE NOT EXISTS (SELECT 1 FROM menu_categories WHERE id = ${categoryId})${parentExists}`,
      );
      category.products?.forEach(([name, value], productPosition) => {
        const productId = d1ProductId(normalizedName, path, name);
        statements.push(
          `INSERT INTO products
            (name, description, value, menu_id, category_id, is_active)
           SELECT ${sqlText(name)}, NULL, ${value}, ${d1MenuId(normalizedName)},
             ${categoryId}, 1
           WHERE ${categoryId} IS NOT NULL
             AND NOT EXISTS (SELECT 1 FROM products WHERE id = ${productId})`,
          `INSERT OR IGNORE INTO category_product_positions
            (category_id, product_id, position)
           SELECT ${categoryId}, ${productId}, ${productPosition}
           WHERE ${categoryId} IS NOT NULL AND ${productId} IS NOT NULL`,
        );
      });
      if (category.children) addCategories(category.children, path);
    });
  };

  addCategories(catalog, []);
  return statements;
}

export function insertComMenuIntoD1(menuName = '.COM menu'): void {
  executeD1Statements(comMenuD1Statements(menuName));
}

export function insertCatalogMenuIntoD1(
  catalog: readonly CategorySeed[],
  menuName: string,
): void {
  executeD1Statements(catalogD1Statements(catalog, menuName));
}

function executeD1Statements(statements: readonly string[]): void {
  const wrangler = resolve(__dirname, '../../node_modules/.bin/wrangler');
  const batches = Math.ceil(statements.length / D1_BATCH_SIZE);
  for (let index = 0; index < statements.length; index += D1_BATCH_SIZE) {
    const batch = statements.slice(index, index + D1_BATCH_SIZE).join(';\n');
    const result = spawnSync(wrangler, [
      'd1', 'execute', 'DB', '--remote', '--command', batch, '--json',
    ], { encoding: 'utf8' });
    if (result.error) throw result.error;
    if (result.status !== 0) {
      process.stderr.write(result.stdout);
      process.stderr.write(result.stderr);
      throw new Error(`Falló el lote D1 ${Math.floor(index / D1_BATCH_SIZE) + 1}.`);
    }
    console.log(`D1: lote ${Math.floor(index / D1_BATCH_SIZE) + 1}/${batches} aplicado.`);
  }
}

const sandwichSizes = (
  baby: readonly ProductSeed[],
  giant: readonly ProductSeed[],
): readonly CategorySeed[] => [
  { name: 'Bebé (20 cm)', products: baby },
  { name: 'Gigante (35 cm)', products: giant },
];

export const comMenuCatalog: readonly CategorySeed[] = [
  {
    name: 'Hamburguesa',
    products: [
      ['Corriente 1/4 de libra', 15900],
      ['Doble queso', 18000],
      ['Hawaiana', 19900],
      ['Tocineta', 19900],
      ['Pollo', 22300],
      ['Pollo y tocineta', 26900],
      ['Doble carne y queso', 27900],
      ['Bistec', 27900],
      ['Rex', 31500],
      ['Súper especial con todo', 31500],
      ['Doble carne, pollo y tocineta', 35900],
    ],
  },
  {
    name: 'Salchipapa',
    products: [
      ['Corriente', 20900],
      ['Americana', 24200],
      ['Especial', 45000],
      ['La Vecina', 50100],
      ['De mi tierra', 97900],
      ['De mi tierra XL', 151900],
    ],
  },
  {
    name: 'Perro',
    children: [
      {
        name: 'Americano',
        products: [
          ['Sencillo', 14400],
          ['Hawaiano', 15500],
          ['Champiñón', 19900],
          ['Pollo', 19900],
          ['Carne', 20500],
          ['Tocineta', 19700],
          ['Pollo champiñón', 22900],
          ['Carne y mazorca', 22900],
          ['Pollo y tocineta', 22900],
          ['Ranchero', 23800],
          ['Mexicano', 30000],
          ['Con todo', 29200],
        ],
      },
      {
        name: 'Anaconda',
        products: [
          ['Sencillo', 18300],
          ['Pollo champiñón', 29900],
          ['Carne y mazorca', 29900],
          ['Pollo y tocineta', 29900],
          ['Ranchero', 31900],
          ['Mexicano', 39700],
          ['Con todo', 44700],
        ],
      },
      {
        name: 'Chorizo',
        products: [
          ['Sencillo', 14400],
          ['Hawaiano', 15500],
          ['Champiñón', 19900],
          ['Pollo', 19900],
          ['Carne', 20500],
          ['Tocineta', 19700],
          ['Pollo champiñón', 22900],
          ['Carne y mazorca', 22900],
          ['Pollo y tocineta', 22900],
          ['Ranchero', 23800],
          ['Mexicano', 30000],
          ['Con todo', 29200],
        ],
      },
    ],
  },
  {
    name: 'Mazorcada',
    products: [
      ['Sencilla', 24900],
      ['Vegetariana', 25500],
      ['Mixta', 26900],
      ['La Nacional', 29900],
      ['Especial', 38400],
    ],
  },
  {
    name: 'Sandwich',
    children: [
      {
        name: 'Ligeros',
        children: sandwichSizes(
          [
            ['Hawaiano', 15900],
            ['Carne de hamburguesa', 16400],
            ['Pollo', 22100],
            ['Carne desmechada', 22500],
          ],
          [
            ['Hawaiano', 31000],
            ['Carne de hamburguesa', 28600],
            ['Pollo', 32000],
            ['Carne desmechada', 34000],
          ],
        ),
      },
      {
        name: 'Mixtos',
        children: sandwichSizes(
          [
            ['Pollo y tocineta', 21400],
            ['Pollo y champiñón', 23900],
            ['Carne y tocineta', 23400],
            ['Carne y pollo', 25000],
          ],
          [
            ['Pollo y tocineta', 34500],
            ['Pollo y champiñón', 36300],
            ['Carne y tocineta', 34500],
            ['Carne y pollo', 39400],
          ],
        ),
      },
      {
        name: 'Especiales',
        children: sandwichSizes(
          [
            ['Vegetariano', 22500],
            ['Criollo', 24600],
            ['Ranchero', 25500],
            ['Costeño', 25600],
            ['Mexicano', 29900],
            ['Colombiano', 28400],
            ['Súper especial', 29900],
            ['Monstruo', 31000],
            ['Godzilla', 31000],
          ],
          [
            ['Vegetariano', 35900],
            ['Criollo', 37300],
            ['Ranchero', 39300],
            ['Costeño', 40500],
            ['Mexicano', 48900],
            ['Colombiano', 52900],
            ['Súper especial', 53900],
            ['Monstruo', 53900],
            ['Godzilla', 59100],
          ],
        ),
      },
    ],
  },
  {
    name: 'Arepa',
    products: [
      ['Estortillada', 9900],
      ['Estrellada', 9900],
      ['Calienta huevos', 9900],
      ['Contenta', 10900],
      ['Solterona', 15900],
      ['Quinceañera', 19500],
      ['Veterana', 19900],
      ['Ardiente', 21900],
      ['Universitaria', 23500],
      ['Gomela', 20900],
      ['Ranchera', 23500],
      ["Pa'l marido", 22500],
      ['Colombiana', 25800],
      ['Mexicana', 25800],
      ["Pa'l mozo", 27800],
      ["Pa'l novio", 29900],
      ['La Vecina con todo', 29900],
    ],
  },
  {
    name: 'Combo',
    special: true,
    children: [
      {
        name: 'Sencillo',
        special: true,
        products: [
          ['Limonada 12 oz', 8500],
          ['Coca-Cola mini', 8500],
          ['Quatro mini', 8500],
          ['Coca-Cola Zero mini', 8500],
          ['Sprite mini', 8500],
        ],
      },
      {
        name: 'Agrandado',
        special: true,
        products: [
          ['Fuze Tea durazno', 12500],
          ['Fuze Tea mango', 12500],
          ['Fuze Tea manzanilla', 12500],
          ['Fuze Tea limón', 12500],
          ['Fuze Tea manzana', 12500],
          ['Coca-Cola personal', 12500],
          ['Quatro personal', 12500],
          ['Coca-Cola Zero personal', 12500],
          ['Sprite personal', 12500],
          ['Jugo del Valle mango', 12500],
          ['Jugo del Valle naranja', 12500],
          ['Jugo del Valle mora', 12500],
          ['Agua con gas', 12500],
          ['Agua sin gas', 12500],
          ['Cerveza Águila', 12500],
          ['Cerveza Póker', 12500],
          ['Cerveza Cola y Pola', 12500],
          ['Limonada 16 oz', 12500],
        ],
      },
    ],
  },
  {
    name: 'Bebida',
    special: true,
    children: [
      {
        name: 'Gaseosa',
        special: true,
        products: [['Coca-Cola 1.5 L', 10800]],
        children: [
          {
            name: 'Personal',
            special: true,
            products: [
              ['Coca-Cola', 5800],
              ['Quatro', 5800],
              ['Coca-Cola Zero', 5800],
              ['Sprite', 5800],
            ],
          },
          {
            name: 'Mini',
            special: true,
            products: [
              ['Coca-Cola', 3500],
              ['Quatro', 3500],
              ['Coca-Cola Zero', 3500],
              ['Sprite', 3500],
            ],
          },
          {
            name: 'Mediana',
            special: true,
            products: [['Coca-Cola', 7900]],
          },
        ],
      },
      {
        name: 'Limonada',
        special: true,
        products: [
          ['12 oz', 3500],
          ['16 oz', 7900],
        ],
      },
      {
        name: 'Fuze Tea',
        special: true,
        products: [
          ['Durazno', 5800],
          ['Mango', 5800],
          ['Manzanilla', 5800],
          ['Limón', 5800],
          ['Manzana', 5800],
        ],
      },
      {
        name: 'Jugo del Valle',
        special: true,
        products: [
          ['Mango', 5800],
          ['Naranja', 5800],
          ['Mora', 5800],
        ],
      },
      {
        name: 'Agua',
        special: true,
        products: [
          ['Con gas', 5800],
          ['Sin gas', 5800],
        ],
      },
      {
        name: 'Cerveza',
        special: true,
        products: [
          ['Águila', 6000],
          ['Póker', 6000],
          ['Cola y Pola', 6000],
        ],
      },
    ],
  },
  {
    name: 'Adición',
    special: true,
    products: [
      ['Papa francesa', 8500],
      ['Papa criolla', 8500],
      ['Huevos de codorniz', 12500],
    ],
  },
  {
    name: 'Promociones',
    special: true,
    products: [
      ['Perro sencillo', 10500],
      ['Hamburguesa sencilla dúo', 19900],
      ['Mini choripapa', 15500],
      ['Mini mazorcada sencilla', 19900],
      ['Hamburguesa doble carne', 23000],
      ['Hamburguesa crunchy tocineta', 15800],
      ['Costillita personal', 22500],
      ['Costillita dúo', 39900],
      ['Carne burger personal', 19900],
      ['Carne burger dúo', 32900],
    ],
  },
];

if (require.main === module) {
  const arguments_ = process.argv.slice(2);
  const requestedName = arguments_.filter((value) => value !== '--d1')
    .join(' ').trim() || '.COM menu';
  insertComMenuIntoD1(requestedName);
  console.log(`Menú "${requestedName}" importado en D1.`);
}
