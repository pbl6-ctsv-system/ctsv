import {ApiError} from '../auth/auth-client.mjs';
const messages:Record<string,string>={UNAUTHENTICATED:'Vui lòng đăng nhập.',TOKEN_REVOKED:'Tài khoản hoặc token đã bị thu hồi. Vui lòng đăng nhập lại.',FORBIDDEN:'Bạn không có quyền thực hiện thao tác này.',NOT_FOUND:'Không tìm thấy dữ liệu.',VERSION_CONFLICT:'Dữ liệu đã thay đổi. Hãy đọc lại trước khi lưu.',DEPENDENCY_UNAVAILABLE:'Chưa thể kiểm tra trạng thái bảo mật. Vui lòng thử lại sau.',LOGIN_REQUIRED:'Lượt làm mới trước chưa được xác nhận. Vui lòng đăng nhập lại.'};
export function errorMessage(error:unknown){
 return error instanceof ApiError ? (messages[error.code]??('Yêu cầu thất bại: '+error.code)) : error instanceof Error ? error.message : 'Có lỗi xảy ra.';
}
