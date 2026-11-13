import test from 'node:test';
import assert from 'node:assert/strict';
import {navigationItems} from '../src/app/navigation/modules.mjs';
import {createAuthClient as movedClient} from '../src/shared/auth/auth-client.mjs';
import {createAuthClient as compatibleClient} from '../src/shared/auth-client.mjs';

test('navigation identifies implemented feature entries without publishing empty modules',()=>{
 assert.equal(new Set(navigationItems.map(item=>item.id)).size,navigationItems.length);
 assert.deepEqual(navigationItems.map(item=>[item.id,item.feature]),[
  ['lookup','foundation'],['students','foundation'],['manage','foundation'],['auth','auth']]);
 assert(navigationItems.every(item=>typeof item.label==='string'&&item.label.length>0));
});
test('legacy auth import and modular auth import share the same implementation',()=>{
 assert.equal(movedClient,compatibleClient);
});
