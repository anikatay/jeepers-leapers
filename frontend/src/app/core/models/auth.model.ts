export interface AuthRequest {
    email: string;
    password: string;
}

export interface AuthResponse{
    token: string;
    userId: string;
    accountId: string | null;
    role: string;
    firstName?: string;
    lastName?: string;
}