# CA DUT adapter — P09 và bàn giao P11

Adapter `pbl6.ctsv.shared.ca` nằm trong CTSV Spring Boot; không API/controller, database, migration, worker hoặc UI CA mới. Nguồn phạm vi/evidence: [P09 Detailed Plan](../../nghiepvu/09-trien-khai/ca-dut-spike-plan.md). Không dùng token Auth CTSV thay token API Client CA.

## Cấu hình local một lần

Backend chỉ tạo bean khi `ca-dut.enabled=true`. Env server-only: `CA_DUT_URL` (origin HTTPS, mặc định `https://chukyso.dut.udn.vn`), `CA_DUT_CLIENT_ID`, `CA_DUT_CLIENT_SECRET`. Không prefix `/api/v1` trong origin. Không VITE_*, không bỏ xác minh TLS.

Launcher spike nạp credential từ `%LOCALAPPDATA%\CTSV\ca-dut\credential.clixml` vào env của tiến trình và phục hồi env khi kết thúc. File được Windows DPAPI mã hóa, chỉ dùng được bởi tài khoản Windows/máy đã tạo. Máy tác giả đã được cấu hình theo quyết định owner; không cần nhập lại mỗi lần. Không copy file này cho thành viên khác.

Máy khác được CA cấp quyền riêng có thể tạo file bằng PowerShell, không đặt secret trong câu lệnh hoặc transcript:

```powershell
$caDir = Join-Path $env:LOCALAPPDATA 'CTSV/ca-dut'
New-Item -ItemType Directory -Force -Path $caDir | Out-Null
$caAcl = New-Object System.Security.AccessControl.DirectorySecurity
$caAcl.SetAccessRuleProtection($true,$false)
$caIdentity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$caRule = New-Object System.Security.AccessControl.FileSystemAccessRule($caIdentity,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
$caAcl.AddAccessRule($caRule)
Set-Acl -LiteralPath $caDir -AclObject $caAcl
$caCredential = Get-Credential -Message 'Nhập API Client ID ở User name và client secret ở Password'
$caCredential | Export-Clixml -LiteralPath (Join-Path $caDir 'credential.clixml')
$caCredential = $null
```

Không ghi credentials, token, raw auth response hoặc signed URL vào log/Git. Env Process đã có đủ ID/secret được ưu tiên; không ghép ID của nguồn này với secret của nguồn khác. Tài khoản DEV synthetic đăng nhập CTSV không phải credential CA.

## Chạy từ thư mục CTSV

```powershell
# Chỉ auth và GET danh mục/chứng thư/mẫu của hai danh tính TEST; không ký.
.\tools\ca\Invoke-CaSpike.ps1 -Mode ReadOnly

# Có tác động thật: chỉ dùng trong đúng phép upload/ký E một lần của owner.
# Guard toàn cục chống gửi lại; máy tác giả đã dùng lượt này.
.\tools\ca\Invoke-CaSpike.ps1 -Mode SignEOnce

# Chỉ GET lại document đã có receipt; không upload hoặc ký lại.
.\tools\ca\Invoke-CaSpike.ps1 -Mode VerifyDownload
```

PowerShell5.1/7; Java21/Maven wrapper hiện có. Không SQL, không chạy dev.ps1/migration. `ReadOnly` không phải nghiệm thu ký; `VerifyDownload` cũng không chứng minh căn cứ gửi ký, cert binding hay chuỗi hai người. Không xóa guard `p09-sign-e-once.intent.json`, đổi RunId hoặc xóa event để gửi lại. Nếu timeout/unknown thì đối soát với owner/CA, không retry POST. File lock chặn hai spike cùng chạy; không thay durable fencing/ledger của P11.

Input mặc định đóng gói tại src/test/resources/ca/file-ky-thu.pdf, byte-for-byte từ PDF TEST của owner (có thể đặt CA_DUT_TEST_PDF nhưng hash vẫn phải đúng phép), SHA256 `d8d6256816ad520de5fbcbe14359d14c163f503ab93cdbe62234a0ba06798f38`,32124bytes,1trang612×792pt,rotation0, có1chữ ký sẵn. Dùng khung E page1 x100 y100 w200 h60; khung C thử bằng stub y200. Không sửa/re-save PDF nguồn để làm test xanh. Trần CTSV10.000.000bytes; response/header/body có timeout và giới hạn.

Các UUID cert/template được chọn từ CA06/07 đúng email; nếu có nhiều cert sẵn sàng thì cần `CA_DUT_SIGNER_E_CERTIFICATE_ID`/`CA_DUT_SIGNER_C_CERTIFICATE_ID`. Optional template cùng owner: `CA_DUT_SIGNER_E_TEMPLATE_ID`/`CA_DUT_SIGNER_C_TEMPLATE_ID`. Không chọn phần tử đầu hoặc dùng mẫu E cho C. Cert không có mẫu là hợp lệ; có thể dùng mặc định riêng của người đó.

CA serial_number là mã provider, có thể khác serial X.509; kể cả chuỗi trông như hex cũng không chứng minh đúng issuer/chứng thư. Cần pin fingerprint SHA256 chứng thư DER từ nguồn được owner/provider xác minh độc lập, không tự tin cậy fingerprint chỉ vì vừa tải thấy nó. Adapter có chỗ nhận fingerprint trong `CaSignerBinding.Signer`; entry spike nhận `CA_DUT_SIGNER_E_CERTIFICATE_SHA256`/`CA_DUT_SIGNER_C_CERTIFICATE_SHA256`. Thiếu liên kết exact cert phải ghi `CA_CERTIFICATE_LINK_NOT_VERIFIED` và chặn bước tiếp theo. Không tự issue/renew để giải quyết.

## Kết quả và giới hạn

Evidence riêng tại `%LOCALAPPDATA%\CTSV\ca-dut\p09-20261119\`: input/geometry, binding provider, intent/event trước POST, receipt đã whitelist, PDF, result từng tầng. Log/report đã lọc trong `target/p09-*.log`, `target/surefire-reports`, `target/failsafe-reports`. Tất cả runtime material giữ ngoài Git. Không gửi raw private directory hoặc auth/env dump.

- `CA_REPORTED_SUCCESS`: receipt đúng document/cert/signer/input.
- `PDF_RETRIEVED`: CA10 thực sự trả PDF đầy đủ, parse được.
- `HASH_VERIFIED`: SHA256 bytes khớp signed_pdf_hash receipt.
- `CRYPTO_VERIFIED`: CMS/digest/ByteRange của các chữ ký đã kiểm; chữ ký cũ được bảo toàn và revision cuối phủ toàn file.
- Trust chain, thời gian tin cậy, revocation và liên kết certificate UUID→chứng thư DER là các kiểm chứng riêng. Không suy hiệu lực pháp lý từ4cờ trên.

Lượt19/11: CA10 tải được PDF sau ký E, không tái hiện lỗi storage qua route này. Không kiểm hoặc follow `signed_file_url` tùy ý. Chưa ký C thật. Xem actual mới nhất ở Detailed Plan; không dùng trạng thái JUnit stub để kết luận CA thật PASS.

## Hợp đồng bàn giao P11

- `CaDutClient`: CA01/02/03/06/07/08/09/10, typed DTO/failure; token server-only single-flight; không API issue/renew/delete. GET401 refresh một lần; POST không auto retry. 5xx/timeout/receipt không xác định giữ unknown.
- `CaSignerBinding`: đúng email từ CA06 filter, CA user, UUID, serial provider, validity/key/template. Không tự cấp capability hoặc chứng minh chức trách thật.
- `CaSigningSequence`: input→upload→ký→tải→parser/hash/CMS→bước tiếp. `Journal.record` phải bền **trước** POST, receipt/artifact phải lưu trước chuyển bước; `ConsentProvider` gắn hash,step,certificate,email và căn cứ. P11 cung cấp ledger/fencing/idempotency/worker và chính sách trust, không dùng journal local thay SQL.
- **CA-R16**: P09 kiểm identity kỹ thuật; P11 kiểm quyền KY, căn cứ C08/C09, phiên bản kế hoạch và đúng chức trách.
- **CA-R17**: P09 không nhận JSON/truncated/hash sai làm PDF, không retry POST; P11 khôi phục durable unknown/receipt và chặn công bố. Không tự xóa checkpoint/lịch sử để retry.
- **TEST aliases do owner cho phép**: BB=CV(E)→PHT(C), QĐ=PP(E)→PHT(C). E=`officer.e@dut.udn.vn`, C=`staff.c@dut.udn.vn`. E và C vẫn là hai người/chứng thư khác nhau; tên/email gửi CA là chủ chứng thư thực tế. Không áp dụng alias này làm role Auth hoặc mapping lãnh đạo production.
- Chưa chạy chuỗi hai người CA thật trong lượt này; P11-99/P12 vẫn cần đủ evidence thật/policy. G8 waived không thay các gate kỹ thuật.

Kiểm local/stub (không gọi CA hoặc SQL):

```powershell
.\mvnw.cmd -o -B -ntp '-Dtest=CaPdfPreflightTest,CaPdfSignatureVerifierTest,CaDutClientTest,CaDutConfigurationTest,CaTokenManagerTest,CaSignerBindingTest,CaSigningSequenceTest' test
```

Kết quả kỹ thuật cuối19/11: P09 56/56 PASS; regression PDF/Auth27/27 PASS. Starter contextLoads riêng ERROR vì thiếu JdbcTemplate ở default profile; không sửa/disable trong P09. CA REAL một E đạt4mức integrity, workflow còn PARTIAL vì exact DER pin/trust chưa xác minh. CA08 category/current_version không được capture ở lượt thật; kiểm parser cuối qua STUB, không POST lại. [Manifest đã lọc](../../nghiepvu/.analysis/runtime/p09/20261119/p09-manifest.json).

CA06 thực tế đã được kiểm lại theo đúng hai URL owner cung cấp: mỗi email1cert active,14field metadata như tài liệu v1; không trả PEM/DER/fingerprint. UUID/binding provider đã có, exact public DER certificate material vẫn cần nguồn CA xác nhận riêng. Không tự sáng tạo endpoint hoặc ký thêm để giải quyết.
