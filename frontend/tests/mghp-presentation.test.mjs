import test from 'node:test';
import assert from 'node:assert/strict';
import {displayName,applicationTitle} from '../src/features/mghp/presentation.mjs';

test('real names and custom application codes are preserved',()=>{
 assert.equal(displayName('Đợt xét tháng 11','HK1-2026'),'Đợt xét tháng 11');
 assert.equal(applicationTitle('21','HS-2026-021'),'HS-2026-021');
});
test('known generated DEV labels and UUID codes get readable display names',()=>{
 assert.equal(displayName('P07-DEV-123 SYNTHETIC','P07-DEV-123'),'Đợt thực hành bổ sung hồ sơ');
 assert.equal(applicationTitle('21','MGHP-7a7f5f2c-de5c-4d71-9b23-52c3534cf304'),'Hồ sơ MGHP #21');
});
