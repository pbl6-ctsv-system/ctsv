package pbl6.ctsv.mghp.application;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component @ConditionalOnProperty(name={"ctsv.mghp.signing.enabled","ctsv.mghp.signing.worker.enabled"},havingValue="true")
class SigningScheduler {
 final SigningWorker worker;SigningScheduler(SigningWorker worker){this.worker=worker;}
 @Scheduled(initialDelayString="PT10S",fixedDelayString="PT30S") void tick(){worker.tick();}
}
