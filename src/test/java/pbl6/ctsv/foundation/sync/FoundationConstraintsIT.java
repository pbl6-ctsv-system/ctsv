package pbl6.ctsv.foundation.sync;
import java.sql.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

/** Native SQL constraints on the actual four tables; fixtures are not provider/CT proof. */
class FoundationConstraintsIT {
    @BeforeAll static void prepare()throws Exception{P02Database.migrate();DurableStoreIT.ds=P02Database.source();}
    static Stream<String> uniqueTables(){return Stream.of("phong_hoc","phan_lop_ren_luyen","ghep_lop_sinh_hoat","tien_do_sinh_vien");}
    static long insert(Connection c,String table,String columns,String expressions)throws Exception {
        try(var s=c.createStatement();var q=s.executeQuery("INSERT dbo.["+table+"]("+columns+") OUTPUT INSERTED.id VALUES("+expressions+")")){q.next();return q.getLong(1);}
    }
    @ParameterizedTest @MethodSource("uniqueTables")
    void everyBusinessUniqueConstraintRejectsAnotherSourceIdWithoutMerging(String table)throws Exception{
        long source=DurableStoreIT.source();try(var c=P02Database.open()){
            long plan=insert(c,"hoc_ky","ma,he_thong_nguon_id,ma_dinh_danh_ben_ngoai","N'PLAN-"+source+"',"+source+",N'101'");
            long student=insert(c,"sinh_vien","mssv,he_thong_nguon_id,ma_dinh_danh_ben_ngoai","N'SV-"+source+"',"+source+",N'201'");
            long cl=insert(c,"lop_sinh_hoat","ma,he_thong_nguon_id,ma_dinh_danh_ben_ngoai","N'LOP-"+source+"',"+source+",N'301'");
            long program=insert(c,"chuong_trinh_dao_tao","ma,he_thong_nguon_id,ma_dinh_danh_ben_ngoai","N'CT-"+source+"',"+source+",N'401'");
            String cols="he_thong_nguon_id,ma_dinh_danh_ben_ngoai",tailColumns,tailValues;
            switch(table){
                case "phong_hoc"->{tailColumns=",ma_phong_nguon";tailValues=",'P02-SAME'";}
                case "phan_lop_ren_luyen"->{tailColumns=",hoc_ky_id,sinh_vien_id,lop_ren_luyen_id";tailValues=","+plan+","+student+","+cl;}
                case "ghep_lop_sinh_hoat"->{tailColumns=",hoc_ky_id,lop_goc_id,lop_nhan_id";tailValues=","+plan+","+cl+","+cl;}
                case "tien_do_sinh_vien"->{tailColumns=",hoc_ky_id,sinh_vien_id,chuong_trinh_dao_tao_id";tailValues=","+plan+","+student+","+program;}
                default->throw new AssertionError();
            }
            insert(c,table,cols+tailColumns,source+",N'1001'"+tailValues);
            assertThatThrownBy(()->insert(c,table,cols+tailColumns,source+",N'1002'"+tailValues)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getErrorCode()).isIn(2601,2627));
            assertThat(P02Database.scalar(c,"SELECT COUNT(*) FROM dbo.["+table+"] WHERE he_thong_nguon_id="+source)).isEqualTo(1);
        }
    }
}
