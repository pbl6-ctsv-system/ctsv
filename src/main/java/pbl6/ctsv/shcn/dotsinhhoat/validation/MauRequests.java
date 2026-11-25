package pbl6.ctsv.shcn.dotsinhhoat.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.MauDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import static pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure.invalid;

public final class MauRequests {
 public Input parse(JsonNode n){
  shape(n,Set.of("expectedVersion","tenMau","muc"),"");String token=text(n.get("expectedVersion"),"expectedVersion",12);byte[] version;
  try{version=Base64.getDecoder().decode(token);if(version.length!=8||!Base64.getEncoder().encodeToString(version).equals(token))throw invalid("expectedVersion");}catch(IllegalArgumentException e){throw invalid("expectedVersion");}
  var rows=n.get("muc");if(!rows.isArray()||rows.isEmpty()||rows.size()>100)throw invalid("muc");var items=new ArrayList<Muc>();
  for(int i=0;i<rows.size();i++){
   String f="muc["+i+"]";var m=rows.get(i);shape(m,Set.of("maMuc","tieuDe","thuTuHienThi","loaiMuc","batBuoc","cauHinhTruong"),f);
   String key=key(m.get("maMuc"),f+".maMuc");var fields=m.get("cauHinhTruong");if(!fields.isArray()||fields.isEmpty()||fields.size()>100)throw invalid(f+".cauHinhTruong");var list=new ArrayList<Truong>();
   for(int j=0;j<fields.size();j++){
    String p=f+".cauHinhTruong["+j+"]";var v=fields.get(j);shape(v,Set.of("maTruong","nhan","kieuDuLieu","batBuoc","nguonDien","maNguonHeThong"),p);
    list.add(new Truong(key(v.get("maTruong"),p+".maTruong"),text(v.get("nhan"),p+".nhan",200),text(v.get("kieuDuLieu"),p+".kieuDuLieu",32),bool(v.get("batBuoc"),p+".batBuoc"),text(v.get("nguonDien"),p+".nguonDien",32),v.get("maNguonHeThong").isNull()?null:key(v.get("maNguonHeThong"),p+".maNguonHeThong")));
   }
   var order=m.get("thuTuHienThi");if(!order.isIntegralNumber()||!order.canConvertToInt())throw invalid(f+".thuTuHienThi");
   items.add(new Muc(key,text(m.get("tieuDe"),f+".tieuDe",250),order.intValue(),text(m.get("loaiMuc"),f+".loaiMuc",32),bool(m.get("batBuoc"),f+".batBuoc"),list,TemplateDictionary.system(key)!=null));
  }
  var input=new Input(version,text(n.get("tenMau"),"tenMau",200),items);validate(input);return input;
 }
 public void validate(Input input){if(input.expectedVersion().length!=8)throw invalid("expectedVersion");checkText(input.tenMau(),"tenMau",200);validateConfiguration(input.muc());}
 public void validateConfiguration(List<Muc> items){
  if(items.isEmpty()||items.size()>100)throw invalid("muc");var keys=new HashSet<String>();
  for(int i=0;i<items.size();i++){
   var m=items.get(i);String f="muc["+i+"]";checkKey(m.maMuc(),f+".maMuc");checkText(m.tieuDe(),f+".tieuDe",250);
   if(!keys.add(m.maMuc()))throw invalid(f+".maMuc");if(m.thuTuHienThi()!=i+1)throw invalid(f+".thuTuHienThi");
   if(m.cauHinhTruong().isEmpty()||m.cauHinhTruong().size()>100)throw invalid(f+".cauHinhTruong");
   var fieldKeys=new HashSet<String>();
   for(int j=0;j<m.cauHinhTruong().size();j++){
    var v=m.cauHinhTruong().get(j);String p=f+".cauHinhTruong["+j+"]";checkKey(v.maTruong(),p+".maTruong");checkText(v.nhan(),p+".nhan",200);
    if(!fieldKeys.add(v.maTruong()))throw invalid(p+".maTruong");if(!TemplateDictionary.TYPES.contains(v.kieuDuLieu()))throw invalid(p+".kieuDuLieu");
    if("NGUOI_NHAP".equals(v.nguonDien())){if(v.maNguonHeThong()!=null)throw invalid(p+".maNguonHeThong");}
    else if("HE_THONG".equals(v.nguonDien())){var source=TemplateDictionary.CATALOG.nguonHeThong().stream().filter(s->s.ma().equals(v.maNguonHeThong())).findFirst().orElseThrow(()->invalid(p+".maNguonHeThong"));if(!source.kieuDuLieu().equals(v.kieuDuLieu()))throw invalid(p+".kieuDuLieu");}
    else throw invalid(p+".nguonDien");
   }
   var fixed=TemplateDictionary.system(m.maMuc());
   if(fixed!=null){if(!m.laMucHeThong()||!fixed.tieuDe().equals(m.tieuDe())||!fixed.loaiMuc().equals(m.loaiMuc())||fixed.batBuoc()!=m.batBuoc()||!fixed.cauHinhTruong().equals(m.cauHinhTruong())||m.maMuc().equals("DIEM_DANH")&&m.thuTuHienThi()!=1)throw new DotFailure(409,"SYSTEM_SECTION_IMMUTABLE");}
   else{if(m.laMucHeThong()||!"NOI_DUNG".equals(m.loaiMuc()))throw invalid(f+".loaiMuc");if(m.batBuoc()&&m.cauHinhTruong().stream().noneMatch(Truong::batBuoc))throw invalid(f+".batBuoc");}
  }
  if(!keys.containsAll(TemplateDictionary.SYSTEM.stream().map(Muc::maMuc).toList()))throw new DotFailure(409,"SYSTEM_SECTION_IMMUTABLE");
 }
 private static void checkKey(String s,String f){if(s==null||!s.matches("[A-Z][A-Z0-9_]{0,31}"))throw invalid(f);}
 private static String key(JsonNode n,String f){String s=text(n,f,32);checkKey(s,f);return s;}
 private static boolean bool(JsonNode n,String f){if(n==null||!n.isBoolean())throw invalid(f);return n.booleanValue();}
 private static String text(JsonNode n,String f,int max){if(n==null||!n.isTextual())throw invalid(f);String s=n.textValue();checkText(s,f,max);return s;}
 private static void checkText(String s,String f,int max){if(s==null||s.isBlank()||s.length()>max||s.chars().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)))throw invalid(f);for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw invalid(f);}else if(Character.isLowSurrogate(c))throw invalid(f);}}
 private static void shape(JsonNode n,Set<String> keys,String f){if(n==null||!n.isObject())throw invalid(f.isEmpty()?"body":f);for(String key:keys)if(!n.has(key))throw invalid(f.isEmpty()?key:f+"."+key);n.fieldNames().forEachRemaining(key->{if(!keys.contains(key))throw invalid(f.isEmpty()?key:f+"."+key);});}
}
