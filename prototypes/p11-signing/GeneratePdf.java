import java.nio.file.*;import org.apache.pdfbox.pdmodel.*;import org.apache.pdfbox.pdmodel.common.PDRectangle;import org.apache.pdfbox.pdmodel.font.PDType0Font;
/** Synthetic visual fixture only; never a legally signed document. */
class GeneratePdf {
 public static void main(String[] args)throws Exception{
  Path folder=Path.of(args[0]);Files.createDirectories(folder);
  for(String type:new String[]{"bb","qd"})for(int variant=0;variant<2;variant++)try(var d=new PDDocument()){
   var font=PDType0Font.load(d,Path.of("C:/Windows/Fonts/arial.ttf").toFile());
   for(int page=1;page<=5;page++){
    var p=new PDPage(PDRectangle.A4);if(variant==1&&page==3)p.setRotation(90);if(variant==1&&page==4)p.setCropBox(new PDRectangle(20,30,550,750));d.addPage(p);
    try(var c=new PDPageContentStream(d,p)){
     line(c,font,44,800,10,"DỮ LIỆU MÔ PHỎNG · KHÔNG PHẢI VĂN BẢN CHÍNH THỨC");
     line(c,font,140,748,16,type.equals("bb")?"BIÊN BẢN XÉT MIỄN GIẢM HỌC PHÍ":"QUYẾT ĐỊNH MIỄN GIẢM HỌC PHÍ");
     line(c,font,175,720,12,"Học kỳ I · Năm học 2026–2027");
     line(c,font,44,667,12,"Đợt: MGHP học kỳ I · Bộ văn bản minh họa P11");
     line(c,font,44,634,12,"Cuộc họp Hội đồng ngày 23/11/2026 · Phòng họp CTSV");
     line(c,font,44,596,12,page==2?"Kết luận và khu vực ký xác nhận":"Nội dung cuộc họp và danh sách kèm theo");
     for(int k=0;k<6;k++)line(c,font,44,560-k*29,11,new String[]{"Dữ liệu xét đã chốt; một diện có mức hưởng cao nhất cho mỗi nhóm.","Thông tin sinh viên và căn cứ được giữ theo đúng phiên bản đã chốt.","Biên bản và quyết định thuộc cùng một bộ dữ liệu.","Người ký kiểm tra đúng tài liệu và tự xác nhận bước ký của mình.","Bản sau mỗi người ký được lưu riêng tại kho tệp của CTSV.","Mọi chữ ký trong prototype đều là mô phỏng để review giao diện."}[k]);
     line(c,font,65,298,11,type.equals("bb")?"CHUYÊN VIÊN MGHP":"PHÓ PHÒNG CTSV");line(c,font,360,298,11,"PHÓ HIỆU TRƯỞNG");
     line(c,font,44,45,10,"SYNTHETIC · Trang "+page+" / 5 · "+(variant==1?"Bản sửa minh họa":"Bản chuẩn bị"));
    }
   }d.save(folder.resolve(type+(variant==1?"-revised":"")+".pdf").toFile());
  }
 }
 static void line(PDPageContentStream c,PDType0Font f,float x,float y,int size,String value)throws Exception{c.beginText();c.setFont(f,size);c.newLineAtOffset(x,y);c.showText(value);c.endText();}
}
