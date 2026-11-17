import {navigationSections} from '../src/app/navigation/modules.mjs';

const sections=navigationSections(
 [{code:'auth.accounts.manage',active:true},{code:'auth.rbac.read',active:true}],
 {student:true,mghpSpecialist:true,ctsvRoleAdmin:true},
);
const navbar=page=>page.getByRole('navigation',{name:'Chức năng CTSV'});

export async function revealNav(page,label) {
 const section=sections.find(item=>item.kind==='dropdown'&&item.groups.some(group=>group.items.some(entry=>entry.label===label)));
 if(section){
  const trigger=navbar(page).getByRole('button',{name:section.label,exact:true});
  await trigger.waitFor();
  if(await trigger.getAttribute('aria-expanded')!=='true')await trigger.click();
 }
 return navbar(page).getByRole('link',{name:label,exact:true});
}

export async function openNav(page,label) {
 await (await revealNav(page,label)).click();
}
