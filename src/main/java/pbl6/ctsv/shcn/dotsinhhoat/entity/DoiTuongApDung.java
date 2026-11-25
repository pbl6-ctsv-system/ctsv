package pbl6.ctsv.shcn.dotsinhhoat.entity;

import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.DoiTuong;

@Entity @Table(name="shcn_doi_tuong_ap_dung",schema="shcn")
public class DoiTuongApDung {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="ma_dot",nullable=false) private Long maDot;
    @Column(name="loai_doi_tuong",nullable=false,length=40) private String loaiDoiTuong;
    @Column(name="ma_doi_tuong",nullable=false,length=32) private String maDoiTuong;
    @Column(name="hinh_thuc_cho_phep",nullable=false,length=40) private String hinhThucChoPhep;
    @Column(name="khung_ngay_bat_dau") private LocalDate khungNgayBatDau;
    @Column(name="khung_ngay_ket_thuc") private LocalDate khungNgayKetThuc;
    @Nationalized @Column(name="khung_tiet_cho_phep",columnDefinition="nvarchar(max)") private String khungTietChoPhep;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="ngay_tao",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayTao;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="ngay_cap_nhat",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayCapNhat;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ma_dot",insertable=false,updatable=false) private DotSinhHoat dot;
    protected DoiTuongApDung(){}
    public static DoiTuongApDung create(long root,DoiTuong d,String periods,OffsetDateTime now){
        var row=new DoiTuongApDung();row.maDot=root;row.loaiDoiTuong=d.loaiDoiTuong();row.maDoiTuong=d.maDoiTuong();row.hinhThucChoPhep=d.hinhThucChoPhep();
        row.khungNgayBatDau=d.khungNgayBatDau();row.khungNgayKetThuc=d.khungNgayKetThuc();row.khungTietChoPhep=periods;row.ngayTao=now;row.ngayCapNhat=now;return row;
    }
    public String periods(){return khungTietChoPhep;}
    public DoiTuong dto(java.util.List<pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.KhoangTiet> periods){return new DoiTuong(loaiDoiTuong,maDoiTuong,hinhThucChoPhep,khungNgayBatDau,khungNgayKetThuc,periods);}
}
