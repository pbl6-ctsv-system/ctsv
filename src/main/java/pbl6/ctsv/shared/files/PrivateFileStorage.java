package pbl6.ctsv.shared.files;

import java.nio.file.Path;
import java.util.Optional;

public interface PrivateFileStorage {
    record Asset(String id, long version) {}

    String cloud();
    default String publicId(String filename) { return filename; }
    Asset upload(String objectKey, Path file, long size);
    void download(String assetId, Path destination, long byteLimit);
    Optional<Asset> find(String objectKey);
}
