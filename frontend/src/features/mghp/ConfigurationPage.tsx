import type {FeaturePageProps} from '../../shared/types/module';
import {ConfigurationWorkspace} from './configuration/ConfigurationWorkspace';
export function ConfigurationPage({client,view}:FeaturePageProps){return <ConfigurationWorkspace client={client} mode={view==='policies'?'policies':'rounds'}/>;}
