export function pdfDocumentOptions(data,resourceBase){
 return {
  data,
  wasmUrl:new URL('wasm/',resourceBase).href,
  standardFontDataUrl:new URL('standard_fonts/',resourceBase).href,
  cMapUrl:new URL('cmaps/',resourceBase).href,
  cMapPacked:true,
  iccUrl:new URL('iccs/',resourceBase).href,
 };
}
