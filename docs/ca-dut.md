# CA DUT adapter — P09 và bàn giao P11

> **Quyết định cuối owner23/11 — hiện hành:** bỏ yêu cầu external certificate/fingerprint pin và bỏ trust khỏi điều kiện ký/nghiệm thu. Giữ CA06 `status=active`, validity/key/owner, hai signer riêng, quyền/consent, receipt/hash/PDF/CMS và lịch sử. Các gate pin/trust trong ledger cũ là lịch sử đã được quyết định này thay thế. Chuỗi A→B REAL đã đạt; [evidence hiện hành](../../nghiepvu/09-trien-khai/ca-dut-spike-plan.md#p09-two-signer-20261123). Chưa triển khai P11.


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

Các UUID cert được chọn từ CA06 đúng email; nếu có nhiều cert sẵn sàng thì cần `CA_DUT_SIGNER_E_CERTIFICATE_ID`/`CA_DUT_SIGNER_C_CERTIFICATE_ID`. Không chọn phần tử đầu hoặc chứng thư người khác. Không gửi user_signature_id/ảnh base64 và không cấu hình TEMPLATE_ID; CA DUT dùng mặc định riêng của người ký.

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
## Owner23/11 — ký trên CTSV và CA06 active

P11 dùng giao diện CTSV cho từng người tự bấm **Ký số**; CV chuẩn bị/điều phối/thu PDF nhưng không ký thay PP/PHT. BB CV→PHT đủ rồi QĐ PP→PHT; chỉ sau tải/kiểm/lưu output bước trước, người bước sau mới tự bấm. Adapter P09 là backend HTTP client, không UI CA/redirect, không worker tự ký cả bộ hoặc callback giả. [Contract hiện hành](../../nghiepvu/01-thiet-ke/ctsv/api-contracts/signing-api.md).

CA06 trong `CaDutClient.certificates` đã luôn gửi user_email/status=active; owner nay chốt đây là query bắt buộc cho consumer CTSV. Vẫn kiểm validity/key/owner/template; không đổi provider source v1 hoặc thêm cấu hình cho phép status bất kỳ.

Hai biến Windows **User** đã lưu ngoài Git từ CA06 REAL23/11: `CA_DUT_SIGNER_E_CERTIFICATE_ID`, `CA_DUT_SIGNER_C_CERTIFICATE_ID`. Chúng là UUID public metadata, không client secret/fingerprint. App/shell đã mở trước khi cấu hình cần khởi động lại hoặc nạp đúng User env khi chạy spike:

```powershell
$env:CA_DUT_SIGNER_E_CERTIFICATE_ID = [Environment]::GetEnvironmentVariable('CA_DUT_SIGNER_E_CERTIFICATE_ID','User')
$env:CA_DUT_SIGNER_C_CERTIFICATE_ID = [Environment]::GetEnvironmentVariable('CA_DUT_SIGNER_C_CERTIFICATE_ID','User')
```

Không in giá trị hoặc đổi `*_CERTIFICATE_SHA256` thành UUID. Credential API Client tiếp tục DPAPI→Process env trong runner rồi khôi phục; không cần nhập lại, không đưa vào frontend/Git.

Retest23/11: ReadOnly IT1PASS, VerifyDownload IT1PASS và refresh rotation REAL200/old401. CA10 tải lại đúng E PDF47.618bytes/hash/CMS đạt;6nhóm route CA01/02/03/06/07/10 đã được kiểm theo phạm vi. Không upload/sign mới; không rerun SignEOnce guard19/11 hoặc phát C khi thiếu exact certificate/trust. ID E/C đã có; public DER/fingerprint/trust material chưa có trong CA06. Không tự tạo API cấp/gia hạn/xóa hoặc nới validator để gọi đủ số.

[Evidence/giới hạn23/11](../../nghiepvu/09-trien-khai/ca-dut-spike-plan.md#p09-retest-20261123). P09-04/99/P11-99/P12 vẫn GATED CA. Không claim OTP/PIN/consent production đã được xác minh hoặc P11 đã triển khai từ kết quả API Client TEST.
## Kết quả cuối23/11 — policy hiện hành và A→B thật

Theo quyết định cuối owner, không cấu hình/bắt cung cấp external public certificate/fingerprint pin hoặc kiểm trust để chuyển bước. Code P09 CaSigningSequence bỏ gate CA_CERTIFICATE_LINK_NOT_VERIFIED; CaSignerBinding distinct dùng email/CA user/cert UUID/serial, không dedup pin legacy. Giữ public interface để consumer không bị vỡ; không code P11.

P09 đã kiểm REAL A201→signed_file_url tải200/hash/CMS→B201 trên đúng output A→signed_file_url tải200/hash/CMS/bảo toàn chữ ký.1upload/2sign, file cuối63.279bytes; typed CaDutClient đọc lại cùng hash.26targeted tests PASS. Lượt lỗi allowlist sau A chỉ tải lại từ receipt, không ký lại; URL Viettel S3 dùng HTTPS, không chuyển Bearer CA sang storage.

Probe chỉ chạy trên file ký thử.pdf TEST đã giao; intent/checkpoint/receipt/PDF nằm ngoài Git tại `%LOCALAPPDATA%/CTSV/ca-dut/p09-20261123-two-signer`. Không chạy SignEOnce lịch sử để replay; nếu checkpoint unknown thì dừng/đối soát. Signed URL trả cho owner chỉ là bàn giao TEST theo yêu cầu, không thay quyền file API trong ứng dụng chính. [Evidence và source mapping](../../nghiepvu/09-trien-khai/ca-dut-spike-plan.md#p09-two-signer-20261123).

## Owner23/11 — payload không chỉ định mẫu chữ ký

CaDutClient/CaSigningSequence và các script ký bỏ user_signature_id. Ký không phụ thuộc CA07; API lookup mẫu vẫn giữ để dùng độc lập nếu cần. Runner không đọc CA_DUT_SIGNER_E_TEMPLATE_ID/CA_DUT_SIGNER_C_TEMPLATE_ID. Không xóa receipt/selection/PDF của lượt REAL trước vốn đã gửi mẫu; không ký lại chỉ để sửa evidence. Delta hiện tại kiểm HTTP STUB, REAL payload mới chưa chạy. Không thay quyền/cert active/consent/thứ tự/hash/CMS.

## P11 execution23/11

Reuse CaDutClient qua SigningProvider, không viết lại adapter. `tools/ca/Invoke-P11Real.ps1` chỉ chạy đúng method REAL với DB P11 guard, secrets DPAPI ngoài Git, intent file ngoài Git ngăn vô tình chạy lại. Có intent thì dừng và đối chiếu DB/receipt; không xóa intent hoặc ký lại để xử lý lỗi. Hai output CTSV riêng đã được kiểm qua Cloudinary/TEP02.

Owner mới nhất bỏ toàn bộ Từ chối ký PP/PHT trong P11 và case sửa dữ liệu đã chốt. Không phục hồi nút/API này từ tài liệu cũ. Hash/CMS/metadata binding/status active giữ nguyên; external pin/trust và user_signature_id không là gate/payload.
