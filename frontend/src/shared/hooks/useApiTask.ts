import {useState} from 'react';
import {errorMessage} from '../api/error-message';
export function useApiTask(){
 const[busy,setBusy]=useState(false),[error,setError]=useState('');
 async function run(action:()=>Promise<void>){setBusy(true);setError('');try{await action();}catch(e){if(!(e instanceof DOMException&&e.name==='AbortError'))setError(errorMessage(e));}finally{setBusy(false);}}
 return {busy,error,run};
}
