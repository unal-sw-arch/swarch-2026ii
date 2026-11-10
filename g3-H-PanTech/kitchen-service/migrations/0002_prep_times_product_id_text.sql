-- Los IDs de producto del catálogo son slugs ("p-cafe-americano"), no UUID.
-- La tabla nació con uuid por error y cualquier producto sin `prep_minutes`
-- hacía fallar el enqueue (el Worker reintentaba y terminaba en la DLQ).
ALTER TABLE kitchen.prep_times
    ALTER COLUMN product_id TYPE text USING product_id::text;
