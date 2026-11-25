import test from 'node:test';
import assert from 'node:assert/strict';
import {validateRound,audienceDateRange,roundEditorInput,semesterLabel} from '../src/features/shcn/model/round.mjs';
const group=(code,from,until)=>({loaiDoiTuong:'KHOA_HOC',maDoiTuong:code,hinhThucChoPhep:'OFFLINE',khungNgayBatDau:from,khungNgayKetThuc:until,khungTietChoPhep:null});
const input=(groups)=>({maHocKy:'2610',tenDot:'Đợt theo khóa',khungNgayBatDau:'2099-01-01',khungNgayKetThuc:'2099-01-02',khungTietChoPhep:[{tietDau:11,tietCuoi:12}],hanChotDangKyLich:'2026-11-29T10:00:00Z',doiTuongApDung:groups});
test('round dates derive from earliest and latest selected cohort dates, ignoring stale root values',()=>{
 const d=input([group('2023','2026-12-20','2026-12-25'),group('2025','2027-03-02','2027-03-04'),group('2021','2026-12-01','2026-12-07')]);const out=validateRound(d);
 assert.equal(out.khungNgayBatDau,'2026-12-01');assert.equal(out.khungNgayKetThuc,'2027-03-04');assert.equal(out.maHocKy,'2610');assert.equal(d.khungNgayBatDau,'2099-01-01');
});
test('at least one selected audience is required to save a draft',()=>{
 assert.throws(()=>validateRound(input([])),/ít nhất một/);const d=input([]);delete d.doiTuongApDung;assert.throws(()=>validateRound(d),/ít nhất một/);
});
test('every selected audience needs two real ordered dates',()=>{
 for(const dates of [[null,null],['2026-12-01',null],['2026-12-07','2026-12-01'],['2026-02-30','2026-03-01'],['0000-01-01','2026-01-01']])assert.throws(()=>validateRound(input([group('2023',...dates)])));
});
test('date range is recomputed after deselecting an audience; incomplete groups produce no hidden range',()=>{
 const a=group('2023','2026-12-01','2026-12-07'),b=group('2025','2027-03-02','2027-03-04');
 assert.deepEqual(audienceDateRange([a,b]),{khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2027-03-04'});
 assert.deepEqual(audienceDateRange([a]),{khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07'});
 for(const values of [[],[a,group('2025',null,null)]])assert.deepEqual(audienceDateRange(values),{khungNgayBatDau:'',khungNgayKetThuc:''});
});
test('editing old inherited dates materializes original root window without changing source snapshot',()=>{
 const old={...input([group('2023',null,null)]),khungNgayBatDau:'2026-12-01',khungNgayKetThuc:'2026-12-07'};const d=roundEditorInput(old);
 assert.equal(d.doiTuongApDung[0].khungNgayBatDau,'2026-12-01');assert.equal(validateRound(d).khungNgayKetThuc,'2026-12-07');assert.equal(old.doiTuongApDung[0].khungNgayBatDau,null);
});
test('semester labels expose names only while missing names never expose business codes',()=>{
 assert.equal(semesterLabel({ma:'2610',ten:'Học kỳ 1 năm học 2026-2027'}),'Học kỳ 1 năm học 2026-2027');
 for(const ten of [null,'','   '])assert.equal(semesterLabel({ma:'2610',ten}),'Chưa có tên học kỳ');
});
