import {useState} from 'react';
import {useApiTask} from './useApiTask';
import type {AuthClient,Data} from '../types/api';
export function useRecordApi(client:AuthClient){
 const[data,setData]=useState<unknown>(null),[tag,setTag]=useState('');
 const task=useApiTask();
 async function get(path:string){const response=await client.request(path);const value:Data=await response.json();setData(value);setTag(response.headers.get('ETag')??'');return value;}
 async function mutate(method:string,path:string,body?:unknown){
  const response=await client.request(path,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),'If-Match':tag},...(body?{body:JSON.stringify(body)}:{})});
  setData(response.status===204?{message:'Đã cập nhật. Đọc lại để lấy phiên bản mới.'}:await response.json());setTag(response.headers.get('ETag')??'');
 }
 return {...task,data,tag,get,mutate,clearVersion:()=>setTag('')};
}
