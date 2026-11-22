# P1 usability — contract delta, owner22/11/2026

Phạm vi được owner giao: thực hiện nhóm P1 của audit ba vai trò, bao gồm các read API còn thiếu. Đây là đặc tả nguồn của delta dưới đây; không thay command, capability hoặc nghiệp vụ đã duyệt. IMPLEMENTATION/verification được theo dõi tại [Detailed Plan](superpowers/plans/2026-11-22-p1-usability.md).

## Quy ước

GET không body; response no-store; JWT và kiểm quyền hiện hành. Query thừa/trùng/sai dạng trả400 VALIDATION_ERROR. `q` tùy chọn trim, tối đa128ký tự, tìm literal substring (escape ký tự LIKE); page0 mặc định, size20 mặc định/1–100; sort ổn định; count cùng filter trước phân trang. Không nhận owner/studentId do client chỉ định cho nguồn chính mình. Không trả password/hash/token/CCCD/contact ngoài projection dưới đây.

| ID | Method/URL | Quyền hiện hành | Query | Response |
|---|---|---|---|---|
| AUTH08-L | GET /api/xac-thuc/tai-khoan | auth.accounts.manage; account/tokenVersion còn hiệu lực | q,page,size | Page<AccountSummary> |
| FND07-LU | GET /api/ctsv/nen-tang/nguoi-dung | ctsv.roles.manage + binding CTSV + Auth fresh | q,page,size | Page<UserChoice> |
| FND07-LR | GET /api/ctsv/nen-tang/vai-tro-mghp | như FND07-LU | không | RoleChoice[] |
| FND-LINK-L | GET /api/ctsv/nen-tang/ho-so-nguon | auth.accounts.manage fresh qua public Auth boundary; không tự yêu cầu quyền MGHP | profileType=student/staff bắt buộc,q,page,size | Page<ProfileChoice> |
| QH01-L | GET /api/ctsv/mghp/toi/quyen-huong | SV có identity mapping; ownership server giống QH01 | page,size | Page<OwnBenefitSummary> |
| QH01-C | GET /api/ctsv/mghp/quyen-huong/{id}/ngu-canh-xet-duyet | chuyên viên MGHP + Auth fresh | không | BenefitReviewContext |

- `Page<T>`: items T[],page integer,size integer,totalElements integer không âm.
- AccountSummary: accountId UUID,username Text128,active boolean. Sort normalized username rồi UUID. Chi tiết, link và role lấy AUTH08-R hiện có; không ETag dùng ghi ở summary.
- UserChoice: id Id,name Text512 nullable,code Text254 (email nội bộ để phân biệt người trùng tên). Chỉ user active/chưa xóa; sort name,id. Email ở đây chỉ để chọn đối tượng phân quyền của admin CTSV, không phục vụ SV.
- RoleChoice: id Id,code Text128,name Text512 nullable,active boolean. Chỉ role MGHP thuộc allowlist grant hiện hành; không mở thêm loại role hoặc quyền cấp.
- ProfileChoice: sourceSystem Text128,profileType enum student/staff,sourceId Text128,code Text128 nullable,name Text512 nullable. Lấy từ danh bạ nguồn hiện có, đúng external ID và source đã đăng ký trong Auth; không dùng PK CTSV/MSSV/email thay sourceId. Admin phải xác minh đúng người trước khi gửi ProfileLink theo command cũ; directory không tự cấp link. Chỉ dữ liệu có source ID/hệ thống nguồn hợp lệ, không trả CCCD/contact. Sort code,sourceId,sourceSystem. Không truy cập repository Auth từ Foundation; public AuthDirectoryAccess kiểm capability và trả trusted source allowlist.
- OwnBenefitSummary: id Id,code Text128,policyCode/name nullable,rate decimal string nullable,recognizedFrom/recognizedUntil/revokedOn Date nullable,status Text128 nullable. Cùng tập quyền mà QH01 cho chính SV đọc; không tạo quyền/công bố mới, không trả reviewReason/event nội bộ/decisionLineId. Sort id giảm. QH01 detail vẫn quyết định ownership hiện hành.
- BenefitReviewContext: application ApplicationDetail của chain nguồn, documents là giấy của các bản trong chain có submissionId, sourceVersionId Id và updateSubmissions {id,revision,policyVersionId,submittedAt}[] chỉ cập nhật liên kết đúng benefit. Thiếu chain nguồn409 BENEFIT_UPDATE_CONFLICT; SV/Admin Auth thiếu MGHP403. Không tạo bản nộp mới hoặc tự chọn bản cập nhật. Khi tạo đánh giá, backend vẫn kiểm chain/semester/policy của XET03.
- BenefitReviewContext.evaluations gồm {id,revision,status,result,semesterId,policyVersionId}[] của đúng benefit, sort revision/id giảm, phục vụ chọn lịch sử thay nhập ID. Chỉ projection CV giống XET01, không có mutation mới.

## Read projection bổ sung tương thích

- FND03 thêm query `ids` tùy chọn (1–100 Id dương phân cách dấu phẩy, không đi cùng q/page/size); trả Page<LookupItem> của đúng các ID hiện có, sort ID và count đúng. Dùng để giải tên các giá trị đối chiếu/đã chọn, không tải toàn danh mục hoặc suy tên lịch sử từ ID. Quyền và whitelist kind giữ nguyên FND03. Tên hiện tại chỉ là nhãn trợ giúp, giá trị nguồn quan sát gốc vẫn giữ nguyên.
- HS01 form context `studyProcesses` có thể thêm `programName`, `cohortCode`, `verificationStatus` từ nguồn hiện có; thiếu thì null, giữ code/ID gốc; không tự đặt quá trình mặc định.
- FND student summary thêm tên/mã lớp và tên đơn vị, giữ các ID hiện có.
- ApplicationDetail.supplementRequests thêm `general` boolean để UI phân biệt phản hồi chung được HS08 hoàn tất nguyên tử với typed request cần xử lý từng mục; chỉ projection, không đổi điều kiện command.
- VH thông báo tiếp nhận/kết luận liên kết `/mghp/ho-so?ho-so={applicationId}`; yêu cầu bổ sung giữ `/mghp/ho-so?bo-sung={id}`. Server chỉ tạo link từ object ID đã kiểm recipient/ownership; FE chỉ nhận hai dạng allowlist này. Legacy `/mghp` không được điều hướng như một route hợp lệ.
- Không sửa representation/ETag của command; detail có thêm field phải tính lại ETag theo chính body bằng cơ chế hiện hành.

## Acceptance / tests bắt buộc

1. Admin tìm theo literal `%`, `_`, dấu nháy, tên trùng và qua trang; không trả secret; SV/CV thiếu Auth capability403.
2. User/role directory không được đọc bằng SV hoặc admin thiếu capability tương ứng; role list không làm mở quyền grant mới.
3. Profile directory đúng source ID, không suy identity từ email; source không trusted bị loại; thiếu/sai query400.
4. Own list count/page chỉ student hiện hành; không cho truyền studentId khác; summary không có field nội bộ; detail người khác404 như QH01.
5. Notification link và deep link đi đúng object, giữ backend ownership, không render URL tùy ý.
6. Target đổi/response đến muộn không hiện/sửa nhầm body hoặc dùng ETag trước; không thay mutation/security hiện hành.
