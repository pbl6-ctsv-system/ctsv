# 08 — Chuẩn bị Git release P00–P04

[Mục lục](README.md) · [Báo cáo](10-handover-review-report.md)

**Không stage/commit/push trong task này.** B01 chỉ là READY TO COMMIT khi tập source/config/tools/docs đã đủ và quét không còn secret rõ ràng; source hiện có local không có nghĩa release đã hoàn thành. Clone sạch vẫn NOT VERIFIED.

## 1. Repo và nhóm source cần review/commit cùng nhau

| Repository | Nhóm file bắt buộc | Lưu ý |
|---|---|---|
| ctsv | POM, wrappers, AGENTS, properties an toàn | Giữ Java21/Boot3.5.16; default không tự nối DB/migrate |
| ctsv | src/main Java Auth/Foundation/shared và phần đã giao, resources/contracts, db/migration V1–V7 | Schemas/catalogs là runtime classpath; toàn bộ source P00–P04 hiện phần lớn untracked |
| ctsv | frontend source/config/public, package.json/package-lock.json; e2e source + lock riêng | Giữ Rollup WASM, không đưa node_modules/dist hoặc browser storageState |
| ctsv | dev.ps1, dev-sync.ps1, tools/dev (helpers, tests, smoke) | Private config/key được sinh riêng, không lấy từ máy tác giả |
| ctsv | docs/team-handover-p00-p04 (11 file) | Cặp source/runtime tương thích; docs chứa preset synthetic theo owner decision |
| ctsv | src/test Java/resources đã có | Chỉ fixture synthetic/source; không copy runtime evidence thô |
| daotao | POM/wrappers/AGENTS, main/test Java/resources, contracts và migrations V1–V5 | **V2__slice_a_database_options.sql.conf** phải đi cùng SQL để chạy ngoài transaction |
| daotao | Main class/properties/test starter đã rename | Index đã có rename trước task; owner phải review cả staged và unstaged |
| daotao | .gitignore | Cùng bảo vệ generated/private runtime |
| taichinh | Không có dependency runtime mới | Local folder taichinh, remote ketoan. Không phát hành lại vì task sync |

HEAD hai repo vẫn starter, branch local codex/p01-slice-a; không có commit release mới. Xem SHA trong [10](10-handover-review-report.md). Không giả định Organization là repo cha.

## 2. Giữ ngoài Git

- TLS/JWT private keys; PFX/P12/JKS/PEM; config/argfiles chứa service key/PFX password; DPAPI/config riêng.
- SIS service key, access/refresh token, SQL credentials cá nhân, dữ liệu thật.
- DB backups/dumps/data files, logs, PID/process state, spool/index, caches, node_modules/target/dist.
- Evidence/screenshots có token, dữ liệu nhạy cảm hoặc browser storageState.

Root .gitignore hai repo đã thêm pattern cho generated/private files, .env (cho phép .env.example), key/cert/keystore, log/PID, DBbackup/data, node_modules/dist. **Không ignore application-local.properties/application-dev.properties an toàn, lockfiles, SQL .conf hoặc schemas.** Private home thực nằm ngoài repo và có ACL/DPAPI.

.gitignore không bảo vệ ngược file đã tracked; phải kiểm index/HEAD. Không dùng git rm/reset tự động nếu phát hiện leak: dừng và owner xử lý đúng phạm vi.

## 3. Credentials synthetic được phép

B02 **OWNER-ACCEPTED DEV CREDENTIAL RISK**: preset ba tài khoản giả lập được giữ trong tools và hướng dẫn private repo. Không nhầm password fixture/test với production secrets; cũng không dùng quyết định này cho TLS/JWT/service/DB secrets thật.

Quét source đã tracked và untracked exclude-standard để phát hiện private-key PEM material, JWT literal, credential URI và file nhạy cảm. Kết quả/phạm vi scan ở report; không khẳng định remote/unreachable history sạch.

## 4. Dependency máy mới

Runtime schemas/catalogs/migrations có trong source. Setup tạo riêng keys/config/account/registry/fixture/epoch/truststore. Cài riêng JDK/Node/PS7/SQL/DLL, tải Maven/npm theo locks; không copy .m2/settings hoặc LOCALAPPDATA tác giả.

Runtime không cần nghiepvu. Khi phát triển/review contract-first, owner cung cấp bộ tài liệu thiết kế/testing đã duyệt đã lọc qua kênh nhóm; không đẩy .analysis/CSV/log thô. Không có URL clone nghiepvu đã xác minh để tự bịa.

## 5. Quy trình vòng sau — owner trực tiếp cho phép

Cwd từng repo, không chạy Git write ở lượt này:

1. Đọc git status --short, git diff --stat, git diff --cached --stat. Review nhóm trên, chú ý rename SIS đã staged từ trước.
2. git ls-files kiểm tracked nhạy cảm; git check-ignore -v kiểm một số key/config ví dụ và file runtime phải giữ.
3. Chỉ khi owner cho phép: stage đúng nhóm source/POM/locks/migrations/scripts/docs. Kiểm git diff --cached --check, --stat và nội dung nhạy cảm trước commit.
4. Commit có ý nghĩa trên feature branch, push khi được giao; pin cặp SHA CTSV/SIS tương thích. Không tự push main, tag/PR/merge.
5. Sau push, clone vào thư mục mới/máy khác, cài dependencies từ đầu, thực hiện [02](02-installation-guide.md) rồi [06](06-sis-ctsv-sync-guide.md), điền [09](09-onboarding-checklist.md). Không copy dev private home.
6. Chỉ kết luận B01 hoàn tất thực sự sau commit/push và clone chạy thành công. G8 cũ vẫn WAIVED, không tạo gate nghiệm thu P04 mới.
