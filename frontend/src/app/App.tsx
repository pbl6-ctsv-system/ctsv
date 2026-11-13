import {useState} from 'react';
import {AuthProvider,useAuth} from './providers/AuthProvider';
import {AppShell} from './layouts/AppShell';
import {navigationItems} from './navigation/modules.mjs';
import {LoginPage} from '../features/auth';
import {featurePages} from './navigation/featureRegistry';
import {Alert} from '../shared/ui/Alert';

function Workspace(){
 const auth=useAuth(),[selected,setSelected]=useState('lookup');
 const entry=navigationItems.find(item=>item.id===selected)??navigationItems[0];
 const ActiveFeature=featurePages[entry.feature];
 return <AppShell username={auth.me?String(auth.me.username):undefined} busy={auth.busy} onLogout={auth.logout}>
  {auth.error&&<Alert>{auth.error}</Alert>}
  {!auth.me?<LoginPage onLogin={auth.login} onRestore={auth.restore} busy={auth.busy}/>:<>
   <nav aria-label="Chức năng CTSV">{navigationItems.map(item=><button key={item.id} className={entry.id===item.id?'selected':''} aria-current={entry.id===item.id?'page':undefined} onClick={()=>setSelected(item.id)}>{item.label}</button>)}</nav>
   {ActiveFeature?<ActiveFeature key={entry.id+':'+String(auth.me.accountId)} client={auth.client} view={entry.view}/>:<Alert>Phân hệ chưa được đăng ký.</Alert>}
  </>}
 </AppShell>;
}
export default function App(){return <AuthProvider><Workspace/></AuthProvider>;}
