export type ScholarshipGroups={student:boolean;specialist:boolean};
export type RoundSummary={
 id:string;sourceSemesterId:string;semesterId:string;academicYear:string;
 semesterOrder:number;number:number;name:string;status:string;
};
export type ScholarshipSemester={
 id:string;academicYear:string;semesterOrder:number;startDate:string;endDate:string;status:string;
};
export type RoundDetail={
 id:string;sourceSemesterId:string;number:number;name:string;status:string;createdBy:string;
 semester:ScholarshipSemester;opensAt:string|null;closedAt:string|null;
 tuitionSnapshotAt:string|null;ruleVersionId:string|null;
};
export type RoundPage={items:RoundSummary[];page:number;size:number;totalElements:number};
export type RoundFilters={page?:number;size?:number;number?:number;semesterId?:string;sourceSemesterId?:string};
export type RoundView={round:RoundDetail;etag:string|null};
export type SourceSemester={
 sourceSemesterId:string;code:string;name:string|null;academicYear:string|null;semesterOrder:number|null;
 startDate:string|null;endDate:string|null;scholarshipSemesterId:string|null;
 selectable:boolean;unavailableReason:string|null;
};
export type SourceSemesterPage={items:SourceSemester[];page:number;size:number;totalElements:number};
export type SourceSemesterFilters={q?:string;page?:number;size?:number};
export type CreateRoundCommand={sourceSemesterId:string;number:number;name:string};
export type UpdateRoundCommand={number:number;name:string};
