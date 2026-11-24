package pbl6.ctsv.shcn.dotsinhhoat.validation;

import java.util.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.MauDtos.*;

/** Configuration keys only. No source queries, student data or expressions are evaluated. */
public final class TemplateDictionary {
 public static final List<String> TYPES=List.of("CHUOI","VAN_BAN","SO_NGUYEN","NGAY","BOOLEAN");
 private static Truong attendanceField(String code,String label){return new Truong(code,label,"SO_NGUYEN",true,"NGUOI_NHAP",null);}
 public static final Muc ATTENDANCE=new Muc("DIEM_DANH","Điểm danh",1,"DIEM_DANH",true,List.of(attendanceField("TONG_SO_SV","Tổng số sinh viên"),attendanceField("CO_MAT","Có mặt"),attendanceField("VANG_MAT","Vắng")),true);
 private static Muc recommendation(String key,String label,int order){return new Muc(key,label,order,"NOI_DUNG",false,List.of(new Truong("NOI_DUNG","Nội dung","VAN_BAN",false,"NGUOI_NHAP",null)),true);}
 public static final List<Muc> SYSTEM=List.of(ATTENDANCE,recommendation("KIEN_NGHI_SV","Kiến nghị của sinh viên",2),recommendation("KIEN_NGHI_GV","Kiến nghị của giảng viên",3));
 private static Nguon source(String code,String label){return new Nguon(code,label,"CHUOI");}
 public static final DanhMuc CATALOG=new DanhMuc(TYPES,List.of(source("DOT_TEN","Tên đợt"),source("HOC_KY_TEN","Học kỳ"),source("NAM_HOC","Năm học"),source("LOP_MA","Mã lớp"),source("LOP_TEN","Tên lớp"),source("KHOA_TEN","Khoa"),source("NGANH_TEN","Ngành"),source("GVCN_HO_TEN","GVCN"),source("LOP_TRUONG_HO_TEN","Lớp trưởng"),new Nguon("NGAY_HOP","Ngày họp","NGAY"),source("TIET_HOP","Tiết họp"),source("HINH_THUC","Hình thức"),source("PHONG","Phòng"),source("LINK_HOP","Link họp")),SYSTEM);
 public static Muc system(String key){return SYSTEM.stream().filter(m->m.maMuc().equals(key)).findFirst().orElse(null);}
 public static List<Muc> defaults(){return List.of(ATTENDANCE,new Muc("TRIEN_KHAI","Tình hình triển khai nội dung sinh hoạt lớp",2,"NOI_DUNG",true,List.of(new Truong("NOI_DUNG","Nội dung","VAN_BAN",true,"NGUOI_NHAP",null)),false),new Muc("KET_QUA_HOC_TAP","Tình hình kết quả học tập",3,"NOI_DUNG",false,List.of(new Truong("NOI_DUNG","Nội dung","VAN_BAN",false,"NGUOI_NHAP",null)),false),SYSTEM.get(1).order(4),SYSTEM.get(2).order(5));}
 private TemplateDictionary(){}
}
