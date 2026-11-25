import type {ReactNode} from 'react';
import dutLogo from '../assets/dut-logo.png';
import styles from './LoginLayout.module.css';

export function LoginLayout({children}:{children:ReactNode}) {
 return <div className={styles.entry}>
  <a className={styles.skipLink} href="#ctsv-login-panel">Đến phần đăng nhập</a>
  <header className={styles.header}>
   <div className={styles.brand}>
    <img src={dutLogo} width="48" height="48" alt=""/>
    <div>
     <strong>Trường Đại học Bách khoa – Đại học Đà Nẵng</strong>
     <small>University of Science and Technology · DUT</small>
    </div>
   </div>
  </header>
  <main id="ctsv-main" className={styles.main} tabIndex={-1}>
   <aside className={styles.hero} aria-labelledby="ctsv-portal-heading">
    <span className={styles.eyebrow}>CỔNG THÔNG TIN</span>
    <h1 id="ctsv-portal-heading">Công tác<br/> Sinh viên.</h1>
    <p>Thông tin, chính sách và hoạt động dành cho sinh viên DUT.</p>
    <ul className={styles.topics} aria-label="Nội dung hệ thống">
     <li>Miễn giảm học phí</li>
     <li>Điểm rèn luyện</li>
     <li>Học bổng</li>
     <li>Sinh hoạt chủ nhiệm</li>
    </ul>
   </aside>
   <section id="ctsv-login-panel" className={styles.panel} tabIndex={-1} aria-label="Đăng nhập CTSV">
    {children}
   </section>
  </main>
  <footer className={styles.footer}>© 2026 · Phòng Công tác Sinh viên · DUT</footer>
 </div>;
}
