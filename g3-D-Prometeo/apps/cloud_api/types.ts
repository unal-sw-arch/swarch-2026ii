export type CloudBindings = {
  DB: D1Database;
  RESTAURANT_COORDINATOR: DurableObjectNamespace;
  ENVIRONMENT?: string;
  // Comma-separated list of browser origins allowed via CORS. When unset the
  // Worker falls back to the local development clients (ports 8080/8081).
  ALLOWED_ORIGINS?: string;
};

export type AuthenticatedSession = {
  sessionId: number;
  expiresAt: string;
  user: {
    id: number;
    fullName: string;
    username: string;
    role: string;
  };
};

export type CloudEnvironment = {
  Bindings: CloudBindings;
  Variables: { session: AuthenticatedSession };
};

export type MenuRow = {
  id: number;
  name: string;
};

export type MenuAssignmentRow = {
  menuId: number;
  hallId: number;
  isPrimary: number;
};

export type CategoryRow = {
  id: number;
  menuId: number;
  name: string;
  parentCategoryId: number | null;
  isSpecial: number;
  position: number;
};

export type ProductRow = {
  id: number;
  name: string;
  description: string | null;
  value: number;
  menuId: number;
  categoryId: number;
  isActive: number;
  position: number | null;
};

export type IngredientRow = {
  id: number;
  name: string;
  description: string | null;
  categoryId: number;
};
