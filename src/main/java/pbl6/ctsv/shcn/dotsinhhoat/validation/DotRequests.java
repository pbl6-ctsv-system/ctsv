package pbl6.ctsv.shcn.dotsinhhoat.validation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.http.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import static pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure.invalid;

public final class DotRequests {
    private static final Set<String> CONFIG=Set.of("maHocKy","tenDot","khungNgayBatDau","khungNgayKetThuc","khungTietChoPhep","hanChotDangKyLich");
    public static final Set<String> STATES=Set.of("NHAP","CHO_DUYET","BI_TRA_LAI","DA_DUYET","DA_PHAT_HANH","DA_HUY");
    private final ObjectMapper json;
    public DotRequests(ObjectMapper json){this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
    public JsonNode body(HttpServletRequest r){
        if(r.getQueryString()!=null)throw invalid("query");
        try{var media=r.getContentType()==null?null:MediaType.parseMediaType(r.getContentType());
            if(media==null||!MediaType.APPLICATION_JSON.includes(media)||media.getCharset()!=null&&!StandardCharsets.UTF_8.equals(media.getCharset()))throw new DotFailure(415,"UNSUPPORTED_MEDIA_TYPE");
        }catch(IllegalArgumentException e){throw new DotFailure(415,"UNSUPPORTED_MEDIA_TYPE");}
        if(r.getHeader("Content-Encoding")!=null&&!"identity".equals(r.getHeader("Content-Encoding")))throw new DotFailure(415,"UNSUPPORTED_MEDIA_TYPE");
        try{var bytes=r.getInputStream().readNBytes(1048577);if(bytes.length>1048576)throw new DotFailure(413,"PAYLOAD_TOO_LARGE");return json.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());}
        catch(java.io.IOException e){throw invalid("body");}
    }
    public void get(HttpServletRequest r,String...allowed){
        var keys=Set.of(allowed);for(var k:r.getParameterMap().keySet())if(!keys.contains(k)||r.getParameterValues(k).length!=1)throw invalid(k);
        try{if(r.getInputStream().read()!=-1)throw invalid("body");}catch(java.io.IOException e){throw invalid("body");}
    }
    public CauHinh create(JsonNode n){configShape(n,CONFIG);return config(n);}
    public SuaDot update(JsonNode n){var fields=new HashSet<>(CONFIG);fields.add("expectedVersion");configShape(n,fields);return new SuaDot(version(n.get("expectedVersion")),config(n));}
    private CauHinh config(JsonNode n){
        var code=code(text(n.get("maHocKy"),"maHocKy",32),"maHocKy");var name=text(n.get("tenDot"),"tenDot",200);
        if(name.isBlank()||!name.equals(name.strip())||space(name.charAt(0))||space(name.charAt(name.length()-1)))throw invalid("tenDot");
        var from=date(n.get("khungNgayBatDau"),"khungNgayBatDau");var until=date(n.get("khungNgayKetThuc"),"khungNgayKetThuc");
        if(until.isBefore(from))throw invalid("khungNgayKetThuc");
        var ranges=n.get("khungTietChoPhep");if(!ranges.isArray()||ranges.isEmpty()||ranges.size()>100)throw invalid("khungTietChoPhep");
        var items=new ArrayList<KhoangTiet>();for(int i=0;i<ranges.size();i++){
            var field="khungTietChoPhep["+i+"]";var row=ranges.get(i);shape(row,Set.of("tietDau","tietCuoi"),field);
            int a=integer(row.get("tietDau"),field+".tietDau"),b=integer(row.get("tietCuoi"),field+".tietCuoi");if(a>b)throw invalid(field+".tietCuoi");items.add(new KhoangTiet(a,b));
        }
        return new CauHinh(code,name,from,until,items,instant(n.get("hanChotDangKyLich")),audiences(n));
    }
    private void configShape(JsonNode n,Set<String> required){
        var all=new HashSet<>(required);if(n!=null&&n.has("doiTuongApDung"))all.add("doiTuongApDung");shape(n,all,"");
    }
    private List<DoiTuong> audiences(JsonNode n){
        if(!n.has("doiTuongApDung"))return null;
        var rows=n.get("doiTuongApDung");if(!rows.isArray()||rows.size()>1000)throw invalid("doiTuongApDung");
        var result=new ArrayList<DoiTuong>();var keys=new HashSet<String>();
        for(int i=0;i<rows.size();i++){
            var r=rows.get(i);String f="doiTuongApDung["+i+"]";
            shape(r,Set.of("loaiDoiTuong","maDoiTuong","hinhThucChoPhep","khungNgayBatDau","khungNgayKetThuc","khungTietChoPhep"),f);
            String type=text(r.get("loaiDoiTuong"),f+".loaiDoiTuong",40),code=code(text(r.get("maDoiTuong"),f+".maDoiTuong",32),f+".maDoiTuong");
            String mode=text(r.get("hinhThucChoPhep"),f+".hinhThucChoPhep",40);
            if(!Set.of("KHOA_HOC","LOP").contains(type)||!Set.of("OFFLINE","ONLINE","CA_HAI").contains(mode)||!keys.add(type+":"+code))throw invalid(f);
            LocalDate from=r.get("khungNgayBatDau").isNull()?null:date(r.get("khungNgayBatDau"),f+".khungNgayBatDau");
            LocalDate until=r.get("khungNgayKetThuc").isNull()?null:date(r.get("khungNgayKetThuc"),f+".khungNgayKetThuc");
            if((from==null)!=(until==null)||from!=null&&until.isBefore(from))throw invalid(f+".khungNgayKetThuc");
            List<KhoangTiet> periods=null;var p=r.get("khungTietChoPhep");
            if(!p.isNull()){
                if(!p.isArray()||p.isEmpty()||p.size()>100)throw invalid(f+".khungTietChoPhep");
                periods=new ArrayList<>();for(var v:p){shape(v,Set.of("tietDau","tietCuoi"),f+".khungTietChoPhep");
                    int a=integer(v.get("tietDau"),f+".tietDau"),b=integer(v.get("tietCuoi"),f+".tietCuoi");if(a>b)throw invalid(f+".tietCuoi");periods.add(new KhoangTiet(a,b));}
            }
            result.add(new DoiTuong(type,code,mode,from,until,periods));
        }
        return result;
    }
    public static Query query(String page,String size,String sort,String q,String semester,String faculty,String state){
        int p=number(page,"page",0,Integer.MAX_VALUE,0),s=number(size,"size",1,100,25);long offset=(long)p*s;if(offset>100000)throw invalid("page");
        if(sort!=null&&!Set.of("id:asc","id:desc").contains(sort))throw invalid("sort");
        if(q!=null){unicode(q,"q");if(q.isEmpty()||q.length()>200)throw invalid("q");}
        if(state!=null&&!STATES.contains(state))throw invalid("trangThai");
        return new Query(p,s,(int)offset,"id:desc".equals(sort),q==null?"":q,semester==null?null:code(semester,"maHocKy"),faculty==null?null:code(faculty,"maKhoa"),state);
    }
    private static int number(String s,String f,int min,int max,int defaultValue){if(s==null)return defaultValue;try{if(!s.matches("[0-9]+"))throw invalid(f);long v=Long.parseLong(s);if(v<min||v>max)throw invalid(f);return(int)v;}catch(NumberFormatException e){throw invalid(f);}}
    public static long id(String s){try{if(s==null||!s.matches("[1-9][0-9]{0,18}"))throw invalid("dotId");return Long.parseLong(s);}catch(NumberFormatException e){throw invalid("dotId");}}
    public static String code(String s,String field){unicode(s,field);if(s.isEmpty()||s.length()>32||s.chars().anyMatch(c->space((char)c)))throw invalid(field);return s;}
    private static boolean space(char c){return Character.isWhitespace(c)||Character.isSpaceChar(c);}
    private static void unicode(String s,String field){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw invalid(field);}else if(Character.isLowSurrogate(c))throw invalid(field);}}
    private static String text(JsonNode n,String f,int max){if(n==null||!n.isTextual()||n.textValue().isEmpty()||n.textValue().length()>max)throw invalid(f);unicode(n.textValue(),f);return n.textValue();}
    private static void shape(JsonNode n,Set<String> fields,String prefix){if(n==null||!n.isObject())throw invalid(prefix.isEmpty()?"body":prefix);for(var key:fields)if(!n.has(key))throw invalid(prefix.isEmpty()?key:prefix+"."+key);var names=n.fieldNames();while(names.hasNext())if(!fields.contains(names.next()))throw invalid(prefix.isEmpty()?"body":prefix);}
    private static int integer(JsonNode n,String f){if(n==null||!n.isIntegralNumber()||!n.canConvertToInt()||n.intValue()<1||n.intValue()>32767)throw invalid(f);return n.intValue();}
    private static LocalDate date(JsonNode n,String f){String s=text(n,f,10);if(!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid(f);try{var d=LocalDate.parse(s,DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT));if(d.getYear()<1)throw invalid(f);return d;}catch(DateTimeException e){throw invalid(f);}}
    private static OffsetDateTime instant(JsonNode n){String f="hanChotDangKyLich",s=text(n,f,40);if(!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,7})?(Z|[+-][0-9]{2}:[0-9]{2})"))throw invalid(f);try{var d=OffsetDateTime.parse(s);int y=d.withOffsetSameInstant(ZoneOffset.UTC).getYear();if(d.getYear()<1||y<1||y>9999||Math.abs(d.getOffset().getTotalSeconds())>50400)throw invalid(f);return d;}catch(DateTimeException e){throw invalid(f);}}
    private static byte[] version(JsonNode n){String s=text(n,"expectedVersion",12);try{var bytes=Base64.getDecoder().decode(s);if(bytes.length!=8||!Base64.getEncoder().encodeToString(bytes).equals(s))throw invalid("expectedVersion");return bytes;}catch(IllegalArgumentException e){throw invalid("expectedVersion");}}
}
