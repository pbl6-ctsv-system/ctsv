package pbl6.ctsv.shcn.dotsinhhoat.reference;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.shcn.dotsinhhoat.reference.SemesterAudienceResolver.Member;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class JdbcAudienceQueryBudgetTest {
    int queries(int size){
        var count=new AtomicInteger();List<Member> people=new ArrayList<>();
        for(int i=1;i<=size;i++)people.add(new Member(i,"SV"+i,"Synthetic",i,"L"+i,"Class "+i,(long)i,"K"+i,"2024","THUONG","0","0","0"));
        JdbcTemplate jdbc=mock(JdbcTemplate.class,inv->{
            String method=inv.getMethod().getName();if(!method.equals("query")&&!method.equals("queryForObject"))return RETURNS_DEFAULTS.answer(inv);
            count.incrementAndGet();String sql=inv.getArgument(0);
            if(method.equals("queryForObject")){
                if(inv.getArgument(1)==Long.class)return 7L;
                return sql.contains("CONVERT(nvarchar")||sql.contains("OPENJSON");
            }
            if(sql.contains("FROM dbo.he_thong_ngoai"))return List.of(4L);
            if(sql.contains("FROM dbo.hoc_ky"))return List.of(7L);
            if(sql.contains("FROM dbo.du_lieu_hoc_vu_dong_bo"))return people;
            if(sql.contains("FROM dbo.phan_lop_ren_luyen"))return List.of();
            throw new AssertionError("Unexpected lookup boundary");
        });
        var source=mock(AcademicSemesterSource.class);
        when(source.findById(7L)).thenReturn(Optional.of(new AcademicSemesterSource.Semester(7,"0010","Semester",null,null,null,null,true,null)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{var result=new JdbcDotReference(jdbc,source,"SYNTHETIC").groups("0010");assertEquals(size,result.khoaHoc().size());return count.get();}
        finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
    @Test void moreStudentsAndCohortsDoNotAddOneQueryPerCode(){
        int small=queries(10),large=queries(200);assertEquals(small,large);assertTrue(large<15);
    }
}
