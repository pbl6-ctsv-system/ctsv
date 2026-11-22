import test from 'node:test';import assert from 'node:assert/strict';
import {accountFormErrors} from '../src/features/auth/pages/account-model.mjs';
test('account form rejects missing username/short password and unresolved source choices',()=>{
 const errors=accountFormErrors({username:' ',password:'short',profileLinks:[{sourceSystem:'',profileType:'student',sourceId:''}]},true);
 assert.ok(errors.username);assert.ok(errors.password);assert.ok(errors.profileLinks);
});
test('Unicode password counts code points; source links and password bytes are not rewritten',()=>{
 const value={username:'P1 thử',password:'😀'.repeat(12),profileLinks:[]};assert.deepEqual(accountFormErrors(value,true),{});assert.equal(value.password,'😀'.repeat(12));
 assert.ok(accountFormErrors({newPassword:'123456789012',reason:''},false).reason);
});
