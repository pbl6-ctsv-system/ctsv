export type SubmittedDocument={id:string;requirementId:string;fileId:string;documentNumber:string|null;issuer:string|null};
export type SubmittedVersion={id:string;revision:number;submittedAt:string;submissionType:string;documents:SubmittedDocument[]};
export type SupplementSummary={id:string;roundNumber:number;status:string;reason:string;deadline:string|null;requestedAt:string|null;respondedAt:string|null;closedAt:string|null;sourceVersionId:string;responseVersionId:string|null;general?:boolean};
export type Dossier={id:string;code:string;roundId:string;studyProcessId:string;policyId:string;policyVersionId:string;status:string|null;submittedAt:string|null;
 onlineStatus?:string;hardCopyStatus?:string;currentStage?:string;councilResult?:string|null;rejectionStage?:string|null;rejectionReason?:string|null;
 submittedVersions:SubmittedVersion[];roundName?:string;semesterCode?:string;semesterName?:string;semesterId?:string;policyCode?:string;policyName?:string;intakeSealed?:boolean;evaluationAllowed?:boolean;
 documentRequirements?:{id:string;name:string}[];supplementRequests?:SupplementSummary[];dispositionHistory?:{id:string;result:string;reason:string|null;at:string}[]};
export type Declaration={claims:Record<string,string|null>;claimDisplayNames?:Record<string,string|null>;relatedPeople:Record<string,string|null>[];
 requestedSemesters:{semesterId:string}[];statements:Record<string,boolean|null>};
