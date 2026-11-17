export class ApiError extends Error {
 constructor(status,code,fields=[]){super(code);this.status=status;this.code=code;this.fields=fields;}
}
export function createAuthClient({fetcher=fetch,locks=navigator.locks,storage=localStorage,channel=new BroadcastChannel('ctsv-auth-state')}={}){
 let access=null;let expires=0;let flight=null;
 const listeners=new Set();
 const uncertain='ctsv.auth.uncertain';
 channel.onmessage=event=>{if(event.data==='signed-out'||event.data==='login-changed'){access=null;expires=0;for(const listener of listeners)listener();}};
 const locked=fn=>{if(!locks)throw new ApiError(503,'WEB_LOCKS_REQUIRED');return locks.request('ctsv-auth-cookie',fn)};
 async function cookie(action,body){
  return locked(async()=>{
   if(action==='lam-moi-token'&&storage.getItem(uncertain))throw new ApiError(401,'LOGIN_REQUIRED');
   // Marker contains no token; persists a failed/aborted/crashed rotation as login-required.
   storage.setItem(uncertain,'1');
   try{
    const response=await fetcher('/api/xac-thuc/'+action,{method:'POST',credentials:'include',cache:'no-store',signal:AbortSignal.timeout(20000),headers:{'X-CSRF-Protection':'1',...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),redirect:'error'});
    if(!response.ok){access=null;expires=0;const error=await response.json().catch(()=>({code:'REQUEST_FAILED'}));throw new ApiError(response.status,error.code);}
    if(action==='dang-xuat'){access=null;expires=0;channel.postMessage('signed-out');storage.removeItem(uncertain);return;}
    const value=await response.json();if(typeof value.accessToken!=='string'||value.expiresIn!==900)throw new ApiError(503,'INVALID_AUTH_RESPONSE');
    access=value.accessToken;expires=Date.now()+900000;
    storage.removeItem(uncertain);
    if(action==='dang-nhap')channel.postMessage('login-changed');
    return value;
   }catch(error){access=null;expires=0;throw error;}
  });
 }
 const refresh=()=>{if(!flight)flight=cookie('lam-moi-token').finally(()=>flight=null);return flight;};
 return {
  subscribe:listener=>{listeners.add(listener);return()=>{listeners.delete(listener);};},
  token:()=>access,
  login:(username,password)=>cookie('dang-nhap',{username,password}),
  refresh,
  logout:()=>cookie('dang-xuat'),
  async request(path,options={}){
   if(!path.startsWith('/api/')||path.startsWith('/api/xac-thuc/dang-xuat'))throw new Error('Unsupported API request');
   if(!access||Date.now()>=expires-10000)await refresh();
   const response=await fetcher(path,{...options,redirect:'error',cache:'no-store',signal:options.signal??AbortSignal.timeout(20000),credentials:'include',headers:{...options.headers,Authorization:'Bearer '+access}});
   if(!response.ok){
    const value=await response.json().catch(()=>null);
    const fields=Array.isArray(value?.errors)
     ? value.errors.map(item=>item?.field).filter(field=>typeof field==='string') : [];
    // Never blindly retry writes or turn a revocation into refresh loops.
    if(response.status===401){access=null;expires=0;}
    throw new ApiError(response.status,value?.code??'REQUEST_FAILED',fields);
   }
   return response;
  }
 };
}
