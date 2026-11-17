import {openNav} from './navigation.mjs';
// Focused DEV regression. Run through Playwright MCP against the real UI/backend.
// Credentials are supplied by the caller and are never included in the result.
export async function checkPolicyConfirmation(page, {username, password, runId}) {
  const assert = {
    equal(actual, expected, message='Unexpected result') { if(actual !== expected) throw new Error(`${message}: ${actual} !== ${expected}`); },
    ok(condition, message='Expected truthy result') { if(!condition) throw new Error(message); }
  };
  await page.goto('https://127.0.0.1:5173');
  if (await page.getByRole('button', {name:'Đăng xuất', exact:true}).count()) {
    await page.getByRole('button', {name:'Đăng xuất', exact:true}).click();
  }
  await page.getByRole('textbox', {name:'Tên đăng nhập', exact:true}).fill(username);
  await page.getByRole('textbox', {name:'Mật khẩu', exact:true}).fill(password);
  await page.getByRole('button', {name:'Đăng nhập', exact:true}).click();
  await openNav(page,'Chính sách MGHP');
  const m9 = page.getByRole('row').filter({has:page.getByRole('cell', {name:'M9', exact:true})});
  await m9.getByRole('button', {name:'Xem phiên bản', exact:true}).click();
  // Read the existing synthetic template; never update that locked version.
  await page.getByRole('button', {name:'Xem bản 1', exact:true}).click();
  await page.getByRole('button', {name:'Tạo phiên bản chính sách', exact:true}).click();
  const name = page.getByRole('textbox', {name:'Tên', exact:true});
  await name.fill(runId+' SYNTHETIC mới');
  await page.getByRole('textbox', {name:'Mã phiên bản quy tắc', exact:true}).fill(runId);
  await page.getByRole('textbox', {name:'Căn cứ pháp lý', exact:true}).fill('SYNTHETIC DEV ONLY: hồi quy UI, không phải căn cứ pháp lý hoặc hồ sơ thật.');
  const post = page.waitForResponse(r=>r.request().method()==='POST' && r.url().endsWith('/phien-ban'));
  await page.getByRole('button', {name:'Lưu cấu hình', exact:true}).click();
  const createdResponse = await post;
  assert.equal(createdResponse.status(), 201);
  const created = await createdResponse.json();
  await page.getByText('Đã lưu. Dữ liệu được đọc lại từ hệ thống.', {exact:true}).waitFor();
  assert.equal(await name.inputValue(), runId+' SYNTHETIC mới');
  await name.fill(runId+' SYNTHETIC đã sửa');
  const put = page.waitForResponse(r=>r.request().method()==='PUT' && r.url().endsWith('/'+created.id));
  await page.getByRole('button', {name:'Lưu cấu hình', exact:true}).click();
  const updated = await put;
  assert.equal(updated.status(), 200);
  assert.equal(await updated.headerValue('etag'), null, 'Transformed PUT must not publish a validator');
  await page.getByText('Đã lưu. Dữ liệu được đọc lại từ hệ thống.', {exact:true}).waitFor();
  assert.equal(await name.inputValue(), runId+' SYNTHETIC đã sửa');
  await page.getByLabel('Căn cứ xác nhận', {exact:true}).fill('SYNTHETIC DEV: chỉ kiểm cập nhật danh sách/chi tiết, M9 theo quy ước đồ án.');
  const confirmation = page.waitForResponse(r=>r.url().endsWith('/'+created.id+'/xac-nhan'));
  await page.getByRole('button', {name:'Xác nhận chính sách', exact:true}).click();
  const confirmed = await confirmation;
  assert.equal(confirmed.status(), 200);
  const final = await confirmed.json();
  assert.equal(final.status, 'locked');
  assert.ok(await confirmed.request().headerValue('if-match'));
  assert.ok(await confirmed.headerValue('etag'));
  await page.getByText('Đã cập nhật trạng thái.', {exact:true}).waitFor();
  assert.equal(await name.isDisabled(), true);
  assert.equal(await page.getByRole('button', {name:'Lưu cấu hình', exact:true}).count(), 0);
  const versions = page.getByRole('heading', {name:'Các phiên bản', exact:true}).locator('..');
  const expected = `Phiên bản ${final.version} · Đã xác nhận · ${final.rate}`;
  const actual = await versions.innerText();
  assert.ok(actual.includes(expected), `List still stale after success (versionId=${final.id}): ${actual}`);
  return {result:'PASS', runId, id:final.id, version:final.version, create:201, edit:200, confirm:200,
    expectedLabel:expected, detailReadOnly:true, manualListReload:false, etagPreserved:true};
}
