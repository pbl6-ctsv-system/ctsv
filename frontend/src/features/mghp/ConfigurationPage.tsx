import {lazy,Suspense} from 'react';
import type {FeaturePageProps} from '../../shared/types/module';
import {ConfigurationWorkspace} from './configuration/ConfigurationWorkspace';
import {IntakeWorkspace} from './intake/IntakeWorkspace';
import {ReviewWorkspace} from './review/ReviewWorkspace';
const DecisionWorkspace=lazy(()=>import('./decisions/DecisionWorkspace').then(m=>({default:m.DecisionWorkspace})));
export function ConfigurationPage({client,view}:FeaturePageProps) {
 if(view==='decisions')return <Suspense fallback={<p role="status">Đang mở Hội đồng và quyết định…</p>}><DecisionWorkspace client={client}/></Suspense>;
 if(['evaluations','benefits','own-benefits'].includes(view))return <ReviewWorkspace client={client} view={view}/>;
 if(view==='applications'||view==='staff-applications'||view==='inbox') return <IntakeWorkspace client={client} mode={view}/>;
 return <ConfigurationWorkspace client={client} mode={view==='policies'?'policies':'rounds'} manager={view!=='public-rounds'}/>;
}
