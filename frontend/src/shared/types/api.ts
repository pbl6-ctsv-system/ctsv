import type {createAuthClient} from '../auth/auth-client.mjs';
export type AuthClient = ReturnType<typeof createAuthClient>;
export type Data = Record<string, unknown>;
export type FieldLabels = Record<string, string>;
