import type {ComponentType} from 'react';
import type {FeaturePageProps} from '../../shared/types/module';
import {FoundationWorkspace} from '../../features/foundation';
import {AuthAdminPage} from '../../features/auth';
import {ShcnWorkspace} from '../../features/shcn';
import {ConfigurationPage} from '../../features/mghp';

// Register only implemented public feature entries. Unknown modules must never render Foundation by fallback.
export const featurePages:Record<string,ComponentType<FeaturePageProps>>={
 foundation:FoundationWorkspace,
 auth:AuthAdminPage,
 mghp:ConfigurationPage,
 shcn:ShcnWorkspace,
};
