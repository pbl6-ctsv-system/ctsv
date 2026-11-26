package pbl6.ctsv.shcn.dotsinhhoat.service;
import java.time.*;
import java.util.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.MauDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.entity.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.repository.*;
import pbl6.ctsv.shcn.dotsinhhoat.validation.*;

/** All methods are called inside DotService.execute: same Auth, permissions and SQL transaction. */
public final class MauService {
 private final DotRepository dots;private final MauRepository templates;private final MauRequests validator;private final Clock clock;
 public MauService(DotRepository dots,MauRepository templates,MauRequests validator,Clock clock){this.dots=dots;this.templates=templates;this.validator=validator;this.clock=clock;}
 private static boolean writable(DotSinhHoat root){try{DotService.writable(root);return true;}catch(DotFailure e){if(e.code().equals("STATE_CONFLICT"))return false;throw e;}}
 public MauDTO read(long dot,DotService.Actor actor){
  var root=dots.find(dot,false);var template=templates.find(dot);var items=template==null?List.<Muc>of():templates.sections(template.id());
  if(template!=null){try{validator.validateConfiguration(items);}catch(DotFailure e){throw new IllegalStateException("Stored template is not valid",e);}}
  return new MauDTO(template==null?null:template.id().toString(),Long.toString(dot),Base64.getEncoder().encodeToString(root.getPhienBanGhi()),template!=null,actor.permissions().prepare()&&writable(root)&&(template==null||!template.state().equals("DA_KHOA")),template==null?null:template.name(),template==null?null:template.state(),items,TemplateDictionary.CATALOG);
 }
 // Caller already created/locked and versioned the root inside DotService.execute.
 public void saveWithRound(long dot,Draft draft,DotService.Actor actor){
  DotService.writable(dots.find(dot,false));validator.validate(draft);
  var existing=templates.find(dot);if(existing!=null&&existing.state().equals("DA_KHOA"))throw new DotFailure(409,"STATE_CONFLICT");
  templates.save(dot,new Input(new byte[8],draft.tenMau(),draft.muc()),actor.identity().principal().accountId().toString(),OffsetDateTime.now(clock));
  dots.audit(actor.identity().userId(),existing==null?"SHCN_TEMPLATE_CREATE":"SHCN_TEMPLATE_UPDATE",dot);
 }
 public MauDTO save(long dot,Input input,DotService.Actor actor){
  var root=dots.find(dot,true);if(!Arrays.equals(root.getPhienBanGhi(),input.expectedVersion()))throw new DotFailure(409,"VERSION_CONFLICT");DotService.writable(root);
  var existing=templates.find(dot);if(existing!=null&&existing.state().equals("DA_KHOA"))throw new DotFailure(409,"STATE_CONFLICT");validator.validate(input);
  var now=OffsetDateTime.now(clock);templates.compareAndTouch(dot,input.expectedVersion(),now);templates.save(dot,input,actor.identity().principal().accountId().toString(),now);
  dots.audit(actor.identity().userId(),existing==null?"SHCN_TEMPLATE_CREATE":"SHCN_TEMPLATE_UPDATE",dot);return read(dot,actor);
 }
}
