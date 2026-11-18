import {createContext,useCallback,useContext,useId,useMemo,useEffect,useState,type ReactNode} from 'react';
import {createPortal} from 'react-dom';
import styles from './Alert.module.css';

type Notice={id:string;content:ReactNode};
type FeedbackActions={show:(id:string,content:ReactNode)=>void;remove:(id:string)=>void};
const FeedbackContext=createContext<FeedbackActions|null>(null);

export function FeedbackProvider({children}:{children:ReactNode}) {
 const [notices,setNotices]=useState<Notice[]>([]);
 const show=useCallback((id:string,content:ReactNode)=>{
  setNotices(current=>[...current.filter(n=>n.id!==id),{id,content}]);
 },[]);
 const remove=useCallback((id:string)=>setNotices(current=>current.filter(n=>n.id!==id)),[]);
 const actions=useMemo(()=>({show,remove}),[show,remove]);
 return <FeedbackContext.Provider value={actions}>
  {children}
  {createPortal(<div className={styles.viewport} aria-label="Thông báo thao tác">
   {notices.map(notice=><div key={notice.id} className={styles.error} role="alert">
    <div><strong>Chưa thể hoàn tất thao tác</strong><p>{notice.content}</p></div>
    <button type="button" className={styles.close} onClick={()=>remove(notice.id)} aria-label="Đóng thông báo lỗi">×</button>
   </div>)}
  </div>,document.body)}
 </FeedbackContext.Provider>;
}

export function ErrorFeedback({children}:{children:ReactNode}) {
 const feedback=useContext(FeedbackContext);
 const id=useId();
 useEffect(()=>{
  feedback?.show(id,children);
  return ()=>feedback?.remove(id);
 },[feedback,id,children]);
 return feedback?null:<div role="alert" className={styles.error}>{children}</div>;
}
