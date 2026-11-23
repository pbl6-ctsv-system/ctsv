import {lazy,Suspense} from 'react';
import type {PdfViewerProps} from './types';

const PdfDocumentView=lazy(()=>import('./PdfDocumentView'));
export function PdfViewer(props:PdfViewerProps){
 return <Suspense fallback={<p role="status">Đang mở trình xem PDF…</p>}><PdfDocumentView {...props}/></Suspense>;
}
