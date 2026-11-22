// Each record read belongs to one selection. Invalidating also rejects late replies.
export function createRequestGate(){
 let revision=0;
 return {begin:()=>++revision,invalidate:()=>{revision++;},current:value=>value===revision};
}
