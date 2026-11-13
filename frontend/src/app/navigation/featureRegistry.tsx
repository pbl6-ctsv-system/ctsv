import type {ComponentType} from 'react';
import type {FeaturePageProps} from '../../shared/types/module';
import {FoundationWorkspace} from '../../features/foundation';
import {AuthAdminPage} from '../../features/auth';

// Register only implemented public feature entries. Unknown modules must never render Foundation by fallback.
export const featurePages:Record<string,ComponentType<FeaturePageProps>>={
 foundation:FoundationWorkspace,
 auth:AuthAdminPage,
};
