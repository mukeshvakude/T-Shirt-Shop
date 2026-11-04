export type Role = 'ROLE_CUSTOMER' | 'ROLE_ADMIN';

export interface AuthUser {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  /** Order updates on WhatsApp (also the default for the checkout checkbox). */
  whatsappOptIn?: boolean;
  emailVerified: boolean;
  roles: Role[];
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  expiresAt: string;
  user: AuthUser;
}
