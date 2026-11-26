package pbl6.ctsv.mghp.application;
import org.springframework.scheduling.annotation.Scheduled;

/** Imports local failure evidence after SQL is available. Does not retry signing or file storage. */
class SigningSupportMaintenance {
 final SigningSupportJournal journal;
 SigningSupportMaintenance(SigningSupportJournal journal){this.journal=journal;}
 @Scheduled(initialDelayString="PT15S",fixedDelayString="PT30S") void reconcileEvidence(){
  try{journal.replay();}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Signing support evidence scan deferred ({})",e.getClass().getSimpleName());}
 }
}
