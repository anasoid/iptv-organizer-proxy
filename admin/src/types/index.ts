export interface AdminUser {
  id: number;
  username: string;
  email: string | null;
  isActive: boolean;
  lastLogin: string | null;
  createdAt: string;
  updatedAt: string;
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

export interface Client {
  id: number;
  sourceId: number;
  filterId: number | null;
  username: string;
  password: string | null;
  name: string | null;
  email: string | null;
  expiryDate: string | null;
  isActive: boolean;
  hideAdultContent: boolean;
  enableProxy: boolean | null;
  connectXtreamApi: 'INHERITED' | 'DEFAULT' | 'NO_PROXY' | null;
  connectXtreamStream: 'INHERITED' | 'DIRECT' | 'NO_PROXY' | 'PROXY' | 'REDIRECT' | 'DEFAULT' | null;
  connectXmltv: 'INHERITED' | 'REDIRECT' | 'TUNNEL' | 'NO_PROXY' | 'DEFAULT' | null;
  notes: string | null;
  lastLogin: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Source {
  id: number;
  name: string;
  url: string;
  username: string | null;
  password: string | null;
  syncInterval: number;
  lastSync: string | null;
  nextSync: string | null;
  isActive: boolean;
  proxyId: number | null;
  enableProxy: boolean | null;
  connectXtreamApi: 'DEFAULT' | 'NO_PROXY' | null;
  connectXtreamStream: 'DIRECT' | 'NO_PROXY' | 'PROXY' | 'REDIRECT' | 'DEFAULT' | null;
  connectXmltv: 'REDIRECT' | 'TUNNEL' | 'NO_PROXY' | 'DEFAULT' | null;
  createdAt: string;
  updatedAt: string;
}

export interface Category {
  id: number;
  sourceId: number;
  externalId: number;
  num: number;
  name: string;
  type: 'live' | 'vod' | 'series';
  allowDeny: 'allow' | 'deny' | null;
  parentId: number | null;
  labels: string | null;
  blackList: 'default' | 'hide' | 'visible' | 'force_hide' | 'force_visible' | null;
  createdAt: string;
  updatedAt: string;
}

export interface Stream {
  id: number;
  sourceId: number;
  externalId: number;
  num: number;
  name: string;
  categoryId: number | null;
  categoryIds: string | null;
  isAdult: boolean;
  allowDeny: 'allow' | 'deny' | null;
  labels: string | null;
  data: Record<string, unknown> | null;
  addedDate: string | null;
  releaseDate: string | null;
  rating: number | null;
  tmdb: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface Filter {
  id: number;
  name: string;
  description: string | null;
  filterConfig: string | null;
  useSourceFilter: boolean;
  favoris: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SyncLog {
  id: number;
  sourceId: number;
  syncType: string;
  startedAt: string;
  completedAt: string | null;
  status: 'running' | 'completed' | 'failed' | 'interrupted';
  itemsAdded: number | null;
  itemsUpdated: number | null;
  itemsDeleted: number | null;
  errorMessage: string | null;
  durationSeconds: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface Proxy {
  id: number;
  name: string;
  description: string | null;
  proxyUrl: string | null;
  proxyHost: string | null;
  proxyPort: number | null;
  proxyType: 'HTTP' | 'HTTPS' | 'SOCKS5' | null;
  proxyUsername: string | null;
  proxyPassword: string | null;
  timeout: number | null;
  maxRetries: number | null;
  createdAt: string;
  updatedAt: string;
}
