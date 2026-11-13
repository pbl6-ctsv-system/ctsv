# 01 — Công cụ và điều kiện trước setup

[Bắt đầu](README.md) · [Tiếp: cài đặt](02-installation-guide.md)

**Đã kiểm version bằng lệnh read-only ngày13/11; chưa cài thử máy mới.** Các bản patch dưới là baseline đã có, không phải yêu cầu tự nâng lên bản mới nhất trên trang tải.

| Công cụ | Vai trò / baseline | Nguồn cài | Kiểm tra và expected | Lỗi thường gặp |
|---|---|---|---|---|
| Windows x64 | Môi trường launcher/JDBC Windows Auth/DPAPI đã kiểm Windows11; build OS từ PowerShell5.1:26100.9818 | Công cụ hệ điều hành hiện có | `Get-CimInstance Win32_OperatingSystem` để đối chiếu máy mình | Linux/macOS chưa có launcher tương đương; không giả định hỗ trợ |
| JDK21 | Compile/run Boot; tác giả Temurin21.0.12.1 | [Temurin JDK21](https://adoptium.net/temurin/releases/?version=21) | `java -version`, `javac -version`; cả hai21, cùng JDK; `JAVA_HOME` trỏ JDK | PATH trỏ JRE/JDK khác; sửa môi trường trước build |
| Maven Wrapper | Cả ba repo pin Maven3.9.16, wrapper3.3.4 only-script | Có `mvnw.cmd` và `.mvn/wrapper/maven-wrapper.properties`; [Maven Wrapper](https://maven.apache.org/tools/wrapper/) | Root từng repo: `.\mvnw.cmd -v`; Maven3.9.16/Java21 | Lần đầu cần internet/proxy; không sửa distributionUrl để né lỗi |
| Spring Boot | POM ba backend3.5.16 | Maven resolve từ POM đã chia sẻ | Đọc parent version; không cần cài Boot riêng | POM ở HEAD cũ thiếu dependency mới |
| Node/npm | Tác giả Node24.22.1/npm11.19.1 | Chọn bản baseline từ [Node downloads/archive](https://nodejs.org/en/download) | `node -v`, `npm.cmd -v`; đối chiếu baseline | Nhiều Node trên PATH; không tự cập nhật lockfile |
| FE libraries | React19.2.0, TS5.9.3, Vite7.1.12, Rollup WASM4.68.2 | Hai package/lockfile dưới `frontend` và `frontend/e2e` | `npm.cmd ci`; sau đó `npm.cmd ls vite rollup`; alias Rollup phải là WASM | Thiếu lockfile; còn cache native Rollup bị SAC chặn |
| SQL Server2022 Developer x64 | Engine16.x, tác giả16.0.4315.2; compatibility160; collation Vietnamese_100_CI_AS_SC | [SQL Server2022 editions](https://learn.microsoft.com/en-us/sql/sql-server/editions-and-components-of-sql-server-2022); lấy bộ cài Developer chính thức Microsoft | SSMS: `SELECT @@VERSION,SERVERPROPERTY('InstanceName');` trên instance của bạn | Express/default instance không khớp guard dev hiện tại |
| JDBC auth DLL | JDBC runtime12.10.2, x64 DLL12.10.2.x64 | [Microsoft JDBC downloads](https://learn.microsoft.com/en-us/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server); chọn release khớp | `Test-Path` đường dẫn DLL ở phần dưới | Có JDBC jar nhưng thiếu DLL; Maven package không tự giải quyết bước này |
| PowerShell | Runtime đã kiểm7.6.9; terminal Windows PowerShell5.1 được entrypoint chuyển tiếp | [PowerShell trên Windows](https://learn.microsoft.com/en-us/powershell/scripting/install/installing-powershell-on-windows) | `pwsh -NoProfile -Command '$PSVersionTable.PSVersion'`; `Get-Command pwsh.exe` | PS5.1 không có pwsh/PATH; runtime cache của tác giả không dùng cho máy mới |
| Git | Tác giả2.51.2.windows.1; chỉ cần Git tương thích clone/status | [Git for Windows](https://git-scm.com/install/windows), không cần GitHub Actions | `git --version`, `git status` | Organization không phải repo cha; không `git clone` URL Organization |
| Browser | Chrome thực đã dùng157.0.8103.52; Web Locks/BroadcastChannel, HTTPS secure context | [Google Chrome](https://www.google.com/chrome/) | Mở DevTools Console: `'locks' in navigator`; expected true | Browser khác chưa có evidence tương đương |
| IDE / SSMS | IntelliJ/VS Code và SSMS tùy chọn; không điều kiện runtime | Nhà cung cấp chính thức | Có thể dùng terminal/SSMS để đọc metadata | Không cần copy `.idea` của tác giả |

OpenSSL **không bắt buộc**: tool hiện dùng `New-SelfSignedCertificate` và API .NET xuất PEM/PFX. Không thêm Docker, service Windows, CI hoặc Auth service.

## Những ràng buộc máy mới cần biết trước

1. Launcher hiện đọc chính xác registry `HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll`, trường `TcpDynamicPorts`. Đích phải là instance **DEV2022** trên local; không có fallback cổng tĩnh hoặc cấu hình instance tùy ý. Nếu máy khác, dừng và đề nghị tổng quát hóa ở vòng sau; không đổi guard sang DB test.
2. DLL được tìm ở `%USERPROFILE%\.m2\repository\com\microsoft\sqlserver\mssql-jdbc_auth\12.10.2.x64\mssql-jdbc_auth-12.10.2.x64.dll`. Đây là file **MANUAL**, không có trong Git hoặc dependency POM được khai báo trực tiếp. Sau khi tải/extract gói Microsoft đúng version/architecture, developer đặt DLL vào vị trí này; không copy DLL/private config từ máy tác giả. Không chạy binary bị Windows từ chối và không tắt SAC.
3. `Build-DevApplication` dùng Maven **`-o`**. Cache Maven trống chưa chạy setup được. Chuẩn bị online theo [02](02-installation-guide.md), không mang nguyên `.m2` chứa settings/credential của người khác.
4. Các script ghi `#requires7` ở tầng trong dùng API .NET mới. Bản đã kiểm là7.6.9; header của entrypoint5.1 không có nghĩa toàn bộ implementation tương thích .NET Framework. Không hứa mọi PowerShell7.0 đều chạy được.

## Quyền và bảo mật

- Cài SQL Server/công cụ hệ thống cần quyền cài đặt phù hợp. SQL Server Configuration Manager dùng khi bật TCP/IP/khởi động lại service, cần quản trị máy; lượt audit không thực hiện.
- Tài khoản Windows chạy setup phải có quyền tạo DB `ctsv_dev` và `sis_dev`, sau đó owner của DB phải chính là login hiện hành; guard kiểm equality và marker `ctsv_dev_owner`. Không dùng account/mật khẩu SQL của tác giả.
- Setup cấp ACL thư mục dev cho Windows SID hiện tại, dùng DPAPI CurrentUser. Không lấy config DPAPI của người khác vì không giải mã được và không thuộc ownership của bạn.
- Trust leaf trong **CurrentUser** sau khi review đúng chứng thư của bạn; không cần thêm Root CA, không cài vào LocalMachine như cách chữa mặc định. Có thể có hộp thoại Windows cần chính chủ bấm Yes.
- SQL JDBC local hiện có `encrypt=true;trustServerCertificate=true` như baseline; đây là giới hạn xác minh cert SQL riêng, không phải bỏ TLS cho HTTP/browser/proxy. HTTPS vẫn xác minh chứng thư.
- G8 P04 WAIVED theo chủ dự án; không tự thêm yêu cầu kiểm độc lập mới để chạy dev. Bàn giao máy mới vẫn cần kiểm thực tế, khác việc mở lại acceptance cũ.

Sync DEV dùng cùng JDK/Maven/auth DLL và leaf HTTPS của chính máy mình. Java consumer tin public leaf qua PKCS12 truststore do dev-sync setup sinh; Windows/Node trust không tự thay cho Java trust. Không cần framework/Root CA mới.
