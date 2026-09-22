---
name: tabler-thymeleaf
description: Thiết kế giao diện Dashboard/Web đẹp bằng Tabler UI tích hợp chuẩn Spring Boot Thymeleaf.
---

# Quy tắc thiết kế Tabler cho Thymeleaf

Mỗi khi người dùng yêu cầu viết giao diện hoặc sửa file HTML, hãy tuân thủ các quy chuẩn sau:

### 1. Đường dẫn tĩnh (Static Assets)
Luôn dùng cú pháp `th:href` và `th:src` trỏ vào `/dist/`:
- CSS: `<link rel="stylesheet" th:href="@{/dist/css/tabler.min.css}">`
- JS: `<script th:src="@{/dist/js/tabler.min.js}" defer></script>`

### 2. Cấu trúc khung trang chuẩn (Page Layout)
Luôn bọc nội dung trong phân cấp:
```html
<div class="page">
  <!-- Navbar / Header -->
  <header class="navbar navbar-expand-md d-print-none"> ... </header>
  
  <div class="page-wrapper">
    <!-- Tiêu đề trang -->
    <div class="page-header d-print-none">
      <div class="container-xl">
        <div class="row g-2 align-items-center">
          <div class="col">
            <h2 class="page-title" th:text="${pageTitle}">Tiêu đề</h2>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Nội dung chính -->
    <div class="page-body">
      <div class="container-xl">
        <!-- Cards, Tables, Forms đặt ở đây -->
      </div>
    </div>
  </div>
</div>
```

### 3. Thành phần giao diện hay dùng
- **Thẻ thống kê (Card):** Dùng `.card`, `.card-body`, `.card-title`.
- **Bảng dữ liệu (Table):** Dùng `.table-responsive` bọc ngoài `<table class="table table-vcenter card-table">`. Sử dụng `th:each` để render danh sách dữ liệu.
- **Nút bấm:** Dùng `.btn .btn-primary`, `.btn .btn-outline-secondary`.
- **Trạng thái (Badges):** Dùng `.badge .bg-green-lt`, `.badge .bg-red-lt` để hiển thị trạng thái mềm mắt.