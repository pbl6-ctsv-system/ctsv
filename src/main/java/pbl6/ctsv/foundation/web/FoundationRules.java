package pbl6.ctsv.foundation.web;
import java.time.LocalDate;
final class FoundationRules {
 record PageInput(int page,int size,long offset){}
 static long id(String raw){
  try{if(raw==null||!raw.matches("[1-9][0-9]{0,18}"))throw FoundationFailure.invalid("id");return Long.parseLong(raw);}
  catch(NumberFormatException e){throw FoundationFailure.invalid("id");}
 }
 static PageInput page(String page,String size,int defaultSize){
  try{
   if(page!=null&&!page.matches("0|[1-9][0-9]*")||size!=null&&!size.matches("[1-9][0-9]*"))throw FoundationFailure.invalid("page");
   int p=page==null?0:Integer.parseInt(page),s=size==null?defaultSize:Integer.parseInt(size);
   if(s<1||s>100)throw FoundationFailure.invalid("size");
   long offset=Math.multiplyExact((long)p,s);if(offset>100000)throw FoundationFailure.invalid("page");
   return new PageInput(p,s,offset);
  }catch(NumberFormatException|ArithmeticException e){throw FoundationFailure.invalid("page");}
 }
 static void dates(LocalDate start,LocalDate end){if(start==null||end!=null&&end.isBefore(start))throw FoundationFailure.invalid("from");}
}
