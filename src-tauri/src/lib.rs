use std::{fs, path::Path};

use serde::Serialize;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ModelCandidate {
  name: String,
  path: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ModelFilePayload {
  name: String,
  path: String,
  directory_path: String,
  bytes: Vec<u8>,
}

fn io_error_message(action: &str, path: &str, error: std::io::Error) -> String {
  format!("{}失败: {} ({})", action, path, error)
}

#[tauri::command]
fn list_model_candidates(directory_path: String) -> Result<Vec<ModelCandidate>, String> {
  let mut candidates = Vec::new();
  let entries = fs::read_dir(&directory_path)
    .map_err(|error| io_error_message("读取模型目录", &directory_path, error))?;

  for entry_result in entries {
    let entry = entry_result.map_err(|error| io_error_message("遍历模型目录", &directory_path, error))?;
    let path = entry.path();
    if !path.is_file() {
      continue;
    }

    let Some(extension) = path.extension().and_then(|value| value.to_str()) else {
      continue;
    };

    if !matches!(extension.to_ascii_lowercase().as_str(), "ply" | "spz") {
      continue;
    }

    let Some(name) = path.file_name().and_then(|value| value.to_str()) else {
      continue;
    };

    candidates.push(ModelCandidate {
      name: name.to_string(),
      path: path.to_string_lossy().into_owned(),
    });
  }

  candidates.sort_by(|left, right| left.name.cmp(&right.name));
  Ok(candidates)
}

#[tauri::command]
fn read_model_file(file_path: String) -> Result<ModelFilePayload, String> {
  let path = Path::new(&file_path);
  let name = path
    .file_name()
    .and_then(|value| value.to_str())
    .ok_or_else(|| format!("无效的模型路径: {}", file_path))?
    .to_string();
  let directory_path = path
    .parent()
    .map(|value| value.to_string_lossy().into_owned())
    .ok_or_else(|| format!("无法确定模型所在目录: {}", file_path))?;
  let bytes = fs::read(path).map_err(|error| io_error_message("读取模型文件", &file_path, error))?;

  Ok(ModelFilePayload {
    name,
    path: file_path,
    directory_path,
    bytes,
  })
}

#[tauri::command]
fn read_text_file_if_exists(path: String) -> Result<Option<String>, String> {
  let file_path = Path::new(&path);
  if !file_path.exists() {
    return Ok(None);
  }

  let contents = fs::read_to_string(file_path)
    .map_err(|error| io_error_message("读取文本文件", &path, error))?;
  Ok(Some(contents))
}

#[tauri::command]
fn write_text_file(path: String, contents: String) -> Result<(), String> {
  let file_path = Path::new(&path);
  if let Some(parent) = file_path.parent() {
    fs::create_dir_all(parent).map_err(|error| io_error_message("创建目录", &parent.to_string_lossy(), error))?;
  }

  fs::write(file_path, contents).map_err(|error| io_error_message("写入文本文件", &path, error))
}

#[tauri::command]
fn write_binary_file(path: String, bytes: Vec<u8>) -> Result<(), String> {
  let file_path = Path::new(&path);
  if let Some(parent) = file_path.parent() {
    fs::create_dir_all(parent).map_err(|error| io_error_message("创建目录", &parent.to_string_lossy(), error))?;
  }

  fs::write(file_path, bytes).map_err(|error| io_error_message("写入二进制文件", &path, error))
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  tauri::Builder::default()
    .plugin(tauri_plugin_dialog::init())
    .invoke_handler(tauri::generate_handler![
      list_model_candidates,
      read_model_file,
      read_text_file_if_exists,
      write_text_file,
      write_binary_file,
    ])
    .setup(|app| {
      if cfg!(debug_assertions) {
        app.handle().plugin(
          tauri_plugin_log::Builder::default()
            .level(log::LevelFilter::Info)
            .build(),
        )?;
      }
      Ok(())
    })
    .run(tauri::generate_context!())
    .expect("error while running tauri application");
}
