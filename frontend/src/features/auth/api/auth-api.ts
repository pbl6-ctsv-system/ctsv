import type {AuthClient} from '../../../shared/types/api';
export const AUTH_API='/api/xac-thuc';
export async function readCurrentAccount(client:AuthClient){return (await client.request(AUTH_API+'/toi')).json();}
