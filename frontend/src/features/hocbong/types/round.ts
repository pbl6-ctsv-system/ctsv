export type ScholarshipGroups={student:boolean;specialist:boolean};
export type RoundSummary={
 id:string;sourceSemesterId:string;semesterId:string;academicYear:string;
 semesterOrder:number;number:number;name:string;status:string;
};
export type ScholarshipSemester={
 id:string;academicYear:string;semesterOrder:number;startDate:string;endDate:string|null;status:string;
};
export type RoundDetail={
 id:string;sourceSemesterId:string;number:number;name:string;status:string;createdBy:string;
 semester:ScholarshipSemester;opensAt:string|null;closedAt:string|null;
 tuitionSnapshotAt:string|null;ruleVersionId:string|null;
};
export type RoundPage={items:RoundSummary[];page:number;size:number;totalElements:number};
export type RoundFilters={page?:number;size?:number;number?:number;semesterId?:string;sourceSemesterId?:string};
export type RoundView={round:RoundDetail;etag:string|null};
export type ScopePair={cohortId:string;majorId:string};
export type ScopeSelection={mode:'all'}|{mode:'groups';groups:ScopePair[]};
export type ScopeGroup=ScopePair&{
 cohortCode:string|null;majorCode:string|null;majorName:string|null;studentCount:number;
};
export type ScopeVersion={
 id:string;revision:number;mode:'all'|'groups';catalogRevision:string;groups:ScopeGroup[];
 createdAt:string;createdBy:string;
};
export type ScopeView={roundId:string;roundEtag:string;scope:ScopeVersion|null};
export type ScopeCatalogPage={catalogRevision:string;items:ScopeGroup[];page:number;size:number;totalElements:number};
export type ScopeCatalogFilters={sourceSemesterId:string;cohortId?:string;majorId?:string;q?:string;page?:number;size?:number};
export type ScopeCatalogSnapshot={catalogRevision:string;groups:ScopeGroup[]};
export type RoundContext=RoundView&{scope:ScopeVersion|null};
export type CreationLookup={requestId:string;roundId:string};
export type CreatedRound=RoundView&{replayed:boolean};
export type SourceSemester={
 sourceSemesterId:string;code:string;name:string|null;academicYear:string|null;semesterOrder:number|null;
 startDate:string|null;endDate:string|null;scholarshipSemesterId:string|null;
 selectable:boolean;unavailableReason:string|null;
};
export type SourceSemesterPage={items:SourceSemester[];page:number;size:number;totalElements:number};
export type DefaultSemester={semester:SourceSemester|null;reason:'NO_ELIGIBLE_SEMESTER'|null};
export type SourceSemesterFilters={q?:string;page?:number;size?:number};
export type CreateRoundCommand={sourceSemesterId:string;catalogRevision:string;scope:ScopeSelection};
export type UpdateRoundCommand={catalogRevision:string;scope:ScopeSelection};
