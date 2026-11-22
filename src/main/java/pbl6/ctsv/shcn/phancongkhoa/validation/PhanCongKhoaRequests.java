package pbl6.ctsv.shcn.phancongkhoa.validation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaRepository.DongNhap;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import static pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure.invalid;

public final class PhanCongKhoaRequests {
    public record PageQuery(int page,int size,int offset,String q){}
    public record TaoNhap(String maKhoa,String namHoc,int phienBanDuKien,Long maDanhSachTruoc,
                          OffsetDateTime hieuLucTu,OffsetDateTime hieuLucDen,String lyDoDieuChinh){}
    public record LuuNhap(byte[] expectedVersion,OffsetDateTime hieuLucTu,OffsetDateTime hieuLucDen,
                          String lyDoDieuChinh,List<DongNhap> cacPhanCong){
        public LuuNhap{expectedVersion=expectedVersion.clone();cacPhanCong=List.copyOf(cacPhanCong);}
        @Override public byte[] expectedVersion(){return expectedVersion.clone();}
    }
    private final ObjectMapper json;
    public PhanCongKhoaRequests(ObjectMapper json){this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
    public void query(HttpServletRequest request,String... allowed){
        try{if(request.getInputStream().read()!=-1)throw invalid("body");}catch(java.io.IOException e){throw invalid("body");}
        var keys=Set.of(allowed);
        for(String key:request.getParameterMap().keySet())
            if(!keys.contains(key)||request.getParameterValues(key).length!=1)throw invalid(key);
    }
    public JsonNode body(HttpServletRequest request){
        if(request.getQueryString()!=null)throw invalid("query");
        try{
            var type=request.getContentType()==null?null:MediaType.parseMediaType(request.getContentType());
            if(type==null||!MediaType.APPLICATION_JSON.includes(type)||type.getCharset()!=null&&!StandardCharsets.UTF_8.equals(type.getCharset()))
                throw new PhanCongKhoaFailure(415,"UNSUPPORTED_MEDIA_TYPE");
        }catch(InvalidMediaTypeException e){throw new PhanCongKhoaFailure(415,"UNSUPPORTED_MEDIA_TYPE");}
        if(request.getHeader("Content-Encoding")!=null&&!"identity".equals(request.getHeader("Content-Encoding")))throw new PhanCongKhoaFailure(415,"UNSUPPORTED_MEDIA_TYPE");
        try{
            byte[] bytes=request.getInputStream().readNBytes(1048577);
            if(bytes.length>1048576)throw new PhanCongKhoaFailure(413,"PAYLOAD_TOO_LARGE");
            return json.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        }catch(java.io.IOException e){throw invalid("body");}
    }
    public byte[] xoa(JsonNode n){
        shape(n,"",Set.of("expectedVersion"));return version(n.get("expectedVersion"));
    }
    public TaoNhap tao(JsonNode n){
        shape(n,"",Set.of("maKhoa","namHoc","phienBanDuKien","maDanhSachTruoc","hieuLucTu","hieuLucDen","lyDoDieuChinh"));
        var from=instant(n.get("hieuLucTu"),"hieuLucTu",false);var until=instant(n.get("hieuLucDen"),"hieuLucDen",true);interval(from,until);
        Long before=n.get("maDanhSachTruoc").isNull()?null:id(text(n.get("maDanhSachTruoc"),"maDanhSachTruoc",19,false,false),"maDanhSachTruoc");
        String reason=text(n.get("lyDoDieuChinh"),"lyDoDieuChinh",4000,true,false);
        return new TaoNhap(code(n.get("maKhoa"),"maKhoa"),label(n.get("namHoc"),"namHoc",20),integer(n.get("phienBanDuKien"),"phienBanDuKien",1,Integer.MAX_VALUE),before,from,until,reason);
    }
    public LuuNhap luu(JsonNode n){
        shape(n,"",Set.of("expectedVersion","hieuLucTu","hieuLucDen","lyDoDieuChinh","cacPhanCong"));
        var from=instant(n.get("hieuLucTu"),"hieuLucTu",false);var until=instant(n.get("hieuLucDen"),"hieuLucDen",true);interval(from,until);
        var rows=n.get("cacPhanCong");if(!rows.isArray()||rows.size()>2000)throw invalid("cacPhanCong");
        var result=new ArrayList<DongNhap>();var seen=new HashSet<String>();
        for(int i=0;i<rows.size();i++){
            String prefix="cacPhanCong["+i+"]";var row=rows.get(i);shape(row,prefix,Set.of("maLop","maGiaoVien","ghiChu"));
            String lop=code(row.get("maLop"),prefix+".maLop");if(!seen.add(lop))throw invalid(prefix+".maLop");
            result.add(new DongNhap(lop,code(row.get("maGiaoVien"),prefix+".maGiaoVien"),text(row.get("ghiChu"),prefix+".ghiChu",4000,true,false)));
        }
        return new LuuNhap(version(n.get("expectedVersion")),from,until,text(n.get("lyDoDieuChinh"),"lyDoDieuChinh",4000,true,false),result);
    }
    public static PageQuery page(String page,String size,String q){
        int p=number(page,"page",0,Integer.MAX_VALUE,0),s=number(size,"size",1,100,25);
        long offset=(long)p*s;if(offset>100000)throw invalid("page");
        if(q!=null){unicode(q,"q");if(q.length()>100)throw invalid("q");}
        return new PageQuery(p,s,(int)offset,q==null?"":q);
    }
    public static Integer optionalVersion(String value){return value==null?null:number(value,"phienBan",1,Integer.MAX_VALUE,1);}
    private static int number(String value,String field,int min,int max,int fallback){
        if(value==null)return fallback;
        try{if(!value.matches("[0-9]+"))throw invalid(field);long number=Long.parseLong(value);if(number<min||number>max)throw invalid(field);return (int)number;}catch(NumberFormatException e){throw invalid(field);}
    }
    public static long id(String value,String field){
        try{if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw invalid(field);return Long.parseLong(value);}catch(NumberFormatException e){throw invalid(field);}
    }
    public static String requiredCode(String value,String field){return label(value,field,32);}
    public static String requiredYear(String value){return label(value,"namHoc",20);}
    public static String label(String value,String field,int max){
        if(value==null||value.isEmpty()||value.length()>max||space(value.charAt(0))||space(value.charAt(value.length()-1)))throw invalid(field);unicode(value,field);return value;
    }
    public static boolean selectableCode(String value){
        try{label(value,"code",32);return true;}catch(PhanCongKhoaFailure e){return false;}
    }
    private static String label(JsonNode n,String field,int max){return label(text(n,field,max,false,false),field,max);}
    private static String code(JsonNode n,String field){return label(n,field,32);}
    private static String text(JsonNode n,String field,int max,boolean nullable,boolean blank){
        if(n==null)throw invalid(field);if(n.isNull()){if(nullable)return null;throw invalid(field);}
        if(!n.isTextual())throw invalid(field);String value=n.textValue();unicode(value,field);
        if(value.length()>max||!blank&&!nullable&&value.isEmpty())throw invalid(field);return value;
    }
    private static int integer(JsonNode n,String field,int min,int max){if(n==null||!n.isIntegralNumber()||!n.canConvertToInt()||n.intValue()<min||n.intValue()>max)throw invalid(field);return n.intValue();}
    private static byte[] version(JsonNode n){
        String value=text(n,"expectedVersion",12,false,false);
        try{byte[] bytes=Base64.getDecoder().decode(value);if(bytes.length!=8||!Base64.getEncoder().encodeToString(bytes).equals(value))throw invalid("expectedVersion");return bytes;}catch(IllegalArgumentException e){throw invalid("expectedVersion");}
    }
    private static OffsetDateTime instant(JsonNode n,String field,boolean nullable){
        if(n!=null&&n.isNull()&&nullable)return null;
        String value=text(n,field,40,false,false);
        if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,7})?(Z|[+-][0-9]{2}:[0-9]{2})"))throw invalid(field);
        try{var result=OffsetDateTime.parse(value);int utcYear=result.withOffsetSameInstant(ZoneOffset.UTC).getYear();
            if(result.getYear()<1||utcYear<1||utcYear>9999||Math.abs(result.getOffset().getTotalSeconds())>14*3600)throw invalid(field);return result;
        }catch(DateTimeException e){throw invalid(field);}
    }
    private static void interval(OffsetDateTime from,OffsetDateTime until){if(until!=null&&!until.toInstant().isAfter(from.toInstant()))throw invalid("hieuLucDen");}
    private static void shape(JsonNode n,String prefix,Set<String> fields){
        if(n==null||!n.isObject())throw invalid(prefix.isEmpty()?"body":prefix);
        var names=n.fieldNames();while(names.hasNext()){String name=names.next();if(!fields.contains(name))throw invalid(prefix.isEmpty()?name:prefix+"."+name);}
        for(String field:fields)if(!n.has(field))throw invalid(prefix.isEmpty()?field:prefix+"."+field);
    }
    private static boolean space(char c){return Character.isWhitespace(c)||Character.isSpaceChar(c);}
    private static void unicode(String value,String field){for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))throw invalid(field);}else if(Character.isLowSurrogate(c))throw invalid(field);}}
}
