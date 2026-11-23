// Dev-only component fixture, excluded from the production entry/build. No business command.
import {StrictMode,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {PdfViewer} from '../src/shared/ui/pdf';
import type {PdfGeometry,PdfViewerProps} from '../src/shared/ui/pdf';

export function mountPdfHarness(client:PdfViewerProps['client'],initial:Omit<PdfViewerProps,'client'>){
 const root=createRoot(document.getElementById('pdf-test')!);
 let update:(props:Omit<PdfViewerProps,'client'>)=>void;
 let selected:PdfGeometry|null=null;
 function Fixture(){
  const [props,setProps]=useState(initial),[selection,setSelection]=useState<PdfGeometry|null>(null);
  update=setProps;
  return <PdfViewer {...props} client={client} selection={selection} onSelectionChange={value=>{selected=value;setSelection(value);}}/>;
 }
 root.render(<StrictMode><Fixture/></StrictMode>);
 return {show:(props:Omit<PdfViewerProps,'client'>)=>update(props),selection:()=>selected,destroy:()=>root.unmount()};
}
