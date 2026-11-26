export type FileView={id:string;fileName:string;size:string;sha256:string;state:string};
export type DocumentView={id:string;documentId:string;documentType?:string;isCurrent:boolean;canPrepare:boolean;title:string;version:number;file:FileView;createdAt:string;datasetSha256:string};
export type {PdfGeometry as Geometry} from '../../../shared/ui/pdf/types';
export type Step={id:string;order:number;role:string;status:string;signerName:string|null;signerEmail:string;outputFile:FileView|null;signedAt:string|null};
export type SigningRow={id:string;documentVersionId:string;title:string;version:number;documentType:string;status:string;mode:string;roundId:string;roundName:string;semesterName:string;currentStep:number|null;waitingForRole:string|null;allowedActions:string[];inputFile:FileView};
export type SigningRequest={id:string;code:string;documentVersionId:string;status:string;phase:string;mode:string;startedAt:string|null;completedAt:string|null;supportMessage:string|null;document:DocumentView;inputFile:FileView;currentStep:number|null;steps:Step[];allowedActions:string[];history:{id:string;action:string;actorId:string;reason:string|null;at:string}[];relatedMinutes?:{documentVersionId:string;requestId:string}|null};
export type Page<T>={items:T[];page:number;size:number;totalElements:number;hasMore:boolean};
