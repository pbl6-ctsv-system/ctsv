package pbl6.ctsv.shcn.phancongkhoa.signing;

import org.springframework.scheduling.annotation.Scheduled;

/** Periodic poller for queued/recoverable SHCN faculty assignment signing jobs when signing is enabled. */
public final class PhanCongSigningScheduler {
    private final PhanCongSigningWorker worker;

    public PhanCongSigningScheduler(PhanCongSigningWorker worker) {
        this.worker = worker;
    }

    @Scheduled(initialDelayString = "PT3S", fixedDelayString = "PT3S")
    public void poll() {
        worker.tick();
    }
}
