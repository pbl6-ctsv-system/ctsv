# Đồng bộ origin/main — 22/11/2026

**Trạng thái review: OWNER AUTHORIZED COMMIT/PUSH.** Sau khi xem nguyên nhân và bằng chứng, owner ra lệnh “bỏ qua lỗi này và push code lên”: cho giữ nguyên4 test navigation đang FAIL và lỗi `CtsvApplicationTests.contextLoads` đã được miễn riêng trước đó. Không sửa code/expected, không đánh các lỗi này PASS. Lượt dừng trước đã được owner cho tiếp tục; commit/push thường lên nhánh riêng, không tạo PR hoặc push main.

## Git và bảo toàn source

- Nhánh `codex/p01-slice-a`, remote `https://github.com/pbl6-ctsv-system/ctsv.git`.
- HEAD trước lượt này `9a042f7`;158 file source/frontend/tests/docs chưa staged. Không có staged sẵn.
- Đã review/stage và commit bảo toàn **9f21709** trước merge. Không secrets/runtime/backups trong tập commit dự kiến. Hai cảnh báo scan là chuỗi header PEM trong code test (không key bytes) và fixture secret giả trong kiểm thử dev tools.
- `git fetch origin`: main chuyển từ `2f7decb` đến **4db9a13**. Merge `--no-commit --no-ff` vào nhánh riêng; không push main/force/reset/discard.
- Conflict duy nhất ở `frontend/src/app/navigation/modules.mjs`: giữ capability/menu SHCN/Học bổng từ main, giữ nhãn/context sinh viên của nhánh hiện hành. FoundationRepository tự merge, không bỏ read projections hoặc semester source của bên nào. Review không còn unmerged path; owner đã cho hoàn tất merge commit sau khi quét staged.

## Migration

| Version sau hợp nhất | Nội dung | Xử lý |
|---|---|---|
| V1–V16 | Migration đã có chung | Giữ nguyên |
| V17 | DRL schema từ main | Giữ nguyên |
| V18 | SHCN draft round từ main | Giữ nguyên |
| V19 | Học bổng round scope từ main | Giữ nguyên |
| V20 | Timeline riêng trước đây là V17 | Chỉ đổi filename, SQL không đổi |
| V21 | Retire round guidance riêng trước đây là V18 | Chỉ đổi filename, SQL không đổi |

So sánh Git blob:19 migration nguồn main không đổi;2 file đổi số giữ nguyên nội dung;0 duplicate version. Không tạo migration nghiệp vụ mới hoặc sửa SQL lịch sử của main.

**ctsv_dev vẫn ở chuỗi V18 riêng cũ** và chưa bị ghi/repair/reset. Không chạy `dev.ps1 migrate` hoặc khởi động JAR merged với DB đó: V17/V18 trong history DEV không phải V17/V18 của main. Đổi tên source không chuyển đổi history DB đã áp dụng. Launcher đang chạy giữ JAR cũ; frontend DEV có thể HMR theo source mới, nên không coi đây là bản DEV đã kiểm tương thích merged. Chưa smoke lại DEV hoặc thay cấu hình DB launcher.

Việc chuyển DEV sang chuỗi chung cần lượt riêng có phương án bảo toàn dữ liệu và phê duyệt rõ. Phương án đơn giản: giữ DB cũ, tạo DEV local mới từ chuỗi chung và nhập lại dữ liệu synthetic cần thiết qua setup/API được cho phép. Không drop/reset DB cũ, không repair checksum, không sửa version history tự động để qua lỗi.

## SQL REAL trên đích riêng được owner cấp phép

Owner cho phép tạo `ctsv_merge_test_20261122` trên `localhost\DEV2022`; Windows Authentication; owner hiện hành; marker `timeline_test_owner=ctsv-merge-test-20261122`; compatibility160; Vietnamese_100_CI_AS_SC. Không dùng DEV hoặc evidence cũ.

Lệnh từ root (PowerShell, sau khi thiết lập P00_SQL_PORT và DLL native authentication đã có):

```powershell
mvn -o -q '-Dtest=MergeMigrationIT' '-Dtimeline.test.enabled=true' '-Dtimeline.test.database=ctsv_merge_test_20261122' test
```

**1 PASS,0 failure/error/skip.** Đã thực thi:

1. V1–V19 từ main trên DB riêng.
2. Tạo1 học kỳ/đợt synthetic ở V19.
3. Upgrade V20/V21, kiểm dữ liệu đợt còn nguyên;8 planned fields cũ không bị điền ngày giả;2 guidance columns được retire đúng SQL.
4. Flyway validate và migrate lần hai0 migration; checksum/history V1–V19 giữ nguyên.
5. Hibernate `ddl-auto=validate` toàn bộ Entity trong package ứng dụng đạt.

`TimelineDatabase` thêm đúng1 target merge được allowlist, giữ owner/marker guard; từ chối DB có2 script version riêng cũ trước khi ghi fixture. Không dùng các evidence DB cũ để test lại numbering mới. Helper tạo DB cho lượt này ở `target/prepare-merge-test.ps1` (ignored), không chứa credential.

## Build/test

| Kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| FE trước merge `npm test` |58 PASS | Baseline |
| BE trước merge `mvn -o -q test` |410 PASS,1 ERROR,0 fail/skip | contextLoads nạp fixture Học bổng/thiếu Auth origins; owner cho bỏ qua riêng, không ghi PASS |
| FE sau merge `npm test` |113 PASS,4 FAIL,0 skip |4 ca navigation bên dưới |
| FE sau merge `npm run build` |PASS | Typecheck +Vite121 modules |
| SQL MergeMigrationIT |1 PASS | REAL SQL +Flyway +Hibernate, toàn source merged đã compile |
| Main-isolated navigation |8 PASS,4 FAIL | Copy đúng source/test `origin/main` vào target; tái hiện cùng4 lỗi |
| BE Unit toàn bộ sau merge / executable JAR mới |NOT RUN | Dừng ở lỗi navigation; không thay JAR DEV đang chạy |
| DEV smoke với source merged |NOT VERIFIED | DEV history chuỗi riêng, chưa được chuyển đổi |

## Lỗi đã xác minh — owner cho bỏ qua để push

4 ca tại `frontend/tests/module-navigation.test.mjs`:

- SHCN faculty and round permissions expose only their respective pages.
- SHCN and scholarship permissions coexist without overriding each other.
- Scholarship specialist sees KKHT navigation and direct route.
- Users without scholarship permission cannot access KKHT routes.

Nguyên nhân: `if(access) capabilities.add('ctsvIdentity')` coi mọi object access là identity CTSV đã xác minh, kể cả fallback App dựng từ quyền module khác. Test main kỳ vọng không có mục `mghp-inbox`, nhưng actual có thêm mục này. Cùng logic/test đã có trên main; không do xử lý conflict thêm ra.

**Đề xuất sửa ở lượt riêng:** tách dấu hiệu identity CTSV đã xác minh từ kết quả quyền server thành điều kiện rõ tại App/navigation; giữ quyền module khác độc lập. Không suy identity từ boolean Role, không chỉ đổi expected để test xanh. Owner đã chọn giữ nguyên lỗi và cho commit/push trong lượt này; không mở rộng sang sửa quyền/navigation.

## Evidence / tiếp tục

- `target/merge-migration.log`, `target/merge-migration-comparison.json`, Surefire `MergeMigrationIT`.
- `target/merge-frontend-tests.log`, `target/main-navigation-check/result.log`.
- Không lưu token/credentials/browser state; không gọi CA/storage hoặc tạo hồ sơ người dùng thật trong lượt sync.
- Theo quyết định cuối: giữ nguyên các lỗi được miễn → review staged/secrets và version lần cuối → commit merge → fetch kiểm remote chưa tiến thêm → push thường `codex/p01-slice-a`. Kết quả/SHA được xác minh qua Git và báo trong chat; chưa tạo PR. Không xem quyền push là quyền chuyển đổi history DEV hoặc bỏ qua các lỗi khác chưa được báo.
