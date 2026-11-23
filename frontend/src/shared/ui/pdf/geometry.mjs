export function geometryFromPixels(viewport,page,rect){
 if(!Number.isInteger(page)||page<1||![rect.x,rect.y,rect.width,rect.height].every(Number.isFinite))throw new Error('Vùng ký không hợp lệ. Chọn lại trên trang PDF.');
 if(rect.width<8||rect.height<8)throw new Error('Vùng ký quá nhỏ. Kéo một khung lớn hơn.');
 const points=corners(rect).map(([x,y])=>viewport.convertToPdfPoint(x,y));
 return {page,...bounds(points)};
}
export function pixelsFromGeometry(viewport,geometry){return bounds(corners(geometry).map(([x,y])=>viewport.convertToViewportPoint(x,y)));}
export function keyboardSelectionRect(viewport){
 const width=Math.min(180,viewport.width*.4),height=Math.min(50,viewport.height*.2);
 if(width<8||height<8)throw new Error('Phóng to PDF để chọn vùng ký hợp lệ.');
 return {x:viewport.width*.45,y:viewport.height*.75,width,height};
}
function corners({x,y,width,height}){return [[x,y],[x+width,y],[x,y+height],[x+width,y+height]];}
function bounds(points){
 const xs=points.map(v=>v[0]),ys=points.map(v=>v[1]),x=Math.min(...xs),y=Math.min(...ys);
 return {x,y,width:Math.max(...xs)-x,height:Math.max(...ys)-y};
}
