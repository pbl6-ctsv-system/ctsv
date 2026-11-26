package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shared.ca.CaProviderDtos.Receipt;

/** Durable, operation-bound evidence. No network/SQL and no credentials in the manifest. */
final class SigningResultSpool {
 record Context(UUID operation,long attempt,long request,long step,long actor,String role,String account,String title,String round,int version,String inputHash,String documentType,String semester,String accountId){
  Context(UUID operation,long attempt,long request,long step,long actor,String role,String account,String title,String round,int version,String inputHash){this(operation,attempt,request,step,actor,role,account,title,round,version,inputHash,"unknown","",null);}
 }
 record Entry(Context context,Receipt receipt,String fileName,long size,String sha256,boolean hadError,
              SigningFailurePolicy.Result error,UUID errorEvent,Instant errorAt,Instant receivedAt,Instant updatedAt,boolean completed,boolean deleted,boolean reported){}
 private final Path root;private final Clock clock;
 private final ObjectMapper json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 SigningResultSpool(Path root,Clock clock){
  this.clock=Objects.requireNonNull(clock);
  try{Files.createDirectories(root);if(Files.isSymbolicLink(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException();this.root=root.toRealPath();}
  catch(IOException|SecurityException e){throw problem("SIGNING_SPOOL_UNAVAILABLE");}
 }
 private static IllegalStateException problem(String code){return new IllegalStateException(code);}
 private Path path(UUID id,String extension){Objects.requireNonNull(id);var p=root.resolve(id+extension).normalize();if(!Objects.equals(p.getParent(),root)||Files.isSymbolicLink(p))throw problem("SIGNING_SPOOL_UNAVAILABLE");return p;}
 synchronized Optional<Entry> read(UUID id){
  Path p=path(id,".json");if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return Optional.empty();
  try{if(Files.size(p)>1_000_000)throw new IOException();var entry=json.readValue(Files.readAllBytes(p),Entry.class);if(entry.context()==null||!id.equals(entry.context().operation()))throw new IOException();return Optional.of(entry);}
  catch(IOException|RuntimeException e){throw problem("SIGNING_SPOOL_MANIFEST_INVALID");}
 }
 private Entry required(UUID id){return read(id).orElseThrow(()->problem("SIGNING_SPOOL_NOT_FOUND"));}
 synchronized Entry start(Context c){
  Objects.requireNonNull(c);Objects.requireNonNull(c.operation());
  var old=read(c.operation());if(old.isPresent()){if(!old.get().context().equals(c))throw problem("SIGNING_SPOOL_CONTEXT_CONFLICT");return old.get();}
  var entry=new Entry(c,null,c.operation()+".pdf",0,null,false,null,null,null,null,clock.instant(),false,false,false);write(entry);return entry;
 }
 synchronized void receipt(UUID id,Receipt receipt){
  var e=required(id);Objects.requireNonNull(receipt);
  if(e.receipt()!=null){
   var old=e.receipt();
   // SQL datetimeoffset(7) may round the provider's nanoseconds; retain the original receipt.
   boolean same=old.id().equals(receipt.id())&&old.document().equals(receipt.document())&&old.certificate().equals(receipt.certificate())&&old.certificateSerial().equals(receipt.certificateSerial())&&old.signerEmail().equals(receipt.signerEmail())&&old.signerName().equals(receipt.signerName())&&old.documentHash().equals(receipt.documentHash())&&old.signedPdfHash().equals(receipt.signedPdfHash())&&old.status().equals(receipt.status())&&Duration.between(old.signedAt(),receipt.signedAt()).abs().compareTo(Duration.ofNanos(1000))<=0;
   if(!same)throw problem("SIGNING_SPOOL_RECEIPT_CONFLICT");return;
  }
  write(new Entry(e.context(),receipt,e.fileName(),e.size(),e.sha256(),e.hadError(),e.error(),e.errorEvent(),e.errorAt(),e.receivedAt(),clock.instant(),e.completed(),e.deleted(),e.reported()));
 }
 synchronized void pdf(UUID id,byte[] bytes){
  var e=required(id);if(bytes==null||bytes.length==0||bytes.length>CaPdfPreflight.MAX_BYTES)throw problem("SIGNING_SPOOL_CONTENT_INVALID");
  String hash=CaPdfPreflight.sha256(bytes);Path p=path(id,".pdf");
  if(e.deleted())throw problem("SIGNING_SPOOL_DELETED");
  if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)){if(!Arrays.equals(pdf(id).orElseThrow(),bytes))throw problem("SIGNING_SPOOL_CONTENT_CONFLICT");}
  else atomic(p,bytes);
  write(new Entry(e.context(),e.receipt(),e.fileName(),bytes.length,hash,e.hadError(),e.error(),e.errorEvent(),e.errorAt(),e.receivedAt()==null?clock.instant():e.receivedAt(),clock.instant(),e.completed(),false,e.reported()));
 }
 synchronized Optional<byte[]> pdf(UUID id){
  var entry=read(id);if(entry.isEmpty()||entry.get().deleted())return Optional.empty();Path p=path(id,".pdf");if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return Optional.empty();
  try{long size=Files.size(p);if(size<1||size>CaPdfPreflight.MAX_BYTES)throw new IOException();byte[] bytes=Files.readAllBytes(p);var e=entry.get();if(e.sha256()!=null&&!e.sha256().equals(CaPdfPreflight.sha256(bytes)))throw new IOException();return Optional.of(bytes);}
  catch(IOException e){throw problem("SIGNING_SPOOL_CONTENT_INVALID");}
 }
 synchronized Entry failure(UUID id,SigningFailurePolicy.Result error){
  var e=required(id);var next=new Entry(e.context(),e.receipt(),e.fileName(),e.size(),e.sha256(),true,error,UUID.randomUUID(),clock.instant(),e.receivedAt(),clock.instant(),e.completed(),e.deleted(),false);write(next);return next;
 }
 /** Written only after SigningArtifacts has verified PDF revision and every signature. */
 synchronized void verified(UUID id,String hash){
  var e=required(id);if(e.deleted()||!Objects.equals(e.sha256(),hash)||pdf(id).isEmpty())throw problem("SIGNING_SPOOL_CONTENT_INVALID");
  atomic(path(id,".verified"),hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
 }
 synchronized boolean verified(UUID id){
  var e=required(id);if(e.deleted()||e.sha256()==null)return false;
  try{Path p=path(id,".verified");return Files.exists(p)&&Files.size(p)==64&&Files.readString(p).equals(e.sha256());}
  catch(IOException ex){throw problem("SIGNING_SPOOL_UNAVAILABLE");}
 }
 synchronized void reported(UUID id,UUID event){
  var e=required(id);if(!Objects.equals(e.errorEvent(),event))return;
  write(new Entry(e.context(),e.receipt(),e.fileName(),e.size(),e.sha256(),e.hadError(),e.error(),e.errorEvent(),e.errorAt(),e.receivedAt(),clock.instant(),e.completed(),e.deleted(),true));
 }
 synchronized void completed(UUID id,boolean cloudAndSqlCommitted){
  if(!cloudAndSqlCommitted)throw problem("SIGNING_RESULT_NOT_COMMITTED");var e=required(id);
  if(e.hadError()){write(new Entry(e.context(),e.receipt(),e.fileName(),e.size(),e.sha256(),true,e.error(),e.errorEvent(),e.errorAt(),e.receivedAt(),clock.instant(),true,e.deleted(),e.reported()));return;}
  try{Files.deleteIfExists(path(id,".pdf"));Files.deleteIfExists(path(id,".verified"));Files.deleteIfExists(path(id,".json"));}catch(IOException ex){throw problem("SIGNING_SPOOL_UNAVAILABLE");}
 }
 synchronized void delete(UUID id){
  var e=required(id);try{Files.deleteIfExists(path(id,".pdf"));}catch(IOException ex){throw problem("SIGNING_SPOOL_UNAVAILABLE");}
  write(new Entry(e.context(),e.receipt(),e.fileName(),e.size(),e.sha256(),e.hadError(),e.error(),e.errorEvent(),e.errorAt(),e.receivedAt(),clock.instant(),e.completed(),true,e.reported()));
 }
 synchronized List<Entry> list(){
  try(var stream=Files.list(root)){var result=new ArrayList<Entry>();for(var p:stream.filter(p->p.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.json")).sorted().toList())result.add(required(UUID.fromString(p.getFileName().toString().substring(0,36))));return List.copyOf(result);}
  catch(IOException ex){throw problem("SIGNING_SPOOL_UNAVAILABLE");}
 }
 private void write(Entry entry){try{atomic(path(entry.context().operation(),".json"),json.writeValueAsBytes(entry));}catch(IOException ex){throw problem("SIGNING_SPOOL_UNAVAILABLE");}}
 private void atomic(Path destination,byte[] bytes){
  Path temp=root.resolve(UUID.randomUUID()+".tmp");
  try{
   try(var channel=FileChannel.open(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){var b=ByteBuffer.wrap(bytes);while(b.hasRemaining())channel.write(b);channel.force(true);}
   try{Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   catch(AtomicMoveNotSupportedException e){Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);}
   // Directory fsync where available. Windows denies opening directories as channels.
   try(var directory=FileChannel.open(root,StandardOpenOption.READ)){directory.force(true);}catch(IOException|UnsupportedOperationException ignored){}
  }catch(IOException|SecurityException ex){throw problem("SIGNING_SPOOL_UNAVAILABLE");}
  finally{try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
 }
}
