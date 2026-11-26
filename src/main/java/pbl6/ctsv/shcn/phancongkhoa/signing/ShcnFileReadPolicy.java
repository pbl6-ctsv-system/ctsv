package pbl6.ctsv.shcn.phancongkhoa.signing;

import java.util.Set;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shared.files.FileReadPolicy;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort;

/**
 * Authorizes private PDF downloads (/api/ctsv/tep/{id}/noi-dung) for SHCN faculty GVCN assignments:
 * - Faculty actors with read scope on the owning ma_khoa (when list != DA_HUY).
 * - CTSV specialists when the list has reached DA_GUI_CHUYEN_VIEN or DA_THAY_THE.
 */
public final class ShcnFileReadPolicy implements FileReadPolicy {
    private static final Set<String> SPECIALIST_READABLE_STATES = Set.of("DA_GUI_CHUYEN_VIEN", "DA_THAY_THE");

    private final PhanCongSigningRepository signingRepository;
    private final PhanCongKhoaFoundationPort foundation;

    public ShcnFileReadPolicy(PhanCongSigningRepository signingRepository, PhanCongKhoaFoundationPort foundation) {
        this.signingRepository = signingRepository;
        this.foundation = foundation;
    }

    @Override
    public boolean canRead(long fileId, FoundationAccess.Identity identity) {
        var owners = signingRepository.findFileOwners(fileId);
        if (owners.isEmpty()) return false;
        var actor = foundation.quyen(identity);
        for (var owner : owners) {
            boolean facultyScoped = actor.khoa().stream()
                    .anyMatch(k -> k.ma().equals(owner.maKhoa()) && k.doc() && k.dangHoatDong());
            if (facultyScoped) return true;
        }
        if (owners.stream().anyMatch(o -> SPECIALIST_READABLE_STATES.contains(o.trangThaiDanhSach()))) {
            return foundation.laChuyenVienDocNguon(identity);
        }
        return false;
    }
}
