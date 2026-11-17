import type {FeaturePageProps} from '../../shared/types/module';
import {ConfigurationWorkspace} from './configuration/ConfigurationWorkspace';
import {IntakeWorkspace} from './intake/IntakeWorkspace';
export function ConfigurationPage({client,view}:FeaturePageProps) {
 if(view==='applications'||view==='staff-applications'||view==='inbox') return <IntakeWorkspace client={client} mode={view}/>;
 return <ConfigurationWorkspace client={client} mode={view==='policies'?'policies':'rounds'} manager={view!=='public-rounds'}/>;
}
