import test from 'node:test';
import assert from 'node:assert/strict';
import {saveBody,matchingContent,vietnamInput,inheritedStatuses} from '../src/features/shcn/model/draft.mjs';
import {visibleNavigation,resolveRoute} from '../src/app/navigation/modules.mjs';
const draft={version:'AAAAAAAAAAE=',hieuLucTu:'2026-08-01T00:00:00+07:00',hieuLucDen:null,lyDoDieuChinh:null,cacPhanCong:[{id:'9007199254740999',maLop:'001',maGiaoVien:'GV1',ghiChu:null,lopHienTai:{}},{id:'2',maLop:'002',maGiaoVien:'GV1',ghiChu:'giữ'}]};
test('SHCN saves entire working copy, preserves codes and strips read-only projections',()=>{const visible=draft.cacPhanCong.filter(r=>r.maLop==='001');assert.equal(visible.length,1);const body=saveBody(draft);assert.equal(body.cacPhanCong.length,2);assert.deepEqual(body.cacPhanCong[0],{maLop:'001',maGiaoVien:'GV1',ghiChu:null});assert.equal(body.expectedVersion,draft.version);assert.equal(body.hieuLucTu,draft.hieuLucTu);});
test('SHCN rejects duplicate classes but permits one lecturer for multiple classes and empty draft',()=>{assert.equal(saveBody(draft).cacPhanCong.length,2);assert.throws(()=>saveBody({...draft,cacPhanCong:[draft.cacPhanCong[0],draft.cacPhanCong[0]]}));assert.deepEqual(saveBody({...draft,cacPhanCong:[]}).cacPhanCong,[]);});
test('SHCN reconciles semantic instants and row order without relying on generated IDs',()=>{assert(matchingContent(draft,{...draft,hieuLucTu:'2026-07-31T17:00:00Z',cacPhanCong:[...draft.cacPhanCong].reverse()}));assert.equal(vietnamInput('2026-07-31T17:00:00Z'),'2026-08-01T00:00:00');assert(!inheritedStatuses.has('NHAP'));});
test('SHCN menu and direct route share verified scoped access; role/function name alone is insufficient',()=>{const deny={student:false,mghpSpecialist:false,ctsvRoleAdmin:false};assert.equal(resolveRoute('/shcn/phan-cong-gvcn',[{code:'shcn.phanCongKhoa.prepare',active:true}],deny).kind,'denied');const allow={...deny,shcnFacultyRead:true};assert(visibleNavigation([],allow).some(v=>v.id==='shcn-assignments'));assert.equal(resolveRoute('/shcn/phan-cong-gvcn',[],allow).kind,'page');});

test('Previous-year inheritance defaults to prior academic year and rejects same/future year',async()=>{
 const {previousSchoolYear,inheritanceYearError}=await import('../src/features/shcn/model/draft.mjs');
 assert.equal(previousSchoolYear('2026-2027'),'2025-2026');
 assert.equal(previousSchoolYear('2026'),'2025');
 assert.equal(previousSchoolYear('DEV-SHCN-2026'),'');
 assert(inheritanceYearError('2026-2027','2026-2027'));
 assert(inheritanceYearError('2026-2027','2027-2028'));
 assert(inheritanceYearError('2026-2027',''));
 assert.equal(inheritanceYearError('2026-2027','2025-2026'),'');
 assert.equal(inheritanceYearError('DEV-SHCN-2026','DEV-SHCN-2025'),'');
});

test('Year dropdown ranks exact and partial matches first and keeps free labels',async()=>{
 const {rankYearOptions}=await import('../src/features/shcn/model/draft.mjs');
 const options=rankYearOptions('2025-2026',['2025-2026','DEV-SHCN-2026','2025-2026'],2026);
 assert.equal(options[0],'2025-2026');assert.equal(options.filter(v=>v==='2025-2026').length,1);
 assert.equal(rankYearOptions('2025-',[],2026)[0],'2025-2026');
 assert(rankYearOptions('DEV',['DEV-SHCN-2026'],2026)[0]==='DEV-SHCN-2026');
 assert(!rankYearOptions('Nhãn mới',[],2026).includes('Nhãn mới'));
});
