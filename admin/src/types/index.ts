export interface AdminUser {
  id: number;
  username: string;
  email: string | null;
  is_active: boolean;
  last_login: string | null;
  created_at: string;
  updated_at: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: AdminUser;
}

export interface ApiError {
  message: string;
  status?: number;
}
