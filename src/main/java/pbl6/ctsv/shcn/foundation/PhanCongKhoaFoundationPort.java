package pbl6.ctsv.shcn.foundation;

import java.util.*;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.*;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests.PageQuery;

/** SHCN public consumer boundary. Its implementation only reads existing Foundation tables. */
public interface PhanCongKhoaFoundationPort {
    record Khoa(long id,String ma,String ten,boolean doc,boolean lap,boolean kySo,boolean guiChuyenVien,boolean dangHoatDong){
        public Khoa(long id,String ma,String ten,boolean doc,boolean lap,boolean dangHoatDong){
            this(id,ma,ten,doc,lap,false,false,dangHoatDong);
        }
    }
    record Actor(long userId,String accountId,boolean doc,boolean lap,boolean kySo,boolean guiChuyenVien,List<Khoa> khoa){
        public Actor{khoa=List.copyOf(khoa);}
        public Actor(long userId,String accountId,boolean doc,boolean lap,List<Khoa> khoa){
            this(userId,accountId,doc,lap,false,false,khoa);
        }
    }
    record NguoiDungKhoa(long userId,String hoTen,String email){}

    Actor quyen(FoundationAccess.Identity identity);
    PhamViDTO phamVi(Actor actor);
    boolean maLuuDuoc(String value);
    Page<ChuongTrinhDTO> chuongTrinh(Khoa khoa,PageQuery query);
    Page<LopDTO> lop(Khoa khoa,Long chuongTrinhId,PageQuery query);
    Page<GiangVienDTO> giangVien(Khoa khoa,PageQuery query);
    Map<String,LopDTO> lopTheoMa(Khoa khoa,Collection<String> codes);
    Map<String,GiangVienDTO> giangVienTheoMa(Khoa khoa,Collection<String> codes);
    boolean trungMaLopTheoCollation(List<String> codes);

    default List<LopDTO> tatCaLopHoatDong(Khoa khoa){return List.of();}
    default Optional<NguoiDungKhoa> nguoiDung(long userId){return Optional.empty();}
    default boolean laChuyenVienDocNguon(FoundationAccess.Identity identity){return false;}
}
