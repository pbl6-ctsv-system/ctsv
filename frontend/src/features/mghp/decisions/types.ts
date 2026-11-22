export type Page<T>={items:T[];page:number;size:number;totalElements:number;hasMore:boolean};
export type Round={id:string;name:string|null;code:string;status:string;reviewClosedAt:string|null;semesterId:string};
export type SourceContext={eligibleCount:number;excludedCount:number;blockerCount:number;blockers:{code:string}[]};
export type Period={id:string;candidateId:string;from:string;to:string;rate:string};
export type Line={id:string;studentId:string;processId:string;semesterId:string;scopeCode:string;policyVersionId:string;evaluationId:string;submissionId:string;candidateId:string;studentCode:string|null;fullName:string|null;classCode:string|null;policyName:string|null;rate:string;periods:Period[]};
export type Candidate={id:string;evaluationId:string;processId:string;semesterId:string;scopeCode:string;rate:string;eligiblePeriodId:string;from:string;to:string;selection:string;reasonCode:string;policyVersionId:string;policyName:string|null;tiePriority:number|null};
export type Minutes={id:string;code:string;reference:string|null;meetingAt:string|null;place:string|null;datasetSha256:string;etag:string};
export type DocumentLink={id:string;documentType:string;documentVersionId:string;status:string|null};
export type Header={id:string;decisionId:string;revision:number;status:string;stage:string;previousVersionId:string|null;code:string;number:string|null;date:string|null;issuingUnitId:string|null;datasetSha256:string;sourceSha256:string;reviewClosedAt:string|null;createdAt:string;createdBy:string;lockedAt:string|null;lockedBy:string|null;reason:string|null;lineCount:number;roundId?:string};
export type Version=Header&{candidates:Page<Candidate>;lines:Page<Line>;minutes:Minutes|null;documents:DocumentLink[]};
export type History=Header&{minutes:Minutes|null;documents:DocumentLink[]};
export type Root={id:string;roundId:string;head:Header};
export type MeetingForm={minutesCode:string;reference:string;meetingAt:string;place:string;reason:string;number:string;date:string};

