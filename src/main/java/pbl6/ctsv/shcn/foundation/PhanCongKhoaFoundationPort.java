package pbl6.ctsv.shcn.foundation;

import java.util.*;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.*;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests.PageQuery;

/** SHCN public consumer boundary. Its implementation only reads existing Foundation tables. */
public interface PhanCongKhoaFoundationPort {
    record Khoa(long id,String ma,String ten,boolean doc,boolean lap,boolean dangHoatDong){}
    record Actor(long userId,String accountId,boolean doc,boolean lap,List<Khoa> khoa){
        public Actor{khoa=List.copyOf(khoa);}
    }
    Actor quyen(FoundationAccess.Identity identity);
    PhamViDTO phamVi(Actor actor);
    boolean maLuuDuoc(String value);
    Page<ChuongTrinhDTO> chuongTrinh(Khoa khoa,PageQuery query);
    Page<LopDTO> lop(Khoa khoa,Long chuongTrinhId,PageQuery query);
    Page<GiangVienDTO> giangVien(Khoa khoa,PageQuery query);
    Map<String,LopDTO> lopTheoMa(Khoa khoa,Collection<String> codes);
    Map<String,GiangVienDTO> giangVienTheoMa(Khoa khoa,Collection<String> codes);
    boolean trungMaLopTheoCollation(List<String> codes);
}
