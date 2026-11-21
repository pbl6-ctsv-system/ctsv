import type {FeaturePageProps} from '../../shared/types/module';
import {FacultyAssignmentsPage} from './pages/FacultyAssignmentsPage';
import {RoundsPage} from './pages/RoundsPage';
export function ShcnWorkspace(props:FeaturePageProps){return props.view==='rounds'?<RoundsPage {...props}/>:<FacultyAssignmentsPage {...props}/>;}
