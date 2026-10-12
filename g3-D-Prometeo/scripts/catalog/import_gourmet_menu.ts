import {
  insertCatalogMenuIntoD1,
  type CategorySeed,
  type ProductSeed,
} from './import_com_menu';

const productsAtPrices = (
  names: readonly string[],
  value: number,
): readonly ProductSeed[] => names.map((name) => [name, value] as const);

const sizedProducts = (
  names: readonly string[],
  sizes: readonly [name: string, value: number][],
): readonly CategorySeed[] => sizes.map(([name, value]) => ({
  name,
  products: productsAtPrices(names, value),
}));

const specialProductsAtPrices = (
  names: readonly string[],
  value: number,
): readonly ProductSeed[] => productsAtPrices(names, value);

const classicPizzas = [
  'Hawaiana',
  'Pepperoni',
  'Pollo champiñón',
  'Criolla',
  'Pollo BBQ',
  'Mexicana',
  'Pepperoni champiñón',
  'Costillita',
  'Miel mostaza',
  'Vegetales',
  'Napolitana',
  'Argentina',
  'Montañera',
] as const;

const gourmetPizzas = [
  'Costeña',
  'Del campo',
  'Texana',
  'Colombiana',
  'Azteca',
  'Camarones crujientes',
  'Camarones al ajillo',
  'Monstruo',
  'Paipana',
] as const;

const sodaFlavors = ['Coca-Cola', 'Coca-Cola Zero', 'Quatro', 'Sprite'] as const;
const milkshakeFlavors = [
  'Vainilla',
  'Chocolate',
  'Frutos del bosque',
  'Arequipe',
  'Oreo',
  'Ferrero',
  'Yogurt de cereza',
] as const;
const naturalJuiceFlavors = [
  'Fresa',
  'Mora',
  'Maracuyá',
  'Mango',
  'Sandía',
  'Lulo',
  'Piña',
  'Guanábana',
] as const;

export const gourmetMenuCatalog: readonly CategorySeed[] = [
  {
    name: 'Hamburguesas artesanales',
    products: [
      ['Corriente', 17800],
      ['Mix queso', 20400],
      ['Tocineta', 22900],
      ['Criolla', 23200],
      ['Mexicana', 22400],
      ['Doble carne', 27600],
      ['Súper especial', 30400],
      ['Street 45', 23600],
      ['Sweet chili', 21900],
      ['Pollo alcachofa', 26200],
    ],
  },
  {
    name: 'Sandwich',
    children: [
      {
        name: 'Bebé (20 cm)',
        products: [
          ['Carnudo', 20400],
          ['Argentino', 23400],
          ['Colombiano', 28900],
          ['Rex', 29400],
          ['Mexicano', 30400],
          ['Súper especial', 30400],
          ['Godzilla', 31500],
          ['Monstruo', 31500],
        ],
      },
      {
        name: 'Gigante (35 cm)',
        products: [
          ['Godzilla', 59600],
          ['Monstruo', 54400],
        ],
      },
    ],
  },
  {
    name: 'Arepas',
    products: [
      ['Solterona', 20100],
      ['Veterana', 21500],
      ['Quinceañera', 21500],
      ['Ranchera', 23400],
      ['Universitaria', 23400],
      ['Colombiana', 26300],
      ['Mexicana', 26300],
      ['La Vecina con todo', 30400],
    ],
  },
  {
    name: 'Perros',
    products: [
      ['Corriente', 14900],
      ['Hawaiano', 16000],
      ['Tocineta', 20200],
      ['Ranchero', 24300],
      ['Con todo', 29700],
    ],
  },
  {
    name: 'Nachos',
    children: [
      {
        name: 'Personal',
        products: [
          ['Clásicos', 23300],
          ['Vegetales', 21000],
          ['Cochinilla pibil', 25800],
        ],
      },
      {
        name: 'A lo grandote',
        products: [['Tostinachos', 40700]],
      },
    ],
  },
  {
    name: 'Burros',
    children: [
      {
        name: 'Nuestra base',
        products: [
          ['Res', 27400],
          ['Cerdo', 26000],
          ['Pollo', 26600],
        ],
      },
      {
        name: 'Especiales',
        products: [
          ['Mixto res pollo', 31200],
          ['Zacateca', 35200],
          ['Yucateco', 35200],
        ],
      },
    ],
  },
  {
    name: 'Salchipapas',
    products: [
      ['Choripapa', 21200],
      ['Americana', 25700],
      ['La Vecina', 53400],
      ['Ranchera', 42900],
      ['De mi tierra para 4', 98400],
      ['De mi tierra XL para 6', 152400],
    ],
  },
  {
    name: 'Mazorcadas',
    products: [
      ['Vegetariana', 24400],
      ['Sencilla', 26500],
      ['Azteca', 30200],
      ['Mixta', 31200],
      ['La Nacional', 32200],
      ['Especial', 38900],
      ['Especial dúo', 52500],
    ],
  },
  {
    name: 'Parrilla',
    products: [
      ['Pechuga 250 g', 32700],
      ['Churrasco 250 g', 43400],
      ['Muslitos de pollo', 25600],
      ['Especial mixto', 62300],
      ['Costilla BBQ 250 g', 28000],
    ],
  },
  {
    name: 'Pizzas',
    children: [
      {
        name: 'Clásicas',
        children: sizedProducts(classicPizzas, [
          ['Personal (20 cm)', 22500],
          ['Medium (40 cm)', 54500],
          ['Large (60 cm)', 73400],
        ]),
      },
      {
        name: 'Gourmet',
        children: sizedProducts(gourmetPizzas, [
          ['Personal (20 cm)', 26700],
          ['Medium (40 cm)', 65500],
          ['Large (60 cm)', 84400],
        ]),
      },
    ],
  },
  {
    name: 'Pizza cono',
    children: [
      {
        name: 'Salada',
        products: [
          ['Exótica', 18000],
          ['Monstruo', 20400],
          ['Mexicana', 22400],
        ],
      },
      {
        name: 'Dulce tentación',
        children: [
          {
            name: 'Sin helado',
            products: productsAtPrices(['Banano', 'Fresa', 'Manzana'], 14400),
          },
          {
            name: 'Con helado',
            products: productsAtPrices(['Banano', 'Fresa', 'Manzana'], 16400),
          },
        ],
      },
    ],
  },
  {
    name: 'Lasañas',
    children: [
      {
        name: 'Junior',
        products: [
          ['Boloñesa', 25500],
          ['Mixta', 25500],
          ['Pollo champiñón', 25500],
          ['Vegetal mix', 24500],
        ],
      },
      {
        name: 'Grande',
        products: [
          ['Boloñesa', 30500],
          ['Mixta', 30500],
          ['Pollo champiñón', 30500],
          ['Vegetal mix', 28500],
        ],
      },
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
          ['Limonada natural', 10300],
          ['Coca-Cola mini', 10300],
          ['Coca-Cola Zero mini', 10300],
          ['Quatro mini', 10300],
          ['Sprite mini', 10300],
        ],
      },
      {
        name: 'Agrandado',
        special: true,
        products: specialProductsAtPrices([
          'Póker',
          'Águila',
          'Club Colombia roja',
          'Club Colombia trigo',
          'Club Colombia dorada',
          'Limonada natural',
          'Limonada cherry',
          'Limonada kiwi',
          'Limonada mango biche',
          'Limonada hierbabuena',
          'Agua',
          'Agua con gas',
          'Sprite',
          'Coca-Cola',
          'Coca-Cola Zero',
          'Quatro',
          'Fuze Tea',
        ], 14000),
      },
    ],
  },
  {
    name: 'Bebidas',
    special: true,
    children: [
      {
        name: 'Smoothies (500 ml)',
        special: true,
        products: productsAtPrices([
          'Arcoíris',
          'Mango tango',
          'Suspiro',
          'Hakuna',
          'Piña colada sin licor',
        ], 13500),
      },
      {
        name: 'Limonadas (500 ml)',
        special: true,
        products: [
          ['Natural', 8100],
          ['Cherry', 11000],
          ['Hierbabuena', 11000],
          ['Sandía', 11500],
          ['Kiwi', 11000],
          ['Coco', 11500],
          ['Mango biche', 11500],
        ],
      },
      {
        name: 'Jugos naturales (500 ml)',
        special: true,
        children: [
          {
            name: 'En agua',
            special: true,
            products: productsAtPrices(naturalJuiceFlavors, 10700),
          },
          {
            name: 'En leche',
            special: true,
            products: productsAtPrices(naturalJuiceFlavors, 11700),
          },
        ],
      },
      {
        name: 'Gaseosas',
        special: true,
        children: [
          {
            name: '400 ml',
            special: true,
            products: productsAtPrices(sodaFlavors, 6000),
          },
          {
            name: '600 ml',
            special: true,
            products: productsAtPrices(sodaFlavors, 7500),
          },
        ],
      },
      {
        name: 'Cervezas nacionales',
        special: true,
        products: productsAtPrices([
          'Póker',
          'Águila',
          'Andina',
          'Cola y Pola',
        ], 6000),
      },
      {
        name: 'Agua',
        special: true,
        products: [['Botella', 5000]],
      },
      {
        name: 'Club Colombia',
        special: true,
        products: productsAtPrices(['Roja', 'Negra', 'Dorada'], 6200),
      },
      {
        name: 'Malteadas',
        special: true,
        children: [
          {
            name: 'Junior',
            special: true,
            products: productsAtPrices(milkshakeFlavors, 12600),
          },
          {
            name: 'Grande',
            special: true,
            products: productsAtPrices(milkshakeFlavors, 17000),
          },
        ],
      },
      {
        name: 'Sodas colombianas',
        special: true,
        products: productsAtPrices([
          'Tamarindo',
          'Feijoa',
          'Uchuva',
          'Maracuyá',
        ], 12700),
      },
      {
        name: 'Macerados (500 ml)',
        special: true,
        products: productsAtPrices([
          'Lulo lychees',
          'Frutos tropicales',
          'Piña pasión',
          'Sakura',
          'Dulce amor',
        ], 16000),
      },
    ],
  },
];

if (require.main === module) {
  const menuName = 'Gourmet';
  insertCatalogMenuIntoD1(gourmetMenuCatalog, menuName);
  console.log(`Menú "${menuName}" importado en D1.`);
}
