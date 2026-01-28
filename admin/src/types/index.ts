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
  name: string | null;
  email: string | null;
  expiryDate: string | null;
  isActive: boolean;
  hideAdultContent: boolean;
  useRedirect: boolean | null;
  useRedirectXmltv: boolean | null;
  enableProxy: boolean | null;
  disableStreamProxy: boolean | null;
  streamFollowLocation: boolean | null;
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
  syncInterval: number;
  lastSync: string | null;
  nextSync: string | null;
  isActive: boolean;
  enableProxy: boolean;
  disableStreamProxy: boolean;
  streamFollowLocation: boolean;
  useRedirect: boolean | null;
  useRedirectXmltv: boolean | null;
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
