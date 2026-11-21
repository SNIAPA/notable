//! MCP server that lets an agent see and edit the page open in Notable on a tablet, through the
//! app's stroke API reached over adb.

mod geometry;
mod tablet;

use geometry::{Item, Pt};
use rmcp::handler::server::router::tool::ToolRouter;
use rmcp::handler::server::wrapper::Parameters;
use rmcp::model::{CallToolResult, ContentBlock, ErrorData, ServerCapabilities, ServerConfig};
use rmcp::{ServerHandler, ServiceExt, tool, tool_handler, tool_router};
use schemars::JsonSchema;
use serde::Deserialize;
use serde_json::{Value, json};
use tablet::{SCREENSHOT_SCALE, Tablet};

/// Which coordinates a tool call uses.
#[derive(Debug, Clone, Copy, Default, Deserialize, JsonSchema)]
#[serde(rename_all = "snake_case")]
enum Space {
    /// Pixels of the image `read_page` returns (the screen at half size).
    #[default]
    Image,
    /// Notable page coordinates, independent of scroll and zoom.
    Page,
}

#[derive(Debug, Deserialize, JsonSchema)]
struct DrawRequest {
    /// Things to draw, all applied as one undo step.
    items: Vec<Item>,
    /// Pen: BALLPEN (default), PENCIL, BRUSH, MARKER, FOUNTAIN, CHARCOAL, CALLIGRAPHY.
    pen: Option<String>,
    /// Stroke width in page units; default 3.
    size: Option<f32>,
    /// ARGB color as a hex string such as "#FF000000"; default black.
    color: Option<String>,
    /// Stroke ids to delete in the same step, so the drawing replaces them.
    #[serde(default)]
    replace: Vec<String>,
    #[serde(default)]
    space: Space,
}

#[derive(Debug, Deserialize, JsonSchema)]
struct IdsRequest {
    /// Stroke ids, as listed by `read_page`.
    ids: Vec<String>,
}

#[derive(Debug, Deserialize, JsonSchema)]
struct MoveRequest {
    ids: Vec<String>,
    dx: f32,
    dy: f32,
    #[serde(default)]
    space: Space,
}

#[derive(Debug, Deserialize, JsonSchema)]
struct OpenRequest {
    /// Page id, from `list_notebooks`.
    page_id: String,
}

#[derive(Debug, Deserialize, JsonSchema)]
struct ReadRequest {
    /// Include stroke ids, bounds and simplified points (default true).
    strokes: Option<bool>,
    #[serde(default)]
    space: Space,
}

/// Maps between page and image coordinates for the current viewport.
#[derive(Clone, Copy)]
struct View {
    scroll: Pt,
    zoom: f32,
}

impl View {
    fn from_status(status: &Value) -> anyhow::Result<View> {
        let v = status.get("viewport").filter(|v| !v.is_null()).ok_or_else(|| {
            anyhow::anyhow!("no page is open in Notable; open one with open_page")
        })?;
        let f = |k: &str| v[k].as_f64().unwrap_or(0.0) as f32;
        Ok(View { scroll: [f("scrollX"), f("scrollY")], zoom: f("zoom").max(1e-3) })
    }
    /// Factor from image units to page units.
    fn image_to_page_scale(&self) -> f32 {
        1.0 / (SCREENSHOT_SCALE * self.zoom)
    }
    fn to_page(&self, space: Space, p: Pt) -> Pt {
        match space {
            Space::Page => p,
            Space::Image => {
                let s = self.image_to_page_scale();
                [p[0] * s + self.scroll[0], p[1] * s + self.scroll[1]]
            }
        }
    }
    fn from_page(&self, space: Space, p: Pt) -> Pt {
        match space {
            Space::Page => p,
            Space::Image => {
                let s = self.image_to_page_scale();
                [(p[0] - self.scroll[0]) / s, (p[1] - self.scroll[1]) / s]
            }
        }
    }
    fn length_to_page(&self, space: Space) -> f32 {
        match space {
            Space::Page => 1.0,
            Space::Image => self.image_to_page_scale(),
        }
    }
}

fn parse_color(c: &str) -> Result<u32, String> {
    let hex = c.trim_start_matches('#').trim_start_matches("0x");
    let v = u32::from_str_radix(hex, 16).map_err(|e| format!("bad color {c}: {e}"))?;
    Ok(if hex.len() <= 6 { 0xFF00_0000 | v } else { v })
}

fn err(e: impl std::fmt::Display) -> ErrorData {
    ErrorData::internal_error(e.to_string(), None)
}

fn text(v: impl std::fmt::Display) -> CallToolResult {
    CallToolResult::success(vec![ContentBlock::text(v.to_string())])
}

/// Keeps every point that is at least `min` from the last kept one, plus the last point.
fn decimate(points: &[Pt], min: f32) -> Vec<Pt> {
    let mut out: Vec<Pt> = Vec::new();
    for p in points {
        if out.last().is_none_or(|q| (p[0] - q[0]).hypot(p[1] - q[1]) >= min) {
            out.push(*p);
        }
    }
    if let (Some(last), Some(kept)) = (points.last(), out.last())
        && last != kept
    {
        out.push(*last);
    }
    out
}

fn round(p: Pt) -> Value {
    json!([(p[0] * 10.0).round() / 10.0, (p[1] * 10.0).round() / 10.0])
}

#[derive(Clone)]
struct NotableMcp {
    tablet: Tablet,
    tool_router: ToolRouter<Self>,
}

#[tool_router(router = tool_router)]
impl NotableMcp {
    fn new() -> Self {
        Self { tablet: Tablet::from_env(), tool_router: Self::tool_router() }
    }

    async fn view(&self) -> Result<(Value, View), ErrorData> {
        let status = self.tablet.get("/status").await.map_err(err)?;
        let view = View::from_status(&status).map_err(err)?;
        Ok((status, view))
    }

    #[tool(
        name = "tablet_status",
        description = "Which Notable page is open on the tablet, its viewport and stroke count."
    )]
    async fn tablet_status(&self) -> Result<CallToolResult, ErrorData> {
        let s = self.tablet.get("/status").await.map_err(err)?;
        Ok(text(serde_json::to_string_pretty(&s).map_err(err)?))
    }

    #[tool(name = "list_notebooks", description = "Notebooks in Notable with their page ids.")]
    async fn list_notebooks(&self) -> Result<CallToolResult, ErrorData> {
        let s = self.tablet.get("/notebooks").await.map_err(err)?;
        Ok(text(serde_json::to_string_pretty(&s).map_err(err)?))
    }

    #[tool(name = "open_page", description = "Open a page in Notable's editor on the tablet.")]
    async fn open_page(
        &self,
        Parameters(req): Parameters<OpenRequest>,
    ) -> Result<CallToolResult, ErrorData> {
        self.tablet.post("/open", json!({ "pageId": req.page_id })).await.map_err(err)?;
        tokio::time::sleep(std::time::Duration::from_millis(1500)).await;
        let s = self.tablet.get("/status").await.map_err(err)?;
        Ok(text(serde_json::to_string_pretty(&s).map_err(err)?))
    }

    #[tool(
        name = "read_page",
        description = "Screenshot of the tablet (half size; its pixels are the default 'image' \
                       coordinates for every tool) plus the open page's strokes with ids and bounds."
    )]
    async fn read_page(
        &self,
        Parameters(req): Parameters<ReadRequest>,
    ) -> Result<CallToolResult, ErrorData> {
        let png = self.tablet.screenshot().await.map_err(err)?;
        let mut content = vec![ContentBlock::image(png, "image/png")];
        let status = self.tablet.get("/status").await.map_err(err)?;
        let mut info = json!({ "status": status });
        if req.strokes.unwrap_or(true)
            && let Ok(view) = View::from_status(&status)
        {
            let strokes = self.tablet.get("/page/current/strokes").await.map_err(err)?;
            let min = 4.0 * view.length_to_page(req.space);
            let list: Vec<Value> = strokes
                .as_array()
                .into_iter()
                .flatten()
                .map(|s| {
                    let pts: Vec<Pt> = s["points"]
                        .as_array()
                        .into_iter()
                        .flatten()
                        .map(|p| [p[0].as_f64().unwrap_or(0.0) as f32, p[1].as_f64().unwrap_or(0.0) as f32])
                        .collect();
                    let b: Vec<f32> = s["bbox"]
                        .as_array()
                        .into_iter()
                        .flatten()
                        .map(|v| v.as_f64().unwrap_or(0.0) as f32)
                        .collect();
                    let (tl, br) = if b.len() == 4 {
                        (view.from_page(req.space, [b[0], b[1]]), view.from_page(req.space, [b[2], b[3]]))
                    } else {
                        ([0.0; 2], [0.0; 2])
                    };
                    let simplified: Vec<Value> =
                        decimate(&pts, min).into_iter().map(|p| round(view.from_page(req.space, p))).collect();
                    json!({
                        "id": s["id"],
                        "pen": s["pen"],
                        "size": s["size"],
                        "bbox": [round(tl), round(br)],
                        "points": simplified,
                    })
                })
                .collect();
            info["strokes"] = Value::Array(list);
        }
        content.push(ContentBlock::text(info.to_string()));
        Ok(CallToolResult::success(content))
    }

    #[tool(
        name = "draw",
        description = "Draw strokes, lines, polylines, boxes, ellipses, arrows and handwriting-like \
                       text on the open page as one undo step, optionally replacing existing strokes. \
                       Coordinates are image pixels from read_page unless space is 'page'."
    )]
    async fn draw(&self, Parameters(req): Parameters<DrawRequest>) -> Result<CallToolResult, ErrorData> {
        let (status, view) = self.view().await?;
        let color = parse_color(req.color.as_deref().unwrap_or("#FF000000"))
            .map_err(|e| ErrorData::invalid_params(e, None))?;
        let size = req.size.unwrap_or(3.0);
        let pen = req.pen.unwrap_or_else(|| "BALLPEN".into());
        let scale = view.length_to_page(req.space);
        let to_page = |p: Pt| view.to_page(req.space, p);
        let add: Vec<Value> = req
            .items
            .iter()
            .flat_map(|item| item.map(&to_page, scale).strokes(size))
            .filter(|pts| !pts.is_empty())
            .map(|pts| {
                json!({
                    "pen": pen,
                    "size": size,
                    "color": color,
                    "points": pts.iter().map(|p| json!([p[0], p[1]])).collect::<Vec<_>>(),
                })
            })
            .collect();
        let body = json!({ "pageId": status["pageId"], "add": add, "delete": req.replace });
        let r = self.tablet.post("/page/current/ops", body).await.map_err(err)?;
        Ok(text(r))
    }

    #[tool(name = "delete_strokes", description = "Delete strokes by id as one undo step.")]
    async fn delete_strokes(
        &self,
        Parameters(req): Parameters<IdsRequest>,
    ) -> Result<CallToolResult, ErrorData> {
        let (status, _) = self.view().await?;
        let body = json!({ "pageId": status["pageId"], "delete": req.ids });
        Ok(text(self.tablet.post("/page/current/ops", body).await.map_err(err)?))
    }

    #[tool(name = "move_strokes", description = "Move strokes by (dx, dy) as one undo step.")]
    async fn move_strokes(
        &self,
        Parameters(req): Parameters<MoveRequest>,
    ) -> Result<CallToolResult, ErrorData> {
        let (status, view) = self.view().await?;
        let s = view.length_to_page(req.space);
        let (dx, dy) = (req.dx * s, req.dy * s);
        let all = self.tablet.get("/page/current/strokes").await.map_err(err)?;
        let update: Vec<Value> = all
            .as_array()
            .into_iter()
            .flatten()
            .filter(|st| req.ids.iter().any(|id| st["id"].as_str() == Some(id)))
            .map(|st| {
                let mut st = st.clone();
                if let Some(points) = st["points"].as_array_mut() {
                    for p in points.iter_mut() {
                        if let Some(a) = p.as_array_mut() {
                            a[0] = json!(a[0].as_f64().unwrap_or(0.0) + dx as f64);
                            a[1] = json!(a[1].as_f64().unwrap_or(0.0) + dy as f64);
                        }
                    }
                }
                st["bbox"] = Value::Null;
                st
            })
            .collect();
        if update.len() != req.ids.len() {
            return Err(ErrorData::invalid_params("some stroke ids are not on the open page", None));
        }
        let body = json!({ "pageId": status["pageId"], "update": update });
        Ok(text(self.tablet.post("/page/current/ops", body).await.map_err(err)?))
    }

    #[tool(name = "undo", description = "Undo the last step on the open page.")]
    async fn undo(&self) -> Result<CallToolResult, ErrorData> {
        Ok(text(self.tablet.post("/undo", json!({})).await.map_err(err)?))
    }

    #[tool(name = "redo", description = "Redo the last undone step on the open page.")]
    async fn redo(&self) -> Result<CallToolResult, ErrorData> {
        Ok(text(self.tablet.post("/redo", json!({})).await.map_err(err)?))
    }
}

#[tool_handler(router = self.tool_router)]
impl ServerHandler for NotableMcp {
    fn get_info(&self) -> ServerConfig {
        let mut info = ServerConfig::default();
        info.capabilities = ServerCapabilities::builder().enable_tools().build();
        info.instructions = Some(
            "Draw on the handwritten notes open in Notable on the user's tablet. Call read_page \
             first: its image pixels are the coordinates draw, move_strokes and the stroke list use. \
             Each draw call is one undo step."
                .into(),
        );
        info
    }
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let service = NotableMcp::new().serve(rmcp::transport::stdio()).await?;
    service.waiting().await?;
    Ok(())
}
