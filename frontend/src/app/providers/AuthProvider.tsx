import {createContext,useContext,useEffect,useState,type ReactNode} from 'react';
import {createAuthClient} from '../../shared/auth/auth-client.mjs';
import {useApiTask} from '../../shared/hooks/useApiTask';
import {readCurrentAccount} from '../../features/auth';
import type {AuthClient,Data} from '../../shared/types/api';

// One client per application/tab, shared by all features. Factory remains available to existing tests.
const client=createAuthClient();
type AuthState={client:AuthClient;me:Data|null;busy:boolean;error:string;login:(u:string,p:string)=>Promise<void>;restore:()=>Promise<void>;logout:()=>Promise<void>};
const AuthContext=createContext<AuthState|null>(null);
export function AuthProvider({children}:{children:ReactNode}){
 const[me,setMe]=useState<Data|null>(null),[changed,setChanged]=useState('');
 const{busy,error,run}=useApiTask();
 useEffect(()=>client.subscribe(()=>{setMe(null);setChanged('Trạng thái đăng nhập đã thay đổi ở tab khác. Vui lòng tiếp tục đăng nhập.');}),[]);
 async function login(username:string,password:string){await run(async()=>{setChanged('');await client.login(username,password);setMe(await readCurrentAccount(client));});}
 async function restore(){await run(async()=>{setChanged('');await client.refresh();setMe(await readCurrentAccount(client));});}
 async function logout(){await run(async()=>{await client.logout();setMe(null);setChanged('');});}
 return <AuthContext.Provider value={{client,me,busy,error:error||changed,login,restore,logout}}>{children}</AuthContext.Provider>;
}
export function useAuth(){const value=useContext(AuthContext);if(!value)throw new Error('AuthProvider is required');return value;}
