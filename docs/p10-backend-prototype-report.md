# P10 — Backend và prototype review, 22/11/2026

**Kết quả cuối22/11, sau owner duyệt prototype:** P10-01–04/06 VERIFIED; P10-99 **ACCEPTED WITH OWNER WAIVER**. G8 **WAIVED BY OWNER**, không PASS. React chính đã tích hợp và Playwright kiểm trên `ctsv_dev`; xem §8–11. Bốn test navigation cũ ngoài P10 còn FAIL theo ngoại lệ owner đã chấp thuận trước đó, không được ghi PASS. Không commit/push/merge, không kiểm Git theo chỉ dẫn owner, không mở P11.

## 1. Trạng thái task

| Task | Kết quả |
|---|---|
| P10-01 | VERIFIED trong phạm vi technical wire, migration/Entity, source/MAX/hash/ETag/read-context. tiePriority nhỏ hơn ưu tiên theo câu trả lời owner. |
| P10-02 | VERIFIED backend: một root/đợt; toàn bộ nguồn hợp lệ; một M cao nhất, chỉ khoảng đã xác minh; chống nhóm đã công bố ở đợt khác; phân trang đọc không cắt tập xét. |
| P10-03 | VERIFIED backend trước ký: bản sau họp clone đúng FK; snapshot/header cũ bất biến; chốt không tạo revision mới; actual Hội đồng, CAS, rollback và race. Correction sau từ chối ký phải được P11 cấp đúng context, API thường không unlock bản locked. |
| P10-04 | VERIFIED backend: history/header/minutes/link metadata đúng phiên bản, ETag thay khi representation đổi. PDF fixture chỉ metadata, không bằng chứng upload/ký P11. |
| P10-05 | RETIRED SCOPE BY OWNER, không triển khai. |
| P10-06 | VERIFIED: prototype đã được owner duyệt; workspace React chính, build/typecheck, test tập trung và 24 kiểm tra browser trên DEV đạt. |
| P10-99 | ACCEPTED WITH OWNER WAIVER: technical evidence backend/React/DEV đầy đủ trong phạm vi P10; G8 WAIVED BY OWNER. Ngoại lệ 4 frontend test cũ được ghi riêng §8. |

## 2. API đã có

Prefix /api/ctsv/mghp, JWT + fresh capability mghp_chuyen_vien:
- POST /quyet-dinh — tạo toàn tập từ nguồn đã Đóng xét; exact retry200, mới201.
- GET /quyet-dinh?roundId&page&size và GET /quyet-dinh/{id} — danh sách/root/head.
- GET /phien-ban-quyet-dinh/{id}; GET /{id}/ung-vien và /{id}/dong — snapshot và các trang con.
- POST /phien-ban-quyet-dinh/{id}/ket-luan-hop — actual meeting, metadata theo version; cùng nội dung không nhân version.
- POST /phien-ban-quyet-dinh/{id}/xac-nhan — khóa head sau họp, actual/actor ghi cùng TX.
- GET /quyet-dinh/{id}/lich-su?page&size — đọc lịch sử đúng bản.

If-Match thiếu428/cũ412; draft/locked/nguồn/permission đều kiểm tại backend. Admin Auth/SV thiếu capability403. Không endpoint QD05/refresh/stop. Wire đầy đủ ở [contract §10](../../nghiepvu/01-thiet-ke/ctsv/api-contracts/decisions-publication-api.md#10-p10-technical-wire--22112026). MINUTES_CODE_CONFLICT409 cho mã biên bản đã dùng ngoài exact replay.

## 3. Database, Entity và DEV

- V22__mghp_decision_snapshot.sql: fence root/version/minutes, header/source/command snapshot, unique root/round và BB/version, FK đơn vị.
- V23__mghp_decision_history_guards.sql: append-only candidate/line/period ngay từ bản trước họp. V22 đã áp trên DB riêng nên bổ sung V23, không sửa migration đã áp.
- Tái sử dụng sáu bảng hiện hữu; DecisionEntities map đủ sáu Entity. Flyway/JPA validate thật đạt.
- DB kiểm riêng ctsv_p10_test_20261122, localhost\\DEV2022, Windows owner/marker guard, compatibility160/collation chuẩn. Không reset/drop/clean DB nào.
- DEV ctsv_dev: backup COPY_ONLY/CHECKSUM + RESTORE VERIFYONLY; V21→V23 bằng dev.ps1 build/migrate/start. **Ở gate backend, 131 bảng cũ giữ nguyên số hàng và SHA-256 của toàn bộ cột cũ**, checksum/script V1–V21 giữ nguyên. Sau approval UI, tạo fixture riêng P10UI theo quyền owner; nguồn cũ được bảo vệ bằng hash trước/sau (§9).
- Backup: D:\\DevTools\\SQL2022Data\\MSSQL16.DEV2022\\MSSQL\\Backup\\ctsv_dev_pre_p10_20261122_152701_4ca14d.bak.
- JAR gate backend trước approval có SHA-256 `623EE6CC9E6434FE90D43F05C95B0BA053CA060E58155D71417B1CC90FB05ECA`; đã được thay bằng JAR cuối §8 sau sửa read projection Hội đồng.
- Launcher gate backend READY; HTTP DEV ba role login200; list P10 CV200/SV403/Admin403. PID/JAR hiện hành được ghi ở manifest cuối, token chỉ trong bộ nhớ.

## 4. Kiểm chứng thực tế

**41 test case duy nhất PASS / 0 FAIL / 0 ERROR / 0 SKIP**, tổng hợp các report thực thi, không cộng lại ca chạy lặp:

| Mức | Số | Nội dung |
|---|---:|---|
| Unit P10 | 8 | MAX một M, khoảng không được lấp, tiePriority, validation, canonical hash/key order |
| SQL migration | 1 | Fresh chain/upgrade V21→V22→V23, indexes/fences/guards; Hibernate validate trong HTTP harness/DEV |
| HTTPS/JWT + SQL P10 | 17 | Create/meeting/confirm/history; 21nhóm/page; MAX hai M qua P08 thật; cross-round published fixture; metadata links; unknown exclusion; 401/403/404/412/428; revoked capability/JWT; retry; audit fault/rollback; create/create, meeting/meeting, confirm/confirm, confirm/source-revision races |
| Regression Unit | 15 | Auth4, Foundation1, P08 Evaluation3, MGHP Round2, Timeline5 |

Các ca HTTP dùng Spring Boot HTTPS thật, JWT/Auth/Foundation thật, JPA và SQL Server thật. Fixture policy/đánh giá synthetic, PDF chỉ metadata/ledger test; không gọi Cloudinary/CA/Portal/Kế toán. Historical publication/link fixtures không chứng minh P11/P12.

Lệnh cwd ctsv, P00_SQL_PORT lấy từ registry đã xác minh; native SQL auth DLL có sẵn:
~~~powershell
mvn -o -q '-Dtest=DecisionRulesTest' test
mvn -o -q '-Dp10.test.enabled=true' '-Dtest=DecisionSqlIT,DecisionHttpIT,DecisionRulesTest,DecisionHashTest' test
mvn -o -q '-Dp10.test.enabled=true' '-Dtest=DecisionSqlIT,DecisionHttpIT,DecisionRulesTest,EvaluationRulesTest,TimelineRulesTest,pbl6.ctsv.mghp.configuration.RoundRulesTest,AuthPolicyTest,FoundationPolicyTest' test
# Các ca bổ sung chỉ chạy đúng selector:
mvn -o -q '-Dp10.test.enabled=true' '-Dtest=DecisionHttpIT#documentMetadataAndEtagStayBoundToTheirOwnVersion' test
mvn -o -q '-Dp10.test.enabled=true' '-Dtest=DecisionHttpIT#confirmedUnknownIsExcludedWithoutChangingItsConclusion+confirmRacingSourceRevisionDoesNotChangeClosedSource' test
~~~

Evidence local:
- [Manifest, ID từng ca và report](../target/p10-20261122/manifest.json).
- [Report lượt cuối14HTTP + Unit/SQL/regression](../target/p10-20261122/backend-final/).
- [Link metadata](../target/p10-20261122/document-link/), [source safety/race](../target/p10-20261122/source-regression/).
- [Bảo toàn DEV/backup/JAR](../target/p10-20261122/dev-upgrade.json), [DEV HTTP](../target/p10-20261122/dev-http-smoke.json).
- Logs target/p10-backend-final.log, p10-targeted-final.log, p10-document-link.log, p10-source-regression.log, p10-dev-update.log.
- Không chạy full backend P00–P09, không chạy lại kiểm thử Cloudinary/CA không bị thay đổi.

## 5. Prototype — lịch sử gate đã được owner duyệt

**Bổ sung ảnh review22/11:** [Gallery18ảnh](http://127.0.0.1:5180/gallery.html) thay cho việc chỉ xem2ảnh desktop ban đầu. Tạo/điền synthetic qua click thật trong prototype, chụp đầy đủ từng bước và cả ba tab sau chốt, lịch sử chỉ đọc, ba tab mobile. Mỗi ảnh có assertion nội dung trước chụp;0pageerror/0API nghiệp vụ. [Manifest](prototypes/p10/review-manifest.json). Không chạy lại backend tests hoặc thay trạng thái nghiệm thu; chưa tích hợp React/ghi DB trong lượt bổ sung ảnh.

Mở **http://127.0.0.1:5180/**; [hướng dẫn và ảnh](prototypes/p10/README.md).
Luồng: chọn đợt đã Đóng xét → tạo bộ → xem danh sách/căn cứ → ghi cuộc họp → kiểm bản sau họp → chốt → lịch sử.
Không form ID, không chọn tay sinh viên/rate; không tạo Navbar/router mới cho app chính.

**13 assertions Playwright MCP PASS**, script [verify.mcp.js](prototypes/p10/verify.mcp.js), mode STUB/no API/no SQL:
đợt chưa đóng không tạo; tạo đúng detail; giải thích M thắng; Escape; search; lỗi ngày tương lai nhìn thấy và giữ input; sửa rồi lưu; hai version; bản cũ chỉ đọc; hủy không nhân bản; chốt khóa; mobile390px; không JS exception/API nghiệp vụ.
Console sau navigation cuối:0error/0warning. Không tính kết quả này là E2E thật.

Ảnh: [danh sách](prototypes/p10/01-list-desktop.png), [chi tiết desktop](prototypes/p10/02-detail-desktop.png), [mobile](prototypes/p10/03-locked-mobile.png).
Tự review đã sửa bảng mobile bị bó chữ thành từng khối có nhãn; cho ba tab xuống dòng để thấy lịch sử; lỗi nổi trong viewport, success tự đóng; không làm lại theme chung.

## 6. Lỗi đã phát hiện/sửa và giới hạn

- HTTP ban đầu6/7FAIL: truy cập field repository qua Spring proxy gây NPE. Sửa bằng phương thức repository, service truyền Entity/typed values; chạy lại đúng workflow rồi bộ liên quan đạt. Log đỏ target/p10-http-first-red.log giữ nguyên.
- Cần bảo vệ cả snapshot draft/lịch sử, không chỉ locked: bổ sung V23 và SQL assertions UPDATE bị từ chối.
- Đối chiếu Policy API thấy range được duyệt là [0,1]; thêm ca Unit RED cho0, sửa validator không tự siết (0,1], rồi6rule+2hash Unit GREEN; build/start JAR cuối lại, không migrate/seed thêm.
- Retry cùng nội dung từ head sau họp phải không tạo revision/mã BB trùng: canonical command hash + replay; assertions trong workflow.
- Phân trang đọc dùng SQL/JPA limit/count trước projection; tổng là JSON integer, không string ID. Ca21nhóm chứng minh không mất nhóm thứ21.
- Script bảo vệ DEV ban đầu dừng trước migration vì history out-of-order không thể dùng installed_rank cuối như version lớn nhất; sửa kiểm MAX(version). Kiểm exit-code PowerShell dùng trạng thái thực thay LASTEXITCODE null. Đọc lại DBV21 trước tiếp tục, không chạy ghi mù.
- Prototype ban đầu locator label select timeout và favicon404; kiểm DOM/đổi role locator, thêm favicon nội tuyến. Không quy lỗi công cụ cho backend.

Chưa xác minh: UI React P10 thật, refresh/deep link/session/role navigation/stale-state recovery trên app chính và FE/BE E2E ctsv_dev — đang chờ gate owner. G7/P10-99 chưa hoàn tất. Multi-role UI P10 chưa thử vì chưa tích hợp.
C10 sau từ chối ký/unknown, PDF bytes/hash/signatures và publication/Portal/accounting thuộc P11–P13, không nhận metadata fixture là bằng chứng. Không sửa locked qua lệnh họp thông thường.

## 7. Files thay đổi

Backend mới trong src/main/java/pbl6/ctsv/mghp/application: DecisionController, DecisionService, DecisionRepository, DecisionSource, DecisionReads, DecisionRules, DecisionEntities. IntakeErrors thêm controller vào advice.
Migration V22/V23; tests DecisionDatabase/DecisionSqlIT/DecisionRulesTest/DecisionHashTest và auth/DecisionHttpIT.
Prototype riêng docs/prototypes/p10: index.html, style.css, app.js, serve.cjs, verify.mcp.js, README và3PNG.
Tài liệu: report này, persistence-entity-inventory; nghiepvu contract/catalog, owner register, fixtures/tests, Master Backlog/Roadmap, p10-readiness-audit và p10-implementation-plan.
Ở gate prototype chưa đổi React chính. Sau approval, các file React P10 và navigation MGHP chung được tích hợp như §10; không đổi Auth implementation, module thành viên khác hoặc dependency/framework.

Gate prototype đã được owner duyệt bằng câu “oke chốt, tui duyệt prototype”. Kết quả sau approval ở dưới; P11 chưa được bắt đầu.

## 8. Tích hợp React và kết quả cuối sau approval

Vào **Miễn giảm học phí → Hội đồng & quyết định** tại https://127.0.0.1:5173/mghp/hoi-dong-quyet-dinh bằng `dev.chuyenvien`. Một workspace trong AppShell chung, một mục trong nhóm MGHP; capability/route guard hiện hành, Auth Admin không tự có quyền. React lazy-load riêng P10, không thêm router/thư viện.

Luồng thật: chọn đợt đã Đóng xét → tạo bộ từ toàn tập nguồn → xem danh sách hưởng và căn cứ → ghi kết quả họp thực tế → chốt → xem các phiên bản chỉ đọc. Mỗi write thành công đọc lại list/root/detail/history, nhãn danh sách đổi ngay. Dữ liệu nguồn/rate/winner do backend quyết định; không gửi lựa chọn/rate từ FE. Form không bắt nhập ID, lỗi nhìn thấy trong modal và qua feedback hiện hành; giữ nội dung khi stale/không rõ kết quả, bắt đọc lại trước gửi tiếp. Không tự retry write.

| Kiểm tra thực thi | PASS | FAIL | SKIP | Phạm vi |
|---|---:|---:|---:|---|
| Backend baseline đã chạy | 41 | 0 | 0 | SQL riêng `ctsv_p10_test_20261122`, HTTPS/Auth/SQL thật; 10 hash nguồn core không đổi |
| Backend chạy lại sau sửa tiến độ | 6 | 0 | 0 | 1 HTTP/SQL confirm-race + actual council; 5 Timeline Unit. Đây là ca chạy lại, không cộng thành 47 ca duy nhất |
| Frontend tập trung | 19 | 0 | 0 | P10 7 ca, configuration/timeline/Auth liên quan; Unit/stub client |
| Frontend toàn bộ lượt cuối | 120 | 4 | 0 | 124 ca; bốn FAIL legacy bên dưới, không tính toàn suite là PASS |
| Playwright MCP trên DEV | 24 | 0 | 0 | 17 kiểm tra luồng chính/fault/quyền và 7 kiểm tra hồi quy cuối; gồm đăng nhập, không phải 24 workflow nghiệp vụ độc lập |
| Build/typecheck | PASS | — | — | `npm.cmd run build` chạy `tsc -b` + Vite; chunk chính471.89kB, chunk P10 29.77kB |

Bốn test cũ vẫn FAIL do assertion inventory navigation chưa tính `mghp-inbox` của CTSV identity: `SHCN faculty and round permissions expose only their respective pages`; `SHCN and scholarship permissions coexist without overriding each other`; `scholarship specialist sees KKHT navigation and direct route`; `users without scholarship permission cannot access KKHT routes`. Giữ ngoại lệ owner đã chốt “bỏ qua lỗi này”; không sửa module khác/expected để xanh và không dùng G8 để che lỗi. Trong test navigation hiện có chỉ bổ sung mục P10 vào hai expected inventory thực sự bị thay đổi bởi task.

Lệnh thực tế (cwd `ctsv/frontend`):

```powershell
npm.cmd run build
node --test tests/mghp-decisions.test.mjs tests/mghp-configuration.test.mjs tests/timeline.test.mjs tests/auth-client.test.mjs
npm.cmd test
```

Sau RED regression actual council, chạy GREEN tại cwd `ctsv`, DB test P10 guard/native SQL auth như harness cũ:

```powershell
mvn -o -q '-Dp10.test.enabled=true' '-Dtest=DecisionHttpIT#concurrentConfirmLocksOnlyOnceAndWritesActualCouncilTime,TimelineRulesTest' test
```

DEV đã `dev.ps1 stop → build → start`, không chạy lại migration. PID cuối BE18016/FE7604; V23, owner/marker và SQL sessions của PID backend trỏ duy nhất `ctsv_dev`. JAR cuối `target/ctsv-0.0.1-SNAPSHOT.jar`, SHA-256 `7B102EEA42D12F224995A102FFF3920DE5ED61BAA0F7160373F8BD2A105C5E9B`.

## 9. Coverage browser và dữ liệu DEV

Mọi Playwright P10 sau approval dùng **React + Spring Boot + SQL Server `ctsv_dev` thật**, không chuyển sang DB test. Trước fixture và sau lượt cuối xác minh instance/owner/marker/Flyway/launcher SQL sessions. Ba tài khoản đăng nhập một lần mỗi role, phiên giữ trong RAM; không lưu token/password/storageState.

| Hành vi | Kiểm chứng thực tế |
|---|---|
| Login/home và menu | CV/SV/Admin về trang chủ; CV có mục P10; SV và Admin không có capability bị chặn cả menu và direct URL |
| Nguồn/tạo bộ | 4 đánh giá đủ căn cứ → 3 nhóm hưởng; POST201, readback/detail/list đúng dữ liệu persisted |
| Một M cao nhất | Trần Hải Yến: M8 50% tháng9–10 thắng M9 25% cả kỳ; UI căn cứ có cả hai; không lấp tháng còn lại |
| Validation và sửa lỗi | Actual meeting tương lai bị từ chối trên UI; sửa ngày tiếp tục được, không bắt đọc lại vì lỗi local |
| Họp/metadata/history | POST201 tạo bản2, số/ngày/biên bản đọc lại đúng; bản1 giữ header cũ và không có lệnh sửa |
| Chốt và hai tab | Tab thứ nhất200/locked; tab thứ hai giữ ETag cũ412, đọc lại thấy locked; list đổi Bản2/Đã chốt ngay |
| Mất phản hồi sau commit | Backend ghi meeting201/version4, cố ý ngắt response; UI giữ input, không tự gửi lại; đọc lại thấy đúng version đã commit |
| Hai tab sửa cuộc họp | Tab thứ hai tạo version5; tab thứ nhất ghi version4 nhận409; input giữ, đọc head5, không ghi đè |
| Refresh/UX/mobile | Query đợt/version được khôi phục; ba tab, căn cứ, Escape, ảnh390px không tràn; logout đúng |
| Hồi quy cuối | Chính sách M1–M9 vẫn truy cập; chi tiết đợt hiển thị Hội đồng Đã hoàn thành/actual từ commit, không dùng lịch dự kiến |

Fixture mới có tiền tố **P10UI-20261122** (không sửa/xóa dữ liệu cũ):

- Học kỳ thực hành4, lớp `23T-P10`; bốn sinh viên DEV-P10-001…004 và bốn quá trình học riêng, không thêm quá trình vào `dev.sinhvien`.
- Hai đợt **20041** (3 sinh viên, 4 hồ sơ) và **20042** (1 sinh viên, 1 hồ sơ); 3 phiên bản chính sách synthetic M2/M8/M9. Không thay bản chính sách đã xác nhận cũ.
- Năm hồ sơ **10030–10034**, bản nộp10043–10047, document metadata10067–10071; P08 HTTP đánh giá/xác nhận, đóng nhận và Đóng xét thật trước khi UI P10 lấy nguồn.
- Bộ **P10UI-QD-HKI-2026**: root1/version1→2 đã chốt, 3 nhóm. Bộ **P10UI-QD-RETRY-2026**: root2/version3→4→5, bản3 hiện hành còn nháp để owner thực hành tiếp.
- File metadata/check/ledger là synthetic phục vụ đủ căn cứ, **không có upload/bytes Cloudinary**; không dùng làm bằng chứng PDF/ký. Không tạo account mới, không xóa fixture sau kiểm để owner xem lại.

`p10-dev-fixture.ps1 -Action Verify` đạt: hash nguồn không mang tiền tố P10UI của đợt/hồ sơ/đánh giá/chính sách/credential/tài liệu quyết định cũ giữ nguyên trước/sau. Guard và IDs trong [fixture](../target/p10-ui-20261122/fixture.json), [persisted](../target/p10-ui-20261122/persisted.json).

## 10. Lỗi tìm thấy, sửa và file của lượt tích hợp

1. Validation ngày họp local từng bật cờ “phải đọc lại”, khiến sửa ngày xong vẫn không gửi được. Tái hiện browser, tách `DecisionInputError`/`requiresDecisionRead`; thêm regression Unit RED→GREEN, chạy lại browser sửa ngày→lưu đạt.
2. Read projection timeline còn trả Hội đồng `not_implemented` dù P10 đã chốt actual. HTTP/SQL regression RED chứng minh; sửa `RoundTimelineService` đọc actual chốt, giữ plannedDate riêng và cảnh báo mềm, chạy lại 6 ca + UI tiến độ đạt. Không migration mới, không triển khai ký/công bố.
3. Summary DOT list không chứa reviewClosedAt: nhãn dropdown chỉ dùng trạng thái biết được, không tự kết luận chưa Đóng xét. Detail/source vẫn là căn cứ cho tạo bộ. Link tới quản lý đợt không hứa mở detail bằng query chưa được hỗ trợ.

File frontend mới: `frontend/src/features/mghp/decisions/{types.ts,model.mjs,api.mjs,DecisionWorkspace.tsx,DecisionDialogs.tsx,DecisionWorkspace.module.css}`; test `frontend/tests/mghp-decisions.test.mjs`; tools kiểm `frontend/e2e/p10-dev-fixture.ps1`, `frontend/e2e/p10-final-dev.mcp.js`. Sửa `ConfigurationPage.tsx`, `app/navigation/modules.mjs`, hai inventory expectations P10 trong `module-navigation.test.mjs`. Backend chỉ thêm read projection council ở `RoundTimelineService.java` và regression tại `auth/DecisionHttpIT.java` trong lượt này; core backend/migration của gate trước giữ nguyên hash.

Đã tự xem ảnh desktop/form/history/mobile; giữ màu/tokens/Navbar/route guard hiện có, bảng mobile đọc theo từng sinh viên. Không đổi Auth/JWT, API request/response P10, dependency hoặc module khác. Read projection council dùng đúng fields contract hiện hữu.

## 11. Evidence, giới hạn và bàn giao

[Manifest cuối](../target/p10-ui-20261122/manifest.json), [24 kiểm tra browser](../target/p10-ui-20261122/browser-cases.json), [frontend suite thật](../target/p10-ui-20261122/frontend-suite.log), [build](../target/p10-ui-20261122/frontend-build.log), [XML targeted](../target/p10-ui-20261122/TEST-pbl6.ctsv.auth.DecisionHttpIT.xml). Baseline41/backend có manifest riêng §4, hash10file core đối chiếu không đổi; không cộng test chạy lại.

Ảnh app thật: [danh sách](../target/p10-ui-20261122/real-locked-list.png), [căn cứ hai M](../target/p10-ui-20261122/real-basis.png), [form cuộc họp](../target/p10-ui-20261122/real-meeting-form.png), [cuộc họp & văn bản](../target/p10-ui-20261122/real-meeting.png), [lịch sử](../target/p10-ui-20261122/real-history.png), [bản trước họp](../target/p10-ui-20261122/real-before-meeting.png), [tiến độ actual](../target/p10-ui-20261122/real-round-council-progress.png), [stale giữ form](../target/p10-ui-20261122/real-stale-form.png), [mobile cuộc họp](../target/p10-ui-20261122/real-mobile-meeting.png), [mobile history](../target/p10-ui-20261122/real-mobile-history.png).

Các ca ghi/fault được chạy bằng MCP inline và lưu kết quả/assertion IDs; script cuối chỉ replay kiểm đọc/hồi quy từ phiên trong RAM, không phải CLI suite độc lập. Không có trace thô/header/storageState trong artifact bàn giao. Listener network ban đầu chồng nhau nên tuples chỉ chứng minh status/path, không dùng để đếm request. Ca lost-response có assertion riêng một write và đọc persisted version. Proxy test dùng `route.fetch(ignoreHTTPSErrors:true)` cho **loopback riêng ca ngắt phản hồi**; browser/app TLS không đổi, không coi ca này là kiểm chứng TLS. Lần thử proxy đầu bị lỗi trust của công cụ; đã đọc lại DB thấy root đã tồn tại trước khi tiếp tục, không tạo lại.

Không còn blocker P10 trong phạm vi đã giao. Bốn FAIL legacy nói trên giữ nguyên ngoại lệ; chưa có fixture browser multi-capability (Unit union đạt). Không chạy lại upload/download, CA, Portal/Kế toán vì P10 không đổi tích hợp đó; không nhận metadata fixture/prototype STUB là REAL integration. P09 exact certificate/trust/real hai người vẫn là gate riêng của P11/P12. **P10-99 ACCEPTED WITH OWNER WAIVER, G8 WAIVED BY OWNER; chưa bắt đầu P11.**

