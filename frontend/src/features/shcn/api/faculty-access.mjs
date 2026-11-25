/** Verify faculty scope only for accounts with an active faculty assignment capability. */
export async function probeFacultyScope(client,functions,signal){
 if(!functions.some(f=>f.active&&['shcn.phanCongKhoa.read','shcn.phanCongKhoa.prepare'].includes(f.code)))return {khoa:[]};
 return (await client.request('/api/shcn/khoa/pham-vi',{signal})).json();
}
