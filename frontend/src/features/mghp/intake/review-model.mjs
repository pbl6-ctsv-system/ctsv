export function currentCheck(paper,kind){return paper.checks.filter(c=>c.kind===kind).at(-1);}
export function pdfAccepted(paper){return currentCheck(paper,'content')?.result==='pass';}
export function approvalBlockers(papers,supplements){
 return {pendingPdfIds:papers.filter(p=>!pdfAccepted(p)).map(p=>p.id),otherCheckIds:papers.filter(p=>[...new Set(p.checks.map(c=>c.kind))].filter(kind=>!['content','hard_copy'].includes(kind)).some(kind=>['fail','needs_verification'].includes(currentCheck(p,kind)?.result))).map(p=>p.id),supplementIds:supplements.filter(s=>['open','expired'].includes(s.status)||(s.status==='responded'&&s.general===false)).map(s=>s.id)};
}
export function nextPendingPaper(papers,currentId){
 const start=papers.findIndex(p=>p.id===currentId);
 for(let offset=1;offset<papers.length;offset++){
  const paper=papers[(start+offset+papers.length)%papers.length];
  if(paper.id!==currentId&&!pdfAccepted(paper))return paper.id;
 }
 return null;
}
