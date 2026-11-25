
import {useEffect,useRef,useState} from 'react';
import {AuthProvider,useAuth} from './providers/AuthProvider';
import {AppShell} from './layouts/AppShell';
import {navigationSections,normalizePath,resolveRoute} from './navigation/modules.mjs';
import {Navbar} from './navigation/Navbar';
import {ApiError} from '../shared/auth/auth-client.mjs';
import {LoginLayout,LoginPage,readCurrentAccount} from '../features/auth';
import {probeRoundAccess} from '../features/shcn';
import {featurePages} from './navigation/featureRegistry';
import {Alert} from '../shared/ui/Alert';
import {FeedbackProvider} from '../shared/ui/Feedback';
import {readScholarshipGroups} from '../features/hocbong';

function Workspace(){
 const auth=useAuth();
 const [path,setPath]=useState(()=>normalizePath(window.location.pathname));
 const [restoring,setRestoring]=useState(()=>path!=='/');
 const attemptedRestore=useRef(false);

 useEffect(()=>{
  const readPath=()=>setPath(normalizePath(window.location.pathname));
  window.addEventListener('popstate',readPath);
  return ()=>window.removeEventListener('popstate',readPath);
 },[]);

 useEffect(()=>{
  if(!restoring||attemptedRestore.current)return;
  attemptedRestore.current=true;
  void auth.restore().finally(()=>setRestoring(false));
 },[auth.restore,restoring]);

 function go(next:string,replace=false){
  if(!window.dispatchEvent(new Event('shcn-leave',{cancelable:true})))return;
  if(window.location.pathname!==next)window.history[replace?'replaceState':'pushState'](null,'',next);
  setPath(next);
 }

 if(auth.me)return <AccountWorkspace key={String(auth.me.accountId)} auth={auth} me={auth.me} path={path} onNavigate={go}/>;

 return <LoginLayout>
  {restoring?<p role="status">Đang khôi phục đăng nhập…</p>:<>
   {auth.error&&<Alert>{auth.error}</Alert>}
   <LoginPage onLogin={(username,password)=>{
    go('/',true);
    return auth.login(username,password);
   }}
    onRestore={auth.restore} busy={auth.busy}/>
  </>}
 </LoginLayout>;
}

type Auth=ReturnType<typeof useAuth>;
type MenuPermissions=Parameters<typeof navigationSections>;

type PermissionCheck={
 path:string;
 functions:MenuPermissions[0];
 access:MenuPermissions[1];
 scholarship:MenuPermissions[2];
 failed:boolean;
 scholarshipFailed:boolean;
 sessionEnded:boolean;
 shcnError:boolean;
};

function AccountWorkspace({auth,me,path,onNavigate}:{auth:Auth;me:NonNullable<Auth['me']>;path:string;onNavigate:(path:string)=>void}){
 const {client}=auth;
 const [verified,setVerified]=useState<PermissionCheck|null>(null);
 const ended=useRef(false);
 const accountId=String(me.accountId);

 useEffect(()=>{
  let pending:AbortController|undefined;

  async function recheck(){
   if(pending||ended.current)return;

   const abort=new AbortController();
   pending=abort;

   const [account,menu,shcn,scholarship]=await Promise.allSettled([
    readCurrentAccount(client),

    client.request('/api/ctsv/nen-tang/toi/quyen-giao-dien',{
     signal:AbortSignal.any([abort.signal,AbortSignal.timeout(20000)]),
    }).then(response=>response.json()),

    client.request('/api/shcn/khoa/pham-vi',{
     signal:AbortSignal.any([abort.signal,AbortSignal.timeout(20000)])
    }).then(response=>response.json()),

    readScholarshipGroups(client,abort.signal),
   ]);

   if(abort.signal.aborted)return;

   const sessionEnded=[account,menu,shcn,scholarship].some(
    result=>result.status==='rejected'&&
     result.reason instanceof ApiError&&
     result.reason.status===401
   );

   const sameAccount=account.status==='fulfilled'&&account.value?.accountId===accountId;

   const functions=sameAccount&&Array.isArray(account.value.functions)
    ?account.value.functions
    :null;

   const value=menu.status==='fulfilled'?menu.value:null;

   const validMenu=value&&
    typeof value.student==='boolean'&&
    typeof value.mghpSpecialist==='boolean'&&
    typeof value.ctsvRoleAdmin==='boolean';

   const shcnAllowed=shcn.status==='fulfilled'&&
    Array.isArray(shcn.value?.khoa)&&
    shcn.value.khoa.some(
     (k:{duocDoc:boolean;duocLapNhap:boolean})=>k.duocDoc||k.duocLapNhap
    );

   const round=await probeRoundAccess(
    client,
    functions??[],
    AbortSignal.any([abort.signal,AbortSignal.timeout(20000)])
   );

   if(abort.signal.aborted)return;

   ended.current=sessionEnded||round.sessionEnded;

   const noCtsvAccess=menu.status==='rejected'&&
    menu.reason instanceof ApiError&&
    menu.reason.status===403;

   const noScholarshipAccess=scholarship.status==='rejected'&&
    scholarship.reason instanceof ApiError&&
    scholarship.reason.status===403;

   setVerified({
    path,
    functions:ended.current?[]:functions??[],

    access:!ended.current&&functions&&
     (validMenu||shcnAllowed||round.read)
      ?{
        ...(validMenu?value:{
         student:false,
         mghpSpecialist:false,
         ctsvRoleAdmin:false
        }),
        shcnFacultyRead:shcnAllowed,
        shcnRoundRead:round.read
       }
      :null,

    scholarship:!ended.current&&sameAccount&&
     scholarship.status==='fulfilled'
      ?scholarship.value
      :null,

    shcnError:round.error||
     (shcn.status==='rejected'&&
      !(shcn.reason instanceof ApiError&&shcn.reason.status===403)),

    scholarshipFailed:scholarship.status==='rejected'&&!noScholarshipAccess,

    failed:!functions||
     (!validMenu&&!noCtsvAccess&&!shcnAllowed&&!round.read),

    sessionEnded:ended.current
   });

   pending=undefined;
  }

  function onVisible(){
   if(document.visibilityState==='visible')void recheck();
  }

  if(ended.current)return;

  setVerified(null);
  void recheck();

  document.addEventListener('visibilitychange',onVisible);

  return ()=>{
   pending?.abort();
   document.removeEventListener('visibilitychange',onVisible);
  };
 },[client,accountId,path]);

 const current=verified?.path===path?verified:null;
 const loading=current===null;

 const {
  functions=[],
  access=null,
  scholarship=null,
  failed=false,
  scholarshipFailed=false,
  shcnError=false
 }=current??{};

 const scholarshipRoute=path.startsWith('/hoc-bong/');

 const route=resolveRoute(path,functions,access,scholarship);

 const entry=route.kind==='page'?route.entry:null;
 const ActiveFeature=entry&&featurePages[entry.feature];

 if(verified?.sessionEnded)return <AppShell busy={auth.busy} onLogout={auth.logout}>
  <Alert>Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.</Alert>
  <a href="/">Về trang đăng nhập</a>
 </AppShell>;

 return <AppShell username={String(me.username)} busy={auth.busy} onLogout={auth.logout}
  navigation={<Navbar sections={navigationSections(functions,access,scholarship)} path={path} onNavigate={onNavigate}/>}>
  {auth.error&&<Alert>{auth.error}</Alert>}

  {loading&&<p role="status">Đang tải quyền CTSV…</p>}

  {failed&&<Alert>Chưa tải được quyền CTSV. Hãy tải lại trang để thử lại.</Alert>}

  {scholarshipFailed&&scholarshipRoute&&
   <Alert>Chưa tải được quyền Học bổng. Hãy tải lại trang để thử lại.</Alert>}

  {route.kind==='home'&&
   <section>
    <h1>Trang chủ CTSV</h1>
    <p>Chào mừng bạn đến với Cổng công tác sinh viên.</p>
   </section>
  }

  {route.kind==='missing'&&
   <Alert>Trang này không có trong Cổng công tác sinh viên.</Alert>
  }

  {(path.startsWith('/shcn/')||
   functions.some(fn=>fn.active&&fn.code.startsWith('shcn.')))&&
   shcnError&&!loading&&
   <Alert>Chưa kiểm tra được quyền SHCN. Vui lòng thử lại sau.</Alert>
  }

  {route.kind==='denied'&&!loading&&!failed&&
   !(path.startsWith('/shcn/')&&shcnError)&&
   !(scholarshipRoute&&scholarshipFailed)&&
   <Alert>Bạn không có quyền truy cập trang này.</Alert>
  }

  {entry&&ActiveFeature&&
   <ActiveFeature key={entry.id+':'+entry.view} client={client} view={entry.view}/>
  }
 </AppShell>;
}

export default function App(){
 return <FeedbackProvider>
  <AuthProvider>
   <Workspace/>
  </AuthProvider>
 </FeedbackProvider>;
}
