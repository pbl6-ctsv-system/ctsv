import test from 'node:test';
import assert from 'node:assert/strict';
import {roundInput,validateRound,sameRound,copyAudienceConfiguration} from '../src/features/shcn/model/round.mjs';
const group=()=>({loaiDoiTuong:'KHOA_HOC',maDoiTuong:'2023',hinhThucChoPhep:'OFFLINE',khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07',khungTietChoPhep:null});
const input=()=>({maHocKy:'0010',tenDot:'Đợt thực hành',khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07',khungTietChoPhep:[{tietDau:11,tietCuoi:12}],hanChotDangKyLich:'2026-11-29T10:00:00Z',doiTuongApDung:[group()]});
test('round payload keeps selected groups and owns nested copies',()=>{const d=input(),out=roundInput(d);assert.deepEqual(out.doiTuongApDung,[group()]);out.doiTuongApDung[0].hinhThucChoPhep='ONLINE';assert.equal(d.doiTuongApDung[0].hinhThucChoPhep,'OFFLINE');});
test('group changes make draft dirty',()=>{const a=input(),b=input();b.doiTuongApDung[0].hinhThucChoPhep='ONLINE';assert.equal(sameRound(a,b),false);});
test('group dates and mode are validated before saving',()=>{for(const change of [{hinhThucChoPhep:'X'},{khungNgayKetThuc:'2026-11-01'},{khungNgayKetThuc:null}]){const d=input();Object.assign(d.doiTuongApDung[0],change);assert.throws(()=>validateRound(d));}});
test('duplicate audience is rejected rather than duplicated on save',()=>{const d=input();d.doiTuongApDung.push(group());assert.throws(()=>validateRound(d));});
test('empty group state stays explicit while saving requires at least one selected group',()=>{const d=input();d.doiTuongApDung=[];assert.deepEqual(roundInput(d).doiTuongApDung,[]);assert.throws(()=>validateRound(d),/ít nhất một/);});


test('only Offline and Online are accepted when writing audience configuration',()=>{
 for(const mode of ['OFFLINE','ONLINE']){const d=input();d.doiTuongApDung[0].hinhThucChoPhep=mode;assert.equal(validateRound(d).doiTuongApDung[0].hinhThucChoPhep,mode);}
 const d=input();d.doiTuongApDung[0].hinhThucChoPhep='CA_HAI';assert.throws(()=>validateRound(d),/hình thức/);
});
test('copy dates and mode to selected cohort and every selected delayed receiver without mutating inputs',()=>{
 const source=group(),target={...group(),maDoiTuong:'2024',hinhThucChoPhep:'ONLINE',khungTietChoPhep:[{tietDau:5,tietCuoi:6}]};
 const groups=[source,target,...['R1','R2'].map(maDoiTuong=>({...group(),loaiDoiTuong:'LOP',maDoiTuong,khungNgayBatDau:null,khungNgayKetThuc:null,hinhThucChoPhep:''})),{...group(),maDoiTuong:'2025',hinhThucChoPhep:'ONLINE'}];
 const before=structuredClone(groups),out=copyAudienceConfiguration(groups,'KHOA_HOC:2023',['KHOA_HOC:2024','LOP:R1','LOP:R2']);
 assert.deepEqual(groups,before);assert.deepEqual(out[0],source);assert.equal(out.length,5);assert.deepEqual(out[4],groups[4]);
 for(const n of [1,2,3]){assert.equal(out[n].hinhThucChoPhep,'OFFLINE');assert.equal(out[n].khungNgayBatDau,source.khungNgayBatDau);assert.equal(out[n].khungNgayKetThuc,source.khungNgayKetThuc);}
 assert.deepEqual(out[1].khungTietChoPhep,target.khungTietChoPhep);
 out[1].khungNgayBatDau='2026-12-03';assert.equal(out[0].khungNgayBatDau,'2026-12-01');assert.equal(out[2].khungNgayBatDau,'2026-12-01');
});
test('copy rejects incomplete or invalid source and absent targets rather than selecting groups implicitly',()=>{
 const target={...group(),maDoiTuong:'2024'};
 for(const patch of [{khungNgayBatDau:null},{khungNgayKetThuc:'2026-11-30'},{hinhThucChoPhep:'CA_HAI'}]){assert.throws(()=>copyAudienceConfiguration([{...group(),...patch},target],'KHOA_HOC:2023',['KHOA_HOC:2024']));}
 assert.throws(()=>copyAudienceConfiguration([group(),target],'KHOA_HOC:2023',['KHOA_HOC:2025']));
 assert.throws(()=>copyAudienceConfiguration([group(),target],'KHOA_HOC:2023',[]));
 assert.throws(()=>copyAudienceConfiguration([group(),target],'KHOA_HOC:2023',['KHOA_HOC:2023']));
});
