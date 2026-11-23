import type {AuthClient} from '../../types/api';

export type PdfGeometry={page:number;x:number;y:number;width:number;height:number};
export type PdfViewerProps={
 client:AuthClient;
 fileId:string;
 title:string;
 fileName?:string;
 selectable?:boolean;
 selection?:PdfGeometry|null;
 onSelectionChange?:(value:PdfGeometry|null)=>void;
};
