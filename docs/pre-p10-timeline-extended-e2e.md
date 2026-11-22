# PRE-P10-01 — Extended DEV browser coverage

> Lưu ý phiên bản: báo cáo này kiểm UI/V17 trước thay đổi form cùng ngày21/11. Owner sau đó duyệt bỏ hai hướng dẫn, gom lịch và chọn nhiều M bằng bảng; DEV hiện dùng V18. Giữ nguyên kết quả/evidence lịch sử24ca, không nhận chúng là lượt chạy lại trên UI mới. Delta mới và regression thực tế xem [form đợt](round-form-simplification.md). Các script timeline cũ gắn V17/selector cũ cần đối chiếu delta khi tái sử dụng; không bỏ guard để chạy ép.

Ngày: 21/11/2026. Scope owner cho phép: React + Spring Boot launcher thật + `ctsv_dev`; chỉ dữ liệu SYNTHETIC có prefix `TLX-`. Không reset database, không triển khai P10. Đây là kiểm chứng bổ sung, không mở lại nghiệm thu/G8 đã miễn.

## Coverage trước khi chạy

| Phạm vi | Evidence cũ | Bổ sung lần này |
|---|---|---|
| Tạo lịch, 8 mốc | DEV smoke tạo lịch đầy đủ | Required-only/null, sai thứ tự, persist/reopen, timezone khác Việt Nam, overlap mềm |
| Sửa lịch/gia hạn | DEV smoke một gia hạn hợp lệ và history | Trước/sau hạn cũ, không tăng/quá khứ/vượt reviewDeadline, reason, stale, locked business config, closed/completed |
| Cửa nhận sinh viên | HTTP SQL test DB riêng | Browser upload/nộp trước mở, sau hạn và sau gia hạn trên DEV |
| Đóng xét | DEV smoke đợt rỗng | Nhiều hồ sơ đạt/không đạt/cần xác minh, chưa xét, bổ sung chưa xử lý, nguồn đổi sau preflight |
| Bảo vệ nguồn | HTTP SQL test DB riêng | UI sửa sau chốt bị từ chối; hồ sơ/receiving round khác còn xử lý được |
| Quyền/hiển thị | Browser test DB riêng | SV/Admin Auth direct URL, refresh/logout, responsive DEV |

## Thứ tự và evidence

1. Guard instance/owner/marker/V17, runtime launcher datasource/JAR. Snapshot Git và số hàng ngoài fixture.
2. Chuẩn bị học kỳ, quá trình học và phiên bản chính sách fixture riêng. Fixture SQL không được nhận là thao tác người dùng; metadata-only PDF không được nhận là Cloudinary REAL.
3. Browser tạo/sửa/mở/gia hạn và kiểm persisted bằng UI/API + SQL.
4. Browser nộp có PDF mẫu qua Cloudinary thật; chuẩn bị thêm nguồn SQL có đánh dấu cho các kết luận. Ghi/xác nhận kết luận và thao tác bổ sung qua UI thật.
5. Browser preflight, source stale, đóng xét, bảo vệ nguồn và phân quyền; kiểm actual/audit/không có quyết định hoặc PDF tự phát sinh.
6. Report JSON có assertions + ảnh; không lưu token/cookie/secret. Trace chỉ bàn giao sau lọc bí mật. Kết quả chạy sẽ cập nhật tại đây.

Không chạy lại suite cũ để tăng PASS. Chỉ regression bị tác động. Chưa chạy không ghi PASS.

## Kết quả thực thi 21/11/2026

**24/24 ca chức năng PASS, 0 FAIL / SKIP / BLOCKED ở kết quả cuối.** Bước `B06-SETUP` tạo fixture được tính riêng, không cộng vào 24. Đây là automated assertions trên browser/HTTP/SQL, ảnh chỉ bổ trợ. G8 waiver cũ giữ nguyên; không nhận là kiểm thử độc lập.

Tất cả E2E mới chạy tại **https://127.0.0.1:5173 → daily launcher Backend https://127.0.0.1:8443 → SQL Server `ctsv_dev` V17**. Đã kiểm actual JDBC sessions gắn PID launcher, instance `DESKTOP-7PPHP5B\DEV2022`, owner `DESKTOP-7PPHP5B\USER`, marker `ctsv_dev_owner=ctsv-local-dev-v1`, TCP động kiểm lại là56826. Không chạy harness/testDB hoặc bỏ TLS validation. Browser Chrome thật, timezone America/New_York (kiểm độc lập giờ Việt Nam); chứng thư DEV hiện có được tái sử dụng.

### Coverage cuối: test bổ sung

| ID | Hành vi / expected được kiểm thực tế | Kết quả |
|---|---|---|
| A01 | Tạo đủ8mốc, reload/mở lại persisted; ngày Hội đồng không lệch ngày dù timezone browser khác | PASS |
| A02 | Chỉ3mốc cũ,8field mới null; đọc lại `Chưa lập lịch` | PASS |
| A03 | Khoảng bổ sung ngược:400 và không tạo đợt; sửa đúng mới201 | PASS |
| A04 | Một đầu nullable; ký/công bố chồng hợp lệ chỉ warning, vẫn lưu | PASS |
| A05 | opensAt>deadline, khoảng ký/công bố ngược:400 qua UI; snapshot giữ nguyên; toast trong viewport | PASS |
| B01 | Đợt open khóa cấu hình nghiệp vụ; vẫn sửa lịch; history có reason/actor | PASS |
| B02 | Gia hạn trước hạn; reason bắt buộc; không tự đổi mốc khác | PASS |
| B03 | Hạn nhỏ hơn cũ/quá khứ/vượt reviewDeadline:400, không partial write | PASS |
| B04 | Chuyên viên chủ động chỉnh reviewDeadline rồi gia hạn vượt hạn rà soát cũ | PASS |
| B05 | closed/completed không có nút gia hạn và BE409; không tự mở lại | PASS |
| B06 | Sinh viên upload PDF REAL; nộp trước mở/sau hạn đều409, không tạo hồ sơ | PASS |
| B07 | Chuyên viên gia hạn đợt open đã quá hạn; sinh viên chủ động retry201, chỉ1bản nộp | PASS |
| B08 | Download từ UI trùng SHA-256; SV không thấy lịch nội bộ, direct URL/API403; logout | PASS |
| B09 | Hạn bằng hạn cũ400; reason trắng disabled; API không cho sửa business config open | PASS |
| B10 | Hai tab sửa lịch: tab cũ412, giữ nội dung; đọc lại phản ánh bản đã commit | PASS |
| C01 |4hồ sơ chưa đánh giá:4blocker, UI không cho đóng, BE409 | PASS |
| C02 | UI ghi/xác nhận đạt/không đạt/cần xác minh; quá reviewDeadline vẫn cho đánh giá đúng nghiệp vụ;1eligible/2excluded/1blocker | PASS |
| C03 | Vòng bổ sung open làm tăng blocker; gia hạn vòng vượt lịch mềm; hủy rồi vòng thứ2, không trùng; sau xử lý1eligible/3excluded/0blocker | PASS |
| C04 | Nguồn đổi bởi revision ở tab khác sau preflight: đóng xét412; đọc lại hiện blocker; xác nhận revision mới hợp lệ | PASS |
| C05 | Đóng xét có hồ sơ200; marker actor/time/hash và history persisted; vẫn closed nhận; cần xác minh không đổi thành không đạt | PASS |
| C06 | Sau chốt: revision, kiểm giấy, mở bổ sung thông thường đều409; hồ sơ ở đợt khác vẫn đánh giá được | PASS |
| C07 | Cùng root đã chốt: mở/gia hạn bổ sung ở receiving round khác vẫn được; marker/hash gốc giữ nguyên | PASS |
| D01 | Admin Auth thiếu capability MGHP: direct URL và API bị từ chối | PASS |
| D02 |390px không tràn ngang; timezone/actual rõ; logout; không có uncaught JS error | PASS |

Ca đóng xét đợt rỗng đã có DEV evidence hợp lệ20/11 (`20018`); không chạy lại chỉ để tăng số PASS. Coverage mới tập trung vào đợt có hồ sơ. API đọc/bổ sung kiểm tra phía server trong suite không được tính thay cho thao tác browser.

### Dữ liệu và dịch vụ thật / fixture

- Run chính **`TLX-20261121-76a266`**, học kỳ3, policy version10012 (SYNTHETIC trên M2); các đợt **20022–20029**. Đợt **20027 MIX** có4root **10012–10015**, OTHER có root10016; root thực nộp qua browser **10017**, submission10026, PDF10021 ở WINDOW20029.
- MIX đã đóng xét **13:32:20.647 ngày21/11/2026, giờ Việt Nam**, actor3,1eligible/3excluded/0blocker. UNKNOWN/PENDING giữ `can_xac_minh`; không cấp quyền hưởng từ thao tác Đóng xét.
- C03 tạo/hủy2vòng; C07 tạo/gia hạn/hủy1vòng receiving round khác. Một quyền hưởng có mã `TLX-…-RECEIVING-FIXTURE` chỉ là fixture scope P08 để kiểm receiving guard, **không phải quyền được công nhận bằng QĐ/P10**.
-5PDF của các source fixture là **SQL metadata-only**, ghi cloud `synthetic-no-storage`, có ledger riêng; không gọi download các file này và không nhận là upload Cloudinary thật. Các nguồn được đánh giá/xác nhận bằng service thật qua UI; không stub API nghiệp vụ.
- **Cloudinary REAL**: upload `du lieu test.pdf` qua Spring Boot,1625263bytes; download qua UI có cùng SHA-256 `b9bb871fa524482a1f61d48ba953c0ae5f776326df34292298cf7ce0d2bf5c46`. File10021 đã gắn hồ sơ10017. Hai upload10019/10020 từ lượt sửa script còn unbound; giữ ledger, không tự xóa Cloudinary.
- Trạng thái completed và việc thời gian đã qua được thiết lập bằng **fixture SQL chỉ trên đợt TLX được guard**; không thay server clock hoặc nhận là workflow hoàn tất P12 thật. Lịch sử fixture trước command được phân biệt với audit command thật.
- Run chuẩn bị đầu `TLX-20261121-d94764` để lại3đợt20019–20021, học kỳ2/policy10011; giữ nhận diện, không xóa/ghi đè dữ liệu khác. **Không xóa dữ liệu trong lượt này**; không DROP/RESET/CLEAN/migrate/seed DB evidence cũ.

### Regression, lỗi trung gian và giới hạn

- P05: tạo/mở/khóa cấu hình và các validation cũ; P06: upload/nộp/download/cửa nhận; P07: vòng bổ sung, gia hạn, nhiều vòng và receiving scope; P08: xác nhận/revision/ba kết luận/căn cứ. Đây là targeted UI regression, không tuyên bố đã chạy lại toàn bộ suite P05–P08 hoặc7SQL barrier race cũ.
- Không phát hiện lỗi ứng dụng cần sửa. **35file source/test của baseline mini trước lượt này đều cùng SHA-256**; executable DEV vẫn khớp build-source pointer. Không sửa backend/frontend production code, contract, Entity hoặc migration. Không build/migrate hoặc chạy lại suite backend chỉ để tăng PASS.
- Run chính giữ6failed script attempts: chờ list trước khi data về; selector label/select hoặc hai form trùng label; toast lỗi che nút khi retry. Đã sửa test để chờ đúng trạng thái, scope đúng form và **bấm×đóng toast như người dùng**; không `force click`/bypass validation. Các ca tương ứng đã chạy lại PASS. Lượt chuẩn bị đầu còn lỗi TLS của Node API client, refresh bị hủy vì điều hướng quá sớm và sai tên field actor trong assertion; không nhận là lỗi sản phẩm. Node dùng đúng DEV leaf làm extra CA, không tắt xác minh TLS.
- `429` đăng nhập được xử lý đúng Retry-After; LoginPage xóa password sau submit nên retry nhập lại từ cấu hình local. Không restart BE để xóa limiter hoặc giảm bảo vệ Auth.
- Toast cố định có thể che vùng nút ở cuối trang; người dùng có thể đóng bằng×để thao tác tiếp. Chưa thay đổi shared Feedback ngoài scope. Preflight hiện chỉ có counts và lý do blocker theo contract; lý do eligible/excluded được kiểm trên màn hình chi tiết đánh giá, không tự thêm DTO/API.
- Hash nhóm dữ liệu MGHP **ngoài TLX** và sinh viên DEV giữ nguyên trước/sau. Bảng biên bản/quyết định/văn bản/phiên bản văn bản đều0 trước/sau, không phát sinh P10. Đã kiểm history liên kết audit đúng actor/reason.

### Lệnh và evidence

Chạy tại root `ctsv`, PowerShell7 (các script test không thay launcher hằng ngày):

```powershell
pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 rounds -EvidenceDirectory target/pre-p10-extended-20261121/run-02
pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 sources -EvidenceDirectory target/pre-p10-extended-20261121/run-02
pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 student -EvidenceDirectory target/pre-p10-extended-20261121/run-02
pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 additional -EvidenceDirectory target/pre-p10-extended-20261121/run-02
pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 security-admin -EvidenceDirectory target/pre-p10-extended-20261121/admin
```

Runner đọc accounts từ cấu hình DPAPI hiện có ngoài Git, không in password/token. Fixture tool guard cứng instance/owner/marker/ctsv_dev/V17; không bỏ guard DB evidence. Suite giữ fixture và những ca đã PASS để tiếp tục ca chưa đạt; không xóa/chạy lại toàn suite ngầm. Muốn tạo một bộ mới dùng EvidenceDirectory mới; muốn chỉ kiểm Admin trên cùng bộ thì copy đúng `fixture.json` (không có credentials) vào thư mục evidence Admin trước khi chạy, như lượt này. Không copy cookie/token.

- [Manifest tổng hợp](../../nghiepvu/.analysis/runtime/pre-p10/20261121/manifest.json).
- [Coverage từng ca](../target/pre-p10-extended-20261121/coverage.md), [report chính](../target/pre-p10-extended-20261121/run-02/report.json), [Admin](../target/pre-p10-extended-20261121/admin/report.json).
- [Snapshot SQL](../target/pre-p10-extended-20261121/run-02/sql-snapshot.json), [hash bảo vệ trước](../target/pre-p10-extended-20261121/run-02/protected-before.json)/[sau](../target/pre-p10-extended-20261121/run-02/protected-after.json), [35source baseline](../target/pre-p10-extended-20261121/run-02/baseline-source-comparison.json).
- Ảnh `A01…D02.png`; các file `*-sanitized-trace.zip` tại thư mục report. [Quét trace](../target/pre-p10-extended-20261121/run-02/trace-scan.json):12ZIP/2175entries,0JWT chưa che,0uncaught pageerror, cookie object vẫn đúng cấu trúc. Raw trace chứa auth bị thay bằng bản đã lọc; không đưa raw hoặc runtime local lên Git.

**Kết luận:** E2E mở rộng đạt; launcher vẫn chạy BE/FE trên ctsv_dev V17. Không còn blocker trong phạm vi đã kiểm. Không tự bắt đầu P10, không commit/push.

## File thay đổi trong lượt này

| File | Mục đích |
|---|---|
| `frontend/e2e/timeline-extended-dev.mjs` |24ca chức năng browser và supplemental API assertions |
| `frontend/e2e/timeline-dev-db.ps1` | Guard đúng DEV và chuẩn bị/đọc fixture có prefix; không reset schema |
| `frontend/e2e/run-timeline-extended-dev.ps1` | Đọc credentials DPAPI và CA leaf local, không đưa secret vào command/log |
| `frontend/e2e/sanitize-timeline-trace.mjs` | Lọc token/cookie/secret, giữ cấu trúc trace; thay raw trace owned |
| `frontend/e2e/collect-timeline-dev-evidence.mjs` | Đối chiếu24case thực chạy, hash dữ liệu/source/audit/PDF và tổng hợp manifest |
| `docs/pre-p10-timeline-extended-e2e.md` | Plan/coverage trước và báo cáo thực thi sau |
| `nghiepvu/09-trien-khai/pre-p10-timeline-implementation-plan.md` §11.4 | Gắn kết quả bổ sung vào plan hiện có |
| `nghiepvu/09-trien-khai/master-implementation-backlog.md`, `roadmap.md` | Bổ sung evidence; không đổi ID/dependency/nghiệm thu hoặc mở P10 |
| `nghiepvu/.analysis/runtime/pre-p10/20261121/manifest.json` | Manifest bổ sung; giữ nguyên evidence20/11 |

Các thay đổi source PRE-P10 đã tồn tại trước lượt này vẫn được bảo toàn, không nhận nhầm là code vừa sửa trong task E2E. Evidence nằm trong `target/` bị ignore; chưa stage/commit/push bất kỳ file nào.
