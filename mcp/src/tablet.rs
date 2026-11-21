//! The tablet: adb plumbing and HTTP calls to Notable's stroke API through a forwarded port.

use anyhow::{Context, bail};
use base64::Engine;
use serde_json::Value;
use std::sync::Arc;
use tokio::process::Command;
use tokio::sync::Mutex;

/// Screenshots are downscaled by this factor; their pixels are the "image" coordinate space.
pub const SCREENSHOT_SCALE: f32 = 0.5;
const PORT: u16 = 8765;

#[derive(Clone)]
pub struct Tablet {
    /// adb serial, e.g. "192.168.0.104:5555".
    serial: String,
    http: reqwest::Client,
    token: Arc<Mutex<Option<String>>>,
}

impl Tablet {
    /// Reads the device from NOTABLE_ADB_SERIAL, defaulting to the Boox on the LAN.
    pub fn from_env() -> Tablet {
        Tablet {
            serial: std::env::var("NOTABLE_ADB_SERIAL").unwrap_or_else(|_| "192.168.0.104:5555".into()),
            http: reqwest::Client::new(),
            token: Arc::new(Mutex::new(None)),
        }
    }

    async fn adb(&self, args: &[&str]) -> anyhow::Result<Vec<u8>> {
        let out = Command::new("adb")
            .args(["-s", &self.serial])
            .args(args)
            .output()
            .await
            .context("running adb")?;
        if !out.status.success() {
            bail!("adb {}: {}", args.join(" "), String::from_utf8_lossy(&out.stderr).trim());
        }
        Ok(out.stdout)
    }

    /// Connects adb, forwards the port and reads the API token.
    async fn connect(&self) -> anyhow::Result<String> {
        if self.serial.contains(':') {
            let _ = Command::new("adb").args(["connect", &self.serial]).output().await;
        }
        self.adb(&["forward", &format!("tcp:{PORT}"), &format!("tcp:{PORT}")]).await?;
        let token = self.adb(&["shell", "cat", "/sdcard/Documents/notable/api-token"]).await.context(
            "reading the API token; open a page in the Notable stroke-api build so its server starts",
        )?;
        let token = String::from_utf8(token)?.trim().to_string();
        if token.is_empty() || token.contains(' ') {
            bail!("no API token on the tablet yet; open a page in Notable");
        }
        Ok(token)
    }

    async fn token(&self, fresh: bool) -> anyhow::Result<String> {
        let mut t = self.token.lock().await;
        if fresh || t.is_none() {
            *t = Some(self.connect().await?);
        }
        Ok(t.clone().unwrap_or_default())
    }

    async fn request(&self, method: reqwest::Method, path: &str, body: Option<&Value>) -> anyhow::Result<Value> {
        let mut last = None;
        for attempt in 0..2 {
            let token = self.token(attempt > 0).await?;
            let mut req = self
                .http
                .request(method.clone(), format!("http://127.0.0.1:{PORT}{path}"))
                .bearer_auth(&token)
                .timeout(std::time::Duration::from_secs(15));
            if let Some(b) = body {
                req = req.json(b);
            }
            match req.send().await {
                Ok(r) => {
                    let status = r.status();
                    let v: Value = r.json().await.unwrap_or(Value::Null);
                    if status == reqwest::StatusCode::UNAUTHORIZED && attempt == 0 {
                        continue;
                    }
                    if !status.is_success() {
                        bail!("{path}: HTTP {status}: {}", v.get("error").unwrap_or(&v));
                    }
                    return Ok(v);
                }
                Err(e) => last = Some(e),
            }
        }
        Err(anyhow::anyhow!("cannot reach Notable on the tablet: {}", last.map(|e| e.to_string()).unwrap_or_default()))
    }

    pub async fn get(&self, path: &str) -> anyhow::Result<Value> {
        self.request(reqwest::Method::GET, path, None).await
    }

    pub async fn post(&self, path: &str, body: Value) -> anyhow::Result<Value> {
        self.request(reqwest::Method::POST, path, Some(&body)).await
    }

    /// A screenshot downscaled by [SCREENSHOT_SCALE], as base64 PNG.
    pub async fn screenshot(&self) -> anyhow::Result<String> {
        let png = self.adb(&["exec-out", "screencap", "-p"]).await?;
        let img = image::load_from_memory(&png).context("decoding screenshot")?;
        let (w, h) = (
            (img.width() as f32 * SCREENSHOT_SCALE) as u32,
            (img.height() as f32 * SCREENSHOT_SCALE) as u32,
        );
        let small = img.resize_exact(w, h, image::imageops::FilterType::Triangle).to_luma8();
        let mut out = std::io::Cursor::new(Vec::new());
        small.write_to(&mut out, image::ImageFormat::Png)?;
        Ok(base64::engine::general_purpose::STANDARD.encode(out.into_inner()))
    }
}
