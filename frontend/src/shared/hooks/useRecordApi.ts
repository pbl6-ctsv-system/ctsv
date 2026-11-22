import {useRef,useState} from 'react';
import {createRequestGate} from '../api/request-gate.mjs';
import {useApiTask} from './useApiTask';
import type {AuthClient,Data} from '../types/api';
export function useRecordApi(client:AuthClient){
 const[data,setData]=useState<unknown>(null),[tag,setTag]=useState('');
 const task=useApiTask();
 const gate=useRef(createRequestGate());
 async function get(path:string){const ticket=gate.current.begin();const response=await client.request(path);const value:Data=await response.json();if(!gate.current.current(ticket))throw new DOMException('Selection changed','AbortError');setData(value);setTag(response.headers.get('ETag')??'');return value;}
 async function mutate(method:string,path:string,body?:unknown){
  const ticket=gate.current.begin();
  const response=await client.request(path,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),'If-Match':tag},...(body?{body:JSON.stringify(body)}:{})});
  const value=response.status===204?{message:'Đã cập nhật. Đọc lại để lấy phiên bản mới.'}:await response.json();
  if(!gate.current.current(ticket))throw new DOMException('Selection changed','AbortError');setData(value);setTag(response.headers.get('ETag')??'');
 }
 function clearRecord(){gate.current.invalidate();setData(null);setTag('');}
 return {...task,data,tag,get,mutate,clearRecord,clearVersion:()=>{gate.current.invalidate();setTag('');}};
}
