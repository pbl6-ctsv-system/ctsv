---
name: ctsv-frontend
description: Thiết kế, triển khai và review React frontend CTSV dùng chung cho Auth, Foundation, MGHP, DRL, Học bổng và SHCN; áp dụng ranh giới module, UI tokens, accessibility và tích hợp API đã duyệt.
---

# Quy chuẩn frontend CTSV

**Baseline kiến trúc 0.1 được chủ dự án chấp nhận ngày 13/11/2026; bổ sung quy tắc UX theo owner22/11/2026 tại §12.** Cấu trúc app/shared/features, tokens và các trang Auth/Foundation được tách trong task refactor FE. Các mô tả thư mục dự kiến là baseline kiến trúc; đối chiếu source/route hiện hành để biết module nào đã triển khai, không suy MGHP hoặc module khác vẫn chỉ là thư mục giữ chỗ từ snapshot cũ. Giữ nguyên các quyết định kiến trúc, API và Auth đã APPROVED. Một yêu cầu áp dụng skill không tự cấp quyền sửa module ngoài task.

## 1. Cách sử dụng và phạm vi

- Member và AI đọc file này trước khi thiết kế/code/review FE CTSV. Trong prompt có thể ghi: “Đọc frontend/SKILL.md và áp dụng cho task này”. frontend/AGENTS.md nhắc Codex đọc file này khi làm việc tại frontend; đây không phải thao tác cài skill toàn cục cho mọi công cụ AI.
- Khi làm UI, đọc thêm [§12 — checklist UX theo vai trò](#ctsv-ux-review); áp dụng cùng [AGENTS.md §10](../AGENTS.md#ctsv-role-first-ux). Không dùng checklist để mở rộng scope hoặc bắt chạy mọi loại test cho một thay đổi nhỏ.
- Khi làm bố cục/câu chữ/trạng thái hiển thị, đọc [§13 — thiết kế thị giác có mục đích](#ctsv-visual-design). Đây là bổ sung cách thiết kế trong theme hiện hành, không là quyền đổi font/palette/Navbar hoặc xây nhận diện riêng cho module.
- Đọc AGENTS.md của repository và API Contract của chức năng được giao. Nếu nguồn đó chưa được chia sẻ, báo thiếu nguồn; không tự dựng API từ UI.
- Một React frontend chung, mỗi member phát triển module được giao. Auth/Foundation/shared/app là phần chung, không thuộc riêng MGHP.
- Không tự thiết kế nghiệp vụ, tạo trang, route chạy thật hoặc skeleton của DRL/Học bổng/SHCN khi chưa được giao. Tên trong sơ đồ dưới chỉ xác định ranh giới tương lai.
- Giữ React 19.2.0, TypeScript 5.9.3, Vite 7.1.12 và Rollup WASM theo package/lockfile hiện hành. Không tự thêm UI library, router, state manager, icon pack hoặc nâng phiên bản.

## 2. Cấu trúc đích

```text
frontend/
├── SKILL.md
├── package.json / package-lock.json / vite.config.ts / tsconfig.json
├── public/                       # file static công khai; tuyệt đối không có secrets
├── src/
│   ├── main.tsx                  # mount ứng dụng; không chứa nghiệp vụ
│   ├── app/
│   │   ├── App.tsx               # ghép providers, layout và vùng nội dung
│   │   ├── layouts/              # AppShell, header/sidebar/navigation dùng chung
│   │   ├── providers/            # một Auth state chung và providers thực sự cần
│   │   └── navigation/           # đăng ký entry/menu/route của các module
│   ├── shared/
│   │   ├── ui/                   # Button, Field, Table, Dialog... khi có nhu cầu
│   │   ├── auth/                 # auth-client hiện có, trạng thái/quyền dùng chung
│   │   ├── api/                  # transport/error handling dùng chung
│   │   ├── styles/               # tokens.css, reset.css, global.css tối thiểu
│   │   ├── hooks/                # hooks kỹ thuật đã có nhu cầu dùng chung
│   │   ├── utils/                # format ngày/số...; không chứa luật nghiệp vụ
│   │   └── types/                # kiểu kỹ thuật dùng chung, không gom mọi DTO
│   └── features/
│       ├── auth/                 # UI đăng nhập/quản trị Auth dùng chung
│       ├── foundation/           # UI/API tra cứu và quản trị dữ liệu nền tảng
│       ├── mghp/                 # phần việc MGHP
│       ├── drl/                  # vị trí dự kiến, member phụ trách tự triển khai
│       ├── hocbong/              # vị trí dự kiến, member phụ trách tự triển khai
│       └── shcn/                 # vị trí dự kiến, member phụ trách tự triển khai
├── tests/                        # kiểm thử dùng chung hiện có
└── e2e/                          # browser tests và dependency riêng hiện có
```

Các thư mục giữ chỗ được tạo theo yêu cầu của owner có .gitkeep để Git giữ lại; không xem chúng là feature đã triển khai. Khi phát triển tiếp, chỉ thêm file/thư mục có nhu cầu thực tế. Mỗi feature có thể gồm `pages/`, `components/`, `api/`, `hooks/`, `types/`, `assets/`, file CSS cạnh component và `index.ts` làm entry công khai; feature nhỏ không cần đủ mọi thư mục.

Ví dụ `features/mghp/pages/ApplicationListPage.tsx`, `features/mghp/api/applications.ts`, `features/mghp/components/ApplicationFilters.module.css`. Không bắt member khác sửa file này để làm trang DRL.

### Hướng phụ thuộc

- `app` → entry công khai của các feature và `shared`.
- Feature → phần nội bộ của chính nó và `shared`. Không import trực tiếp nội bộ feature nghiệp vụ khác.
- `shared` không import `app` hoặc feature; không chứa điều kiện riêng kiểu “nếu là MGHP thì...”.
- Khi module cần dữ liệu Foundation, dùng API/hook công khai do Foundation cung cấp, chẳng hạn entry rõ ràng tại `features/foundation/index.ts`; không import trang/entity/state nội bộ. Đây là ngoại lệ phụ thuộc dùng chung có chủ đích, không cho phép phụ thuộc vòng.
- `index.ts` chỉ xuất entry/hợp đồng cần thiết, không export toàn bộ file. Import nội bộ feature trực tiếp để tránh vòng và gom cả module vào bundle ngoài ý muốn.
- Auth UI dùng auth-client/state chung; mỗi module không tự tạo cơ chế đăng nhập hoặc JWT riêng.

## 3. Ownership và tích hợp

| Loại thay đổi | Cách làm |
|---|---|
| Trang/component/API adapter/CSS riêng | Sửa trong feature được giao; thêm test phù hợp |
| Component UI/common hook mới | Dùng chung khi đã có nhu cầu rõ; giải thích consumers, không tạo abstraction dự phòng |
| Shared/App/Auth/Foundation, navigation, tokens | Nêu ảnh hưởng và phối hợp member liên quan trước tích hợp; chỉ sửa trong phạm vi được giao |
| package.json/lockfile/Vite/TypeScript | Tài nguyên chung; không đổi để tiện riêng một module khi chưa được thống nhất |
| Feature của member khác | Báo dependency hoặc lỗi; không sửa thay để test của mình xanh |

`App.tsx`/navigation/theme vẫn là điểm tích hợp chung, nên không thể hứa hoàn toàn không có Git conflict. Giữ cấu hình đăng ký ngắn, entry độc lập và tích hợp từng thay đổi nhỏ. Không cần microfrontend, module federation hoặc bốn frontend/build riêng.

Đường dẫn màn hình dự kiến có namespace `/mghp`, `/drl`, `/hoc-bong`, `/shcn`; chỉ đăng ký khi task/thiết kế màn hình được giao. Đây không phải URL API và không tự quyết định thư viện router. Hiện dự án chưa khai báo router dependency.

## 4. Màu sắc và design tokens

Kế thừa màu hiện tại. Khi triển khai theme, đặt biến tại `shared/styles/tokens.css`; component sử dụng tên biến, không tự chọn màu theo từng module. Bảng sau là baseline đã đặt tại shared/styles/tokens.css.

| Token đề xuất | Giá trị | Công dụng |
|---|---|---|
| --color-primary | #086ca8 | CTA, nền Navbar, mục navigation đang chọn |
| --color-text | #18322f | Nội dung chính |
| --color-text-muted | #657773 | Chú thích, nội dung phụ |
| --color-background | #f1f5f3 | Nền ứng dụng |
| --color-surface | #ffffff | Card, form, dialog |
| --color-surface-muted | #f7faf8 | Tiêu đề bảng, vùng phụ |
| --color-border | #dae5de | Viền, divider |
| --color-control-border | #b9cec5 | Input/select |
| --color-secondary-bg | #e4eeea | Nút phụ |
| --color-secondary-text | #285446 | Chữ nút phụ |
| --color-danger | #9e3d39 | Hành động hủy/xóa có hậu quả |
| --color-error-text / --color-error-bg | #8d392a / #fff0ed | Thông báo lỗi |
| --color-success | #086ca8 | Trạng thái thành công |
| --color-warning | #8a5700 | Cần chú ý, chờ xử lý |
| --color-info | #1d4ed8 | Thông tin trung tính |
| --color-focus | var(--color-primary), hiện là #086ca8 | Focus bàn phím rõ trên nền sáng |

Cập nhật owner 25/11/2026: đổi màu #176b55 sang #086ca8 cho primary, focus và success; Navbar dùng token chung để giữ đồng nhất với nút chính và vùng chọn.

Mỗi module dùng cùng palette; phân biệt bằng tên/menu/icon nếu cần, không đổi toàn bộ theme. Success/error/warning phải có chữ hoặc biểu tượng kèm theo, không chỉ dựa vào màu. Kiểm contrast trên tổ hợp màu thực tế; bảng màu này không phải bằng chứng đã đạt accessibility cho mọi trường hợp. Chưa triển khai dark mode hoặc công tắc theme trong task này.

## 5. Typography, khoảng cách và layout

- Tiếng Việt có dấu cho nội dung người dùng. Font ưu tiên `"Segoe UI", system-ui, sans-serif`; không tải font ngoài nếu chưa có nhu cầu/thống nhất. Code/ID kỹ thuật mới dùng monospace.
- Body baseline 15px, line-height 1.5–1.6; chú thích 13px; H1 28–34px, H2 22px, H3 18px. Chỉ một H1 mỗi trang, không chọn heading theo kích thước chữ.
- Spacing scale: 4, 8, 12, 16, 24, 32, 40px; khai báo token khi làm theme. Radius: control 6px, nhóm 8px, card 14px, kế thừa UI hiện tại. Shadow nhẹ, tránh nhiều lớp card/shadow không cần thiết.
- AppShell chung giữ header/navigation/nội dung. Feature trả nội dung trang; không dựng lại header/sidebar/logout riêng cho từng module.
- Trang danh sách: tiêu đề + mô tả ngắn → action chính → filters → trạng thái/kết quả → phân trang nếu API hỗ trợ. Trang chi tiết chia nhóm thông tin và action rõ ràng.
- Desktop ưu tiên tối đa 1200px cho trang form/thông tin; trang bảng có thể rộng hơn khi nội dung cần. Padding khoảng 24–28px, mobile 14–16px. Form một hoặc hai cột theo không gian thực; không ép chiều rộng cố định.
- Nội dung reflow ở khoảng 650px trở xuống như baseline hiện tại; kiểm thêm viewport 360px và desktop 1280px. Chỉ vùng bảng được scroll ngang khi cần, không để toàn trang tràn ngang.
- Không thêm banner/trang trí làm giảm diện tích xử lý công việc. Không để thông tin kỹ thuật DB/JWT/ETag xuất hiện trong UI người dùng thường nếu không phục vụ tác vụ của họ.

## 6. CSS và component dùng chung

- CSS toàn cục chỉ dành cho tokens/reset/base typography. Không viết selector toàn cục `button`, `section`, `header`, `nav` trong module mới như stylesheet hiện tại.
- CSS riêng dùng `*.module.css` cạnh component; selectors/animation/asset không tác động module khác. Không override CSS của module khác, không lạm dụng `!important` hoặc inline style.
- Shared UI là component trình bày: Button/Field/Dialog/Table...; không tự gọi API nghiệp vụ. Feature quyết định dữ liệu và thao tác.
- Button thống nhất primary/secondary/danger, có hover/focus/disabled/loading. Mỗi vùng tác vụ ưu tiên một CTA primary. Nút hủy/xóa cần nội dung rõ; xác nhận khi thao tác có hậu quả đáng kể.
- Field có label thực, required/error/helper text liên kết bằng `id`/`aria-describedby`. Placeholder không thay label; lỗi field không chỉ đổi viền đỏ. Submit giữ dữ liệu người dùng nếu request thất bại.
- Bảng có header ngữ nghĩa, cột nội dung/action nhất quán, trạng thái có nhãn. Không tự phân trang/sort giả trên một trang dữ liệu lấy từ server rồi trình bày như toàn bộ kết quả.
- Dialog có tên truy cập, focus được quản lý và trả về trigger khi đóng; thao tác bàn phím dùng được. Không dùng clickable div thay button/link.
- Không thêm icon/font package riêng từng module. Nếu cần icon, dùng bộ đang thống nhất hoặc SVG nhẹ được review; nút chỉ có icon phải có accessible name.

## 7. State, API và bảo mật

- State form/filter/selection ở feature; chỉ auth/nhận diện và trạng thái thực sự dùng chung đặt ở app/shared. Không tạo global store cho mọi dữ liệu.
- Auth client chuẩn ở src/shared/auth/auth-client.mjs. src/shared/auth-client.mjs chỉ re-export tương thích cho integrations/tests cũ; không thêm logic Auth thứ hai ở đường dẫn cũ.
- API riêng đặt trong `features/<module>/api`; kiểu request/response cạnh adapter. UI không tự xây endpoint, error code, role hay DTO trái contract.
- Trước khi tạo transport dùng chung, đối chiếu auth-client đã có; không tạo hai lớp retry/refresh chồng nhau. Giữ tương thích chữ ký và hành vi đang được kiểm thử.
- Access Token ở memory; Refresh Cookie Secure/HttpOnly, rotation và logout cookie-only. Giữ CSRF/CORS, Web Locks và xử lý mất response; module không tự refresh song song hoặc retry refresh bằng cookie cũ.
- Không lưu JWT/refresh/password vào localStorage, URL, log, toast, screenshot hoặc shared state phát ra giữa tab. Không đặt private key/service key/SQL credentials trong FE hoặc biến VITE_*.
- Frontend ẩn/chặn thao tác theo capability đã có để dễ dùng; backend vẫn là nơi quyết định quyền/ownership và online verification. Không suy quyền từ tên module, chức danh hay việc menu đang hiện.
- Đợi quyền hiện hành từ backend trước khi hiển thị mục hoặc nút cần quyền. Dùng cùng điều kiện cho menu, màn hình và đường điều hướng trực tiếp. Khi đổi tài khoản hoặc quyền, làm mới phạm vi hiển thị và bỏ lựa chọn cũ nếu quyền mới loại trừ. Kiểm bằng tài khoản được cấp quyền và tài khoản thiếu quyền.
- Mỗi màn hình có loading, empty, error, forbidden và success khi áp dụng. Phân biệt danh sách rỗng với chưa tải hoặc request lỗi; feedback lưu nằm gần thao tác, không chỉ một toast thoáng qua.
- 401/thu hồi token đi qua xử lý Auth chung; 403 hiển thị thiếu quyền; lỗi validation gắn field nếu contract cung cấp; lỗi DB/integration không lộ stack trace. Không đổi mọi lỗi thành “không có dữ liệu”.
- Giữ ETag/If-Match theo contract; version conflict yêu cầu đọc lại/đối chiếu, không tự ghi đè hoặc retry write mù. Client validation hỗ trợ người dùng, không thay validation server.
- Sau lệnh ghi/chuyển trạng thái thành công, đồng bộ các vùng bị ảnh hưởng (chi tiết, danh sách, phiên bản, count/capability nếu có) từ Backend trước khi báo thành công. Giữ lựa chọn và ETag đúng representation; lỗi tải lại vẫn qua xử lý lỗi hiện có, không phát lại lệnh ghi. Test browser phải kiểm cả chi tiết lẫn dòng/nhãn danh sách ngay sau thao tác, không bấm tải lại để che state cũ. Hồi quy P05: `e2e/p05-policy-confirmation.mjs`.
- Chỉ hiển thị dữ liệu cá nhân cần cho tác vụ/quyền; không log response hồ sơ, CCCD, minh chứng hoặc token. Không đưa dữ liệu thật vào fixtures/ảnh demo.

## 8. Quy ước code và kiểm chứng

- Component/page: PascalCase `.tsx`; hook `useX.ts`; helpers/API: tên diễn đạt rõ `.ts`; giữ `.mjs` hiện có đến khi có task chuyển đổi được duyệt. Nội dung UI tiếng Việt; không tự đổi JSON field/API vì naming code.
- Một component có trách nhiệm rõ; tách khi có state/luồng độc lập hoặc cần dùng lại, không đặt giới hạn số dòng máy móc. Không dồn login/admin/tra cứu và mọi màn hình nghiệp vụ vào một component.
- Request độc lập có thể chạy song song; kết quả tìm kiếm cũ không được ghi đè kết quả mới. Không thêm memoization/state library hoặc download dữ liệu hàng loạt khi chưa có lý do.
- Tests riêng đặt cạnh feature hoặc theo bố cục test mà project cấu hình hỗ trợ; tests shared kiểm component/transport chung. Không chỉ đưa file vào thư mục rồi mặc nhiên nói test runner đã chạy nó.
- Cwd `frontend`: `npm.cmd run build` kiểm TypeScript/build; `npm.cmd test` hiện chỉ chạy `tests/*.test.mjs`. TSX/browser tests mới cần runner hiện có phù hợp hoặc thiết kế test tooling riêng được thống nhất; không tự thêm dependency.
- Tái dùng E2E hiện có khi thay đổi Auth/AppShell/Foundation. Kiểm điều hướng, bàn phím, viewport, loading/empty/error và quyền của màn hình bị ảnh hưởng; không chạy lại toàn P00–P04 cho thay đổi thuần tài liệu.
- Chọn đích Playwright theo [AGENTS.md §8](../AGENTS.md#8-bàn-giao-dev-cho-mỗi-slice--15112026). Mặc định smoke luồng hằng ngày trên `ctsv_dev` qua `.\dev.ps1 start`. Dùng DB test riêng cho các ca cần trạng thái cố định hoặc có thao tác phá hủy.
- Chỉ báo PASS với lệnh, số ca và kết quả thực tế; phân biệt mock với browser/API thật. Không đổi business assertion để hợp thức hóa refactor.

## 9. Mở rộng từ cấu trúc hiện tại

1. Làm việc trong features/<module>/pages, components, api, hooks, types, assets của module được giao. Không copy toàn bộ App/Auth/Foundation để có màn hình riêng.
2. Xuất page/entry cần tích hợp ở features/<module>/index.ts; không import app từ feature.
3. Khi module sẵn sàng, phối hợp thêm entry public vào app/navigation/featureRegistry.tsx và menu/view vào modules.mjs. App chỉ render feature đã đăng ký, không tự tạo route/API cho thư mục rỗng.
4. Page entry có props client (Auth client dùng chung) và view (màn hình trong feature), kiểu FeaturePageProps ở shared/types/module.ts. UI tiếp tục dùng shared components/tokens; tên trang/header mới cần đặt ở điểm tích hợp phù hợp, không sửa AppShell thành logic nghiệp vụ.
5. Module có thể có tests riêng; chạy đúng test runner. Giữ các regression Auth và module khác bị ảnh hưởng; không commit/push chỉ vì đọc skill này.
6. Đăng ký đường dẫn, quyền và nhóm của mỗi trang trong `app/navigation/modules.mjs`. Navbar và truy cập URL trực tiếp cùng dùng cấu hình đó. Trang chủ dùng `/`, nhóm MGHP dùng `/mghp/…`, Dữ liệu nền dùng `/du-lieu-nen/…`, Quản trị dùng `/quan-tri/…` và Thông báo dùng `/thong-bao`. Các mục đang phát triển chỉ là nhãn cho sinh viên. Khi phục vụ build SPA ngoài Vite DEV, web server cần trả `index.html` cho route con frontend để tải lại trang.

## 10. Checklist review mỗi thay đổi FE

- Đúng feature được giao, hướng import đúng và không phụ thuộc vòng?
- Tài nguyên chung thay đổi đã nêu ảnh hưởng/được phối hợp đúng phạm vi?
- Dùng tokens/components chung, CSS có cô lập, không đổi theme riêng?
- Có label/focus/keyboard, mobile và đủ trạng thái dữ liệu?
- API/validation/quyền/ETag đúng contract, Auth/TLS không bị nới lỏng?
- Không thêm thư viện, token storage hoặc dữ liệu nhạy cảm ngoài quyết định đã chốt?
- Build/test phù hợp đã chạy thật, những phần chưa kiểm được ghi rõ?

File này không thay thế nghiệp vụ/API Contract của từng module hoặc cấp quyền triển khai thay member khác.

## 11. Thiết kế theo tác vụ — áp dụng sau review UI ngày 18/11/2026

- Trước khi dựng màn hình, xác định người dùng, việc chính và bước tiếp theo. Danh sách, chi tiết và form tạo/sửa là các trạng thái rõ ràng. Khi mở form dài, thu lại danh sách và tác vụ không liên quan. Có đường quay về dễ thấy, giữ lựa chọn và dữ liệu nhập khi lỗi.
- Cùng dữ liệu nhưng khác tác vụ phải có tên menu và màn hình rõ theo capability. “Hồ sơ MGHP” của sinh viên để nộp/theo dõi tách khỏi “Tiếp nhận & bổ sung hồ sơ” của chuyên viên. Hai tác vụ có route/guard riêng, vẫn có thể dùng cùng component/API hợp lệ; người có cả hai capability được truy cập từng tác vụ, không để việc chọn view theo một vai trò che mất tác vụ còn lại. Không suy quyền từ tên role hoặc tên menu.
- Mỗi vùng chỉ có một nút chính. Nút đọc lại/tải tài liệu/chuyển bước là secondary. Nút rút/hủy/khóa là danger, đặt trong vùng tác vụ riêng, nêu hậu quả và yêu cầu lý do theo contract. Mọi button trong form phải khai báo type; phím Enter chỉ gửi đúng form đang thực hiện.
- Form dài chia nhóm hoặc bước có tên nghiệp vụ. Giữ state khi chuyển bước, kiểm đủ các phần trước khi gửi và mở đúng phần có lỗi. Form con không được lồng trong form khác. Field chỉ đọc trình bày như thông tin, không dựng hàng loạt input disabled chỉ để hiển thị.
- Desktop dùng một hoặc hai cột, gap 16–24px. Căn hàng các label/control; nút tác vụ nằm cùng nhóm. Mobile 360px một cột; các button, UUID và tên dài không làm tràn trang. Không tạo card/padding/shadow lồng vô hạn cho mọi section.
- Các thao tác phụ như gia hạn, nhập mã tham khảo, cấu hình chi tiết hoặc kiểm một giấy phải được mở theo nhu cầu. Trạng thái và hạn cần xử lý vẫn thấy ngay. Bảng đối chiếu dùng cột có nhãn; lịch sử dùng dòng thời gian, tài liệu có tên dễ đọc.
- Lỗi request phải xuất hiện trong viewport ngay cả khi đã cuộn xuống cuối form. Dùng Alert/FeedbackProvider chung. Lỗi không tự mất theo timer; người dùng có thể đóng bằng bàn phím. Không tự đưa focus ra khỏi field khi có lỗi mạng. Lỗi field phải giữ/mở đúng phần để sửa; thông báo thành công cần gần tác vụ.
- Tên và nhãn hướng đến người dùng. Hiển thị ngày theo vi-VN, trạng thái có chữ, UUID/mã kỹ thuật ở thông tin tham khảo. Với danh mục đã có API, cung cấp tên gợi ý/chọn và giữ ID trong request. Không ép người dùng đoán mã hoặc tạo API giả khi thiếu nguồn.
- Dữ liệu mẫu đặt tên có ý nghĩa, ví dụ “Đợt thực hành bổ sung hồ sơ”, mô tả rõ đây là thực hành. Mã chạy test/epoch nằm ở phần tham khảo. Chỉ đổi cách trình bày cho mẫu nhận diện chắc chắn; tên thật và mã đối chiếu phải giữ nguyên. Không suy học kỳ, người, quyền hoặc căn cứ pháp lý từ một mã test.
- API/quyền/ETag và Auth client giữ nguyên trong thay đổi UI. Trạng thái pending chặn gửi lặp; sau success đọc lại các vùng liên quan. Không dùng tải lại toàn trang, bỏ validation hoặc đổi expected để che lỗi nút/request.
- Kiểm bằng browser thật theo tác động trước bàn giao. Khi sửa hành vi/tác vụ/quyền, chọn luồng ghi hợp lệ, lỗi backend sau khi cuộn, tài khoản thiếu quyền, giữ dữ liệu qua chuyển bước, refresh route và mobile liên quan; kiểm request/status và kết quả đã lưu. Chỉ sửa nhãn thì kiểm đúng ngữ cảnh/đường điều hướng bị tác động, không tạo hồ sơ hoặc upload lại vô cớ. Với thay đổi bố cục, xem ảnh desktop/mobile. Giữ các gate bắt buộc theo phạm vi, ghi rõ SQL/HTTP/storage REAL hay STUB; không đồng nhất build xanh với UX đạt.

<a id="ctsv-ux-review"></a>

## 12. Checklist UX theo vai trò — bài học owner chốt22/11/2026

### Trước khi dựng hoặc sửa màn hình

- Ghi ngắn: **ai dùng/capability → mục đích vào trang → thông tin cần để quyết định → hành động chính → bước tiếp theo/kết thúc → cách phục hồi khi lỗi**. Dùng tình huống của người dùng thật, không chỉ danh sách endpoint. Không cần thêm tài liệu hay vòng xin duyệt cho lựa chọn nhỏ đã được giao.
- Với từng field, xác định người dùng có cần nhập và có hiểu được không. Dữ liệu đã có/đã ràng buộc ngữ cảnh thì hiển thị tên hoặc tự lấy khi xác định duy nhất; nhiều lựa chọn thì cho chọn có ý nghĩa hoặc báo thiếu nguồn, không đoán. Không yêu cầu gõ ID học kỳ phân cách dấu phẩy, ID tệp, mã vòng hoặc object cấu hình trong form nghiệp vụ thông thường.
- Với từng nút, trả lời được tác động, điều kiện xuất hiện, kết quả nhìn thấy và đường đi khi thất bại. Không thêm nút chỉ vì API có sẵn, không đổi nhãn nhưng giữ hành động cũ khác nghĩa. Không tự tạo tính năng/route hoặc bỏ yêu cầu Backend chỉ để giao diện gọn.
- Tự rà lại màn hình hoàn chỉnh sau khi ghép component: bỏ thông tin lặp, gom các mốc liên quan, đặt đối chiếu/PDF gần thao tác xử lý, đưa chi tiết kỹ thuật/lịch sử phụ ra khỏi luồng chính. Giữ dữ liệu lịch sử ở backend; ẩn một khối UI không có nghĩa xóa dữ liệu.

### Trong hành trình sử dụng

| Điểm phải kiểm | Quy tắc |
|---|---|
| Vai trò và ngữ cảnh | Cùng dữ liệu có thể phục vụ tác vụ khác nhau. SV nộp/theo dõi own; CV đọc căn cứ, xử lý, kết luận. Dùng capability backend đã xác minh, kể cả nhiều quyền/direct URL; không cấp quyền nghiệp vụ bằng tên role. |
| Danh sách/tạo/xem | Tiêu đề và action đúng ngữ cảnh; lọc danh sách không chen vào form tạo. Trang xem đợt có đường bắt đầu nộp khi đủ điều kiện. Trạng thái kết thúc hiển thị dữ liệu/hành động theo contract, không để người dùng đoán bước tiếp theo. |
| Hành động và trạng thái | Phân biệt đồng ý một PDF, duyệt hồ sơ, đánh giá quyền hưởng và công bố. Sau ghi/đọc lại, cập nhật mọi vùng liên quan từ server, kể cả detail con có ID giữ nguyên nhưng ETag thay đổi. Lỗi không được đổi nhãn hoặc chuyển bước như đã thành công. |
| Quy trình nhiều lần | Đi thử cả tạo yêu cầu, phản hồi, chưa đáp ứng, yêu cầu tiếp và kết thúc. Một vòng đang hoạt động không có nghĩa chỉ được một lần trong suốt hồ sơ. Nút bị chặn cần lý do và đường xử lý hợp lệ; không tạo vòng song song hoặc giả hoàn tất. |
| Thông báo và sửa lỗi | Feedback xuất hiện trong viewport sau khi cuộn, có nguyên nhân/cách tiếp tục; giữ input và mở đúng nhóm lỗi. Không chỉ hiện mã lỗi thô hoặc thông báo thành công sau request bị lỗi. Không ép lý do cho mọi hành động nếu contract chỉ cần ở trường hợp từ chối/hủy. |
| PDF và danh sách lựa chọn | Bấm mục nào xem đúng bytes của file gắn mục đó; hỗ trợ n mục, kể cả nhiều giấy dùng chung file. Chọn rõ mục hiện tại và nhãn đã/chưa xử lý bằng chữ, không chỉ màu. Nếu có tự chuyển, chỉ chuyển sau lưu thành công; bỏ qua mục đã xử lý, không vòng lặp vô hạn. Đổi nhanh không được để response cũ ghi đè lựa chọn mới. |

### Bằng chứng đủ để bàn giao phần đã sửa

- Chọn ca theo tác động: tái hiện lỗi → kiểm đúng ca sau sửa → luồng thành công/lỗi/quyền liên quan → hồi quy cuối có mục tiêu, theo AGENTS.md §9. Kiểm locator trên DOM trước luồng dài; đọc lại trạng thái sau timeout/HMR trước khi gửi thêm lệnh ghi. Không đổi expected HTTP trái contract hoặc force-click để che lỗi.
- Assertion phải kiểm nội dung/kết quả: đúng hồ sơ của tài khoản, đúng file của lựa chọn, nhãn/count/chi tiết khớp sau thao tác, lưu và đọc lại được. `200/201`, nút có request, iframe tồn tại hoặc một screenshot không tự chứng minh UX đúng. Ảnh bố cục phải được xem, không chỉ tạo file.
- Dataset synthetic phải dễ đọc và đủ phân biệt tình huống đang kiểm: ví dụ kiểm đổi PDF cần nội dung khác nhau; kiểm nhiều chủ sở hữu phải có dữ liệu của nhiều chủ. Không suy privacy từ dataset một người hoặc tính năng n mục từ việc chỉ chạy một mục. Mã test/UUID để ở tham khảo, không thành tiêu đề người dùng phải hiểu. Không sửa dữ liệu mẫu/DB ngoài phạm vi được phép.
- Áp dụng DEV/test DB và REAL/STUB theo runbook; harness PASS chưa chứng minh JAR/profile/DB DEV hằng ngày. Chỉ kiểm dịch vụ ngoài thật khi luồng bị ảnh hưởng; không upload lại cho ca điều hướng. Báo số ca, phạm vi và FAIL/BLOCKED/NOT VERIFIED rõ ràng; không tự tuyên bố owner hài lòng/ACCEPTED từ kết quả kỹ thuật.

### Quyết định MGHP đã chốt cần giữ đúng phạm vi

- Nội dung PDF: không có nút Từ chối PDF/ô nhận xét PDF trong luồng đơn giản; PDF chưa phù hợp đi Yêu cầu bổ sung. Đồng ý thành công có nhãn và tự chuyển giấy chưa đồng ý tiếp theo. Lịch sử kiểm vẫn lưu; không tự xác nhận hình thức/thời hạn/chủ thể/bản cứng thay chuyên viên.
- HS08: đồng ý hồ sơ không bắt lý do; từ chối bắt lý do. Phản hồi bổ sung general có đường xử lý xong hoặc yêu cầu tiếp, giữ bản nộp cũ và một vòng active. Đối chiếu contract/source hiện hành trước sửa, không phục hồi UI cũ chỉ vì thấy field/API lịch sử còn tồn tại.
- Những quyết định này không tự áp cho DRL/SHCN/Học bổng hoặc mở phần P17 còn hoãn. Mọi thay đổi contract/nghiệp vụ vẫn theo quyền owner đã giao.

Trước khi báo hoàn thành, tự đi theo vai trò: **người dùng có tự tìm được chức năng, biết cần nhập gì, hiểu nút sẽ làm gì, thấy đúng kết quả và biết làm tiếp khi lỗi không?** Nếu còn điểm chưa kiểm hoặc chưa rõ, báo đúng phần đó; không để owner phải khám phá toàn bộ đường sử dụng sau lời báo PASS.

<a id="ctsv-visual-design"></a>

## 13. Thiết kế thị giác có mục đích — owner22/11/2026

Tiếp thu có chọn lọc từ tài liệu tham khảo `frontend-design` do owner cung cấp. Phục vụ ứng dụng nghiệp vụ CTSV và các lựa chọn đã duyệt tại §4–6; không sao chép yêu cầu làm website quảng bá, đổi nhận diện hoặc gây ấn tượng bằng trang trí.

### Phác bố cục rồi đối chiếu yêu cầu

- Với màn hình mới hoặc bố cục thay đổi lớn, phác bằng vài dòng hoặc wireframe ASCII: vùng nhận diện/ngữ cảnh, thông tin cần quyết định, vùng xử lý chính, chi tiết phụ và vị trí feedback. Nêu phần nào được ưu tiên và cách đọc trên mobile. Dùng dữ liệu/độ dài tên sát nghiệp vụ, không chỉ placeholder ngắn đẹp giả tạo.
- Đối chiếu phác thảo với tác vụ/role và giao diện hiện hành trước khi code: đã đủ thông tin cần thiết, thao tác tiếp có dễ tìm, có phần lặp hoặc cấu trúc mẫu áp vào không phù hợp không? Chỉnh phác thảo nếu cần. Kế thừa tokens/font/component hiện có; không tạo palette/type system mới. Sửa nhãn hoặc vùng nhỏ không cần quy trình phác thảo riêng; không thêm gate phê duyệt ngoài yêu cầu owner.

### Phân cấp và tiết chế

- Chọn trọng tâm thị giác theo tác vụ: trong trang xử lý hồ sơ, ưu tiên trạng thái, nội dung cần kiểm và hành động tiếp theo. Dùng vị trí, cỡ/độ đậm chữ, khoảng cách và mức nhấn của nút để phân cấp; không cho mọi thông tin cùng trọng lượng hoặc biến mọi số lượng thành KPI nổi bật.
- Mỗi khung, đường chia, nhãn và số thứ tự phải giúp hiểu nội dung. Ưu tiên khoảng cách cho nhóm đơn giản; dùng card cho vùng độc lập, số thứ tự cho danh sách/thứ tự/tiến trình cần phân biệt. Không bọc mọi đoạn trong card giống nhau hoặc thêm nhãn trên tiêu đề chỉ để trang trông đầy. Không tự xóa kiểu Navbar chữ hoa hay bố cục owner đã chấp nhận vì mẫu tham khảo khuyên khác.
- Typography theo thang chữ và font §5, căn hàng nhất quán. Đoạn hướng dẫn dài nên có chiều rộng dễ đọc (tham khảo dưới khoảng80ký tự mỗi dòng), không áp giới hạn đó máy móc cho bảng/PDF hoặc ép ngắt chuỗi dữ liệu. Giữ tiếng Việt có dấu rõ, tên dài/nhãn lỗi không phá bố cục. Không đổi typeface để tạo cá tính khi chưa được giao.

### Câu chữ và chuyển động giúp hiểu thao tác

- Dùng một thuật ngữ cho cùng hành động xuyên suốt nút, dialog, thông báo và lịch sử; phân biệt các khái niệm khác nhau như công khai đợt/công bố kết quả. Ví dụ: “Gửi yêu cầu bổ sung” → “Đã gửi yêu cầu bổ sung”. CTA nói rõ kết quả phù hợp ngữ cảnh, tránh “Submit”, “OK”, “Xử lý” khi người dùng không biết tác động. Nội dung ngắn, động từ rõ, không thêm khẩu hiệu/nhãn trang trí vào màn hình nghiệp vụ.
- Empty/error state có hướng đi đúng quyền: giải thích chưa có dữ liệu hay lỗi tải, cung cấp thao tác có thật nếu hợp lệ; không mời tạo mới khi thiếu quyền, không biến lỗi thành danh sách rỗng. Kế thừa feedback/giữ input tại §7/§12, không chỉ viết câu nghe thân thiện nhưng không giúp xử lý.
- Chuyển động chỉ dùng khi làm rõ tương tác: mở/thu nhóm, đổi lựa chọn, phản hồi sau khi lưu. Nếu tự chuyển sang giấy tiếp theo, trạng thái mục cũ và lựa chọn mới phải rõ; chuyển động không thay bằng chứng lưu thành công. Hạn chế chuyển động tự chạy và hiệu ứng lặp trên từng card; tôn trọng `prefers-reduced-motion`, không để animation chặn thao tác hoặc là cách duy nhất truyền thông tin. Không thêm thư viện animation cho nhu cầu nhỏ.

### Review trên giao diện thực

- Rà độ ưu tiên CSS trong cùng component, nhất là tổ hợp selected/hover/focus/disabled/pending: selector không được vô tình triệt tiêu padding, màu hoặc dấu trạng thái. Dùng CSS Modules, tokens và state/variant rõ theo §6; không tăng specificity hoặc thêm override toàn cục để che xung đột.
- Với phần hình thức đã đổi, xem ảnh desktop/mobile thực và trả lời: mắt nhìn vào đâu trước; tác vụ chính có nổi rõ; chữ/nhóm có dễ đọc; căn hàng/khoảng cách có đều; có thể bỏ trang trí nào mà vẫn giữ đủ thông tin và nghiệp vụ? Sửa đúng điểm chưa đạt. Không lấy sở thích thẩm mỹ để bỏ warning, dữ liệu hoặc điều kiện bắt buộc.
- Kiểm thị giác bổ sung cho assertion hành vi tại §12. Không dùng ảnh đẹp để kết luận request/quyền/dữ liệu đúng; không dùng build xanh để kết luận bố cục dễ dùng. Báo riêng phần đã kiểm, vấn đề còn lại và phạm vi chưa xác minh.
<a id="p11-signing-memory-20261123"></a>

## 14. Memory UX ký số MGHP — owner23/11/2026

Đọc [thiết kế P11 đã chốt](../../nghiepvu/09-trien-khai/p11-signing-design.md) trước khi làm phần ký; chưa có UI P11 runtime ở lượt đồng bộ này. Các quy tắc sau chỉ cho ký văn bản MGHP, không đổi UI/quyền module khác:

- Một mục Tài liệu ký số MGHP trong navigation/AppShell hiện có. CV điều phối/BB bước1, PP QĐ bước1, PHT bước2; đúng actor tự bấm, hiển thị chờ ai và tiến độ sau ký. Giữ kho đã xử lý/lịch sử, không làm file biến mất sau thao tác.
- CV không có Từ chối ký. P11 không có Từ chối ký ở mọi role theo quyết định cuối23/11. PP xác nhận xem đúng PDF trong dialog Ký số, không nút Đã xem riêng. PP được xem/tải BB đã ký xong của đúng bộ QĐ được giao; quyền do backend cấp, không mọi BB.
- Màn hình ký dùng PDF.js/canvas + overlay chọn trang/kéo/di chuyển/resize; không nhập point/ID/hash/cert/token. Đúng page/zoom/rotation/crop và đúng input PDF; thay file bỏ vùng cũ. Sau thành công mở đúng trang của file CTSV mới lưu. **Cập nhật owner23/11:** cả màn hình xem và ký dùng viewer chung tại `shared/ui/pdf`, thay iframe ở các màn hình trước; giữ PDF.js 6.5.438 hiện có. Quy tắc triển khai tại §15.
- Sau mỗi bước phải lưu PDF riêng authenticated ở Cloudinary CTSV và liên kết SQL commit mới báo hoàn tất/chuyển bước. Viewer/download lấy qua backend file CTSV, không CA URL. UI có trạng thái Đang nhận PDF/Đang lưu kết quả/lỗi/unknown; lỗi nhìn thấy sau cuộn và có đường phục hồi đúng bước.
- Không thêm user_signature_id/chọn mẫu, không tái đưa external pin/trust vào gate. CV thu hồi tài liệu đang chờ ký là DEFERRED, không dựng chức năng hoặc test nhánh đó trước khi owner giao; vẫn giữ bất biến signed/unknown/history.

### Quyết định P11 cuối23/11 — không Từ chối ký

Owner đã bỏ **toàn bộ Từ chối ký của PP/PHT trong P11**, CV cũng không có. Không tái dựng nút/API/reason từ chối hoặc case sửa dữ liệu đã chốt. Phần memory cũ về PP/PHT refusal đã superseded. Giữ ký chủ động đúng bước, PP ACK, PDF.js, output từng bước CTSV, lịch sử và phục hồi lỗi; không auto retry unknown. Thu hồi tài liệu chờ ký vẫn DEFERRED. Nguồn: [Owner Decisions](../../nghiepvu/06-bao-cao/phan-loai-readiness-api-ctsv-2026-11-05.md#p11-no-refusal-20261123).

## 15. PDF.js dùng chung — owner23/11/2026

- Entry công khai `shared/ui/pdf` xuất `PdfViewer`, `PdfViewerProps`, `PdfGeometry`; đọc [hướng dẫn tích hợp](../docs/shared-pdf-preview-guide.md). Mọi feature import entry này, không copy viewer/worker/geometry hoặc import viewer từ feature khác. Shared không import app/features hay chứa điều kiện tên module/role.
- Read-only là mặc định; các tính năng chung gồm chuyển trang, zoom, xoay và tải bytes đã load. Owner xác nhận không cần tìm chữ/bôi đen/sao chép ở lần chuyển đổi này. Công cụ kéo/di chuyển/resize vùng chỉ hiện khi consumer có quyền và cấp callback; component không thực hiện lệnh ký.
- Dùng AuthClient ổn định và fileId từ backend. Backend tiếp tục kiểm quyền trên file; module mới không tự được đọc file chỉ nhờ dùng viewer chung. Giữ API/CORS/CSRF/JWT, không tải CA/storage URL trực tiếp hoặc đưa token vào query.
- Fetch phụ thuộc client/fileId/retry; giữ PDF khi metadata/ETag đổi, không thêm key gây remount. Hủy request/render cũ, chờ canvas cancellation và destroy loading task khi cleanup; không hiện file cũ dưới tên mới. Không cache PDF/token vào storage. Download dùng bytes đang xem và thu hồi Blob URL tạm.
- Geometry dùng PDF points qua viewport, giữ đúng page/zoom/rotation/crop; đổi trang/file hoặc version/bước/ETag nghiệp vụ làm mất xác nhận cũ phù hợp. Viewer không thay quyền, ETag, hash check hoặc workflow riêng của module.
- Kiểm canvas có nội dung, đúng file/hash tải xuống, n tài liệu và đổi nhanh, retry, metadata không GET thừa, desktop/mobile và quyền. Test harness chọn vùng không chứng minh quyền ký hoặc CA integration REAL; không gửi ký thật chỉ để kiểm viewer.
