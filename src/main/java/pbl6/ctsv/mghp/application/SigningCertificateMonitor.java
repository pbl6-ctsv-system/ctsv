package pbl6.ctsv.mghp.application;

import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

/** Read-only provider observation; startup/network failures never prevent the app starting. */
class SigningCertificateMonitor {
 final SigningCertificateService certificates;
 final java.util.concurrent.atomic.AtomicBoolean running=new java.util.concurrent.atomic.AtomicBoolean();
 final ExecutorService startup=Executors.newVirtualThreadPerTaskExecutor();
 SigningCertificateMonitor(SigningCertificateService certificates){this.certificates=certificates;}
 @EventListener(ApplicationReadyEvent.class) void started(){startup.submit(this::check);}
 @Scheduled(initialDelayString="PT24H",fixedDelayString="PT24H") void daily(){check();}
 void check(){if(!running.compareAndSet(false,true))return;try{certificates.checkSystem();}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Certificate monitoring deferred ({})",e.getClass().getSimpleName());}finally{running.set(false);}}
 @jakarta.annotation.PreDestroy void close(){startup.shutdownNow();}
}
