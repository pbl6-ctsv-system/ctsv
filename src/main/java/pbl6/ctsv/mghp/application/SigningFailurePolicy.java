package pbl6.ctsv.mghp.application;

import java.util.*;
import pbl6.ctsv.shared.ca.CaFailure;

/** Fixed diagnostic vocabulary; arbitrary exception/provider text never enters incident/public DTOs. */
final class SigningFailurePolicy {
 private SigningFailurePolicy(){}
 static final String SUPPORT="Yêu cầu ký đang được bộ phận quản trị xử lý. Bạn chưa cần ký lại.";
 record Result(String code,String publicMessage,String adminMessage,String stage,String apiMethod,String apiPath,int httpStatus,boolean userCorrectable,boolean uncertain){}
 private static final Set<String> SAFE=Set.of(
  "CA_STORAGE_NO_SUCH_KEY","CA_PROVIDER_REJECTED","CA_AUTH_REJECTED","CA_TIMEOUT","CA_TRANSPORT_FAILURE","CA_INTERRUPTED",
  "CA_CATEGORY_INVALID","CA_CERTIFICATE_INVALID","CA_CERTIFICATE_SELECTION_REQUIRED","CA_CERTIFICATE_NOT_READY","CA_SIGNERS_NOT_DISTINCT",
  "CA_INPUT_HASH_MISMATCH","CA_UPLOAD_HASH_MISMATCH","CA_UPLOAD_MISMATCH","CA_UPLOAD_RECEIPT_MISMATCH","CA_RECEIPT_MISMATCH",
  "CA_SIGNED_HASH_MISMATCH","CA_REVISION_MISMATCH","CA_CRYPTO_NOT_VERIFIED","CA_SCHEMA_INVALID","CA_RESPONSE_LIMIT","CA_PAGINATION_INVALID",
  "CA_DOCUMENT_NOT_FOUND","CA_DOWNLOAD_UNAVAILABLE","CA_NOT_CONFIGURED","CA_CONFIG_MISSING","CA_SIGN_INVALID",
  "CA_FILE_TOO_LARGE","CA_PDF_INVALID","CA_PDF_PASSWORD_PROTECTED","CA_PDF_MISSING",
  "PDF_TOO_LARGE","FILE_TOO_LARGE","STORAGE_UNAVAILABLE","FILE_INTEGRITY_FAILED","FILE_STORAGE_UNAVAILABLE","FILE_OPERATION_CONFLICT","FILE_UPLOAD_PENDING","CA_GEOMETRY_INVALID","CA_RENEW_AMBIGUOUS","CA_RENEW_PENDING","CA_IDENTITY_INVALID",
  "SIGNING_AUTHORIZATION_REVOKED","SIGNING_RESULT_UNKNOWN","SIGNING_SPOOL_UNAVAILABLE","SIGNING_SPOOL_CONTENT_CONFLICT",
  "SIGNING_SPOOL_CONTEXT_CONFLICT","SIGNING_SPOOL_MANIFEST_INVALID","SIGNING_SPOOL_CONTENT_INVALID","SIGNING_SPOOL_NOT_FOUND","SIGNING_SPOOL_DELETED","SIGNING_SPOOL_RECEIPT_CONFLICT",
  "SIGNING_DATABASE_UNAVAILABLE","CA_RECEIPT_REQUIRES_MANUAL_LINK","SIGNING_LEASE_LOST","SIGNING_INTERNAL_ERROR");
 static Result classify(String phase,RuntimeException failure){
  String candidate=failure instanceof CaFailure f?f.code():failure instanceof org.springframework.dao.DataAccessException||failure instanceof org.springframework.transaction.TransactionException?"SIGNING_DATABASE_UNAVAILABLE":failure.getMessage();
  String code=candidate!=null&&SAFE.contains(candidate)?candidate:"SIGNING_INTERNAL_ERROR";
  int http=failure instanceof CaFailure f?f.httpStatus():failure instanceof pbl6.ctsv.shared.files.FileProblem f?f.status():0;
  boolean uncertain=Set.of("upload_intent","sign_intent").contains(phase)&&(!(failure instanceof CaFailure f)||f.uncertain());
  String stage=switch(phase){case "queued","certificate"->"certificate";case "upload_intent","uploaded","upload"->"upload";case "sign_intent","sign"->"sign";case "download","storage","database","local_storage"->phase;default->"internal";};
  String method=switch(stage){case "certificate","download"->"GET";case "upload","sign","storage"->"POST";default->"INTERNAL";};
  String path=switch(stage){case "certificate"->"/api/v1/certificates/?user_email={configuredEmail}&status=active";case "upload"->"/api/v1/documents/";case "sign"->"/api/v1/signatures/sign/";case "download"->"/api/v1/documents/{documentId}/view-file/";case "storage"->"Cloudinary /raw/upload + liên kết tệp CTSV";case "database"->"Ghi kết quả SQL";case "local_storage"->"Lưu kết quả trên ổ đĩa backend";default->"Xử lý nội bộ CTSV";};
  boolean user=http==400&&Set.of("CA_FILE_TOO_LARGE","CA_PDF_INVALID","CA_PDF_PASSWORD_PROTECTED","CA_PDF_MISSING").contains(code);
  String detail=switch(code){
   case "CA_STORAGE_NO_SUCH_KEY"->"CA đã báo ký thành công nhưng chưa tải được PDF kết quả (NoSuchKey). Giữ biên nhận để liên hệ CA.";
   case "CA_FILE_TOO_LARGE","PDF_TOO_LARGE","FILE_TOO_LARGE"->"Tệp PDF quá lớn. Vui lòng chọn tệp nhỏ hơn.";
   case "CA_PDF_INVALID"->"Tệp không phải PDF hợp lệ. Vui lòng kiểm tra và chọn lại tệp.";
   case "CA_PDF_PASSWORD_PROTECTED"->"PDF đang được bảo vệ bằng mật khẩu. Vui lòng chuẩn bị PDF cho phép ký.";
   case "CA_PDF_MISSING"->"Chưa có tệp PDF để xử lý. Vui lòng kiểm tra tài liệu.";
   case "CA_CATEGORY_INVALID"->"CA không chấp nhận danh mục do CTSV gửi. Admin cần xem cấu hình tích hợp.";
   case "CA_CERTIFICATE_INVALID","CA_CERTIFICATE_SELECTION_REQUIRED","CA_CERTIFICATE_NOT_READY"->"Chưa xác định được duy nhất chứng thư còn hiệu lực, sẵn sàng của đúng người ký.";
   case "CA_INPUT_HASH_MISMATCH","CA_SIGNED_HASH_MISMATCH","FILE_INTEGRITY_FAILED"->"Kiểm tra toàn vẹn PDF không đạt. Giữ dữ liệu để Admin đối chiếu, không chuyển bước.";
   case "CA_CRYPTO_NOT_VERIFIED","CA_REVISION_MISMATCH"->"Chưa kiểm chứng được chữ ký hoặc chuỗi phiên bản PDF kết quả. Không chuyển bước.";
   case "CA_TIMEOUT","CA_TRANSPORT_FAILURE","SIGNING_RESULT_UNKNOWN"->"Kết nối chưa cung cấp đủ kết quả. Cần kiểm tra bằng chứng đã có trước khi thực hiện tiếp.";
   case "CA_AUTH_REJECTED","CA_CONFIG_MISSING","CA_NOT_CONFIGURED"->"Chưa sử dụng được cấu hình hoặc quyền kết nối CA. Admin cần kiểm tra cấu hình.";
   case "SIGNING_SPOOL_UNAVAILABLE"->"Không lưu được kết quả trên ổ đĩa backend. Admin cần kiểm tra ổ đĩa và quyền ghi.";
   case "STORAGE_UNAVAILABLE","FILE_STORAGE_UNAVAILABLE"->"Chưa đọc hoặc lưu được PDF tại kho tệp CTSV. Admin kiểm tra kết nối Cloudinary và đối chiếu tệp đang giữ trên server.";
   case "CA_GEOMETRY_INVALID"->"CA không chấp nhận vùng ký CTSV gửi. Admin đối chiếu trang, tọa độ và kích thước.";
   case "CA_RENEW_AMBIGUOUS","CA_RENEW_PENDING"->"CA đang có đề nghị gia hạn cần đối chiếu. Không tự gửi thêm yêu cầu.";
   case "SIGNING_DATABASE_UNAVAILABLE"->"Không đọc hoặc ghi được dữ liệu SQL ở bước đang xử lý. Admin cần đối chiếu bằng chứng trên server và trạng thái SQL.";
   case "CA_RECEIPT_REQUIRES_MANUAL_LINK"->"Server đã giữ biên nhận ký nhưng SQL chưa có liên kết tương ứng. Admin cần đối chiếu dữ liệu, không gửi ký lại.";
   default->"Thao tác chưa hoàn tất tại bước được ghi nhận. Admin cần xem API, mã phản hồi và bằng chứng liên quan.";
  };
  return new Result(code,user?detail:SUPPORT,detail,stage,method,path,http,user,uncertain);
 }
}
