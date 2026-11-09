export type AuthTokens = {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  refresh_expires_in: number;
};

export type UserSummary = {
  id: string;
  email: string;
  full_name: string;
  active: boolean;
  roles: string[];
  permissions: string[];
  level: number;
  created_at: string;
  last_login_at: string | null;
};

export type AuthResponse = AuthTokens & {
  user: UserSummary;
};
