// 团队协作效率工作台 — Tauri 桌面壳入口
// 业务 UI 全部来自前端 dist/（dev 模式指向 http://127.0.0.1:3200），此处只负责开窗
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    tauri::Builder::default()
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
