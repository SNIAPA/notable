//! Shapes and text turned into pen strokes, in page coordinates.

use schemars::JsonSchema;
use serde::Deserialize;

pub type Pt = [f32; 2];

/// One thing to draw. Coordinates are in the space chosen by the tool call.
#[derive(Debug, Clone, Deserialize, JsonSchema)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum Item {
    /// A freehand stroke through the given points.
    Stroke { points: Vec<Pt> },
    /// A straight line.
    Line { from: Pt, to: Pt },
    /// Connected straight segments; `closed` joins the last point to the first.
    Polyline {
        points: Vec<Pt>,
        #[serde(default)]
        closed: bool,
    },
    /// An axis-aligned rectangle with top-left corner (x, y).
    Box { x: f32, y: f32, w: f32, h: f32 },
    /// An ellipse centered on (cx, cy).
    Ellipse { cx: f32, cy: f32, rx: f32, ry: f32 },
    /// A line with an arrowhead at `to`, drawn as one stroke.
    Arrow {
        from: Pt,
        to: Pt,
        /// Arrowhead length; defaults to 6 x the pen size, at least 24.
        head: Option<f32>,
    },
    /// Single-line handwriting-like text. (x, y) is the left end of the baseline;
    /// `height` is the capital letter height.
    Text { text: String, x: f32, y: f32, height: f32 },
}

impl Item {
    /// Maps every coordinate of the item through `f` and scales lengths by `scale`.
    pub fn map(&self, f: &impl Fn(Pt) -> Pt, scale: f32) -> Item {
        let pts = |v: &Vec<Pt>| v.iter().map(|p| f(*p)).collect::<Vec<_>>();
        match self {
            Item::Stroke { points } => Item::Stroke { points: pts(points) },
            Item::Line { from, to } => Item::Line { from: f(*from), to: f(*to) },
            Item::Polyline { points, closed } => Item::Polyline { points: pts(points), closed: *closed },
            Item::Box { x, y, w, h } => {
                let [x, y] = f([*x, *y]);
                Item::Box { x, y, w: w * scale, h: h * scale }
            }
            Item::Ellipse { cx, cy, rx, ry } => {
                let [cx, cy] = f([*cx, *cy]);
                Item::Ellipse { cx, cy, rx: rx * scale, ry: ry * scale }
            }
            Item::Arrow { from, to, head } => Item::Arrow { from: f(*from), to: f(*to), head: head.map(|h| h * scale) },
            Item::Text { text, x, y, height } => {
                let [x, y] = f([*x, *y]);
                Item::Text { text: text.clone(), x, y, height: height * scale }
            }
        }
    }

    /// The strokes (point lists) that draw this item.
    pub fn strokes(&self, pen_size: f32) -> Vec<Vec<Pt>> {
        match self {
            Item::Stroke { points } => vec![points.clone()],
            Item::Line { from, to } => vec![densify(&[*from, *to])],
            Item::Polyline { points, closed } => {
                let mut p = points.clone();
                if *closed && !p.is_empty() {
                    p.push(p[0]);
                }
                vec![densify(&p)]
            }
            Item::Box { x, y, w, h } => {
                vec![densify(&[[*x, *y], [x + w, *y], [x + w, y + h], [*x, y + h], [*x, *y]])]
            }
            Item::Ellipse { cx, cy, rx, ry } => {
                let n = ((rx.max(*ry) * 0.5) as usize).clamp(24, 160);
                let pts = (0..=n)
                    .map(|i| {
                        let a = i as f32 / n as f32 * std::f32::consts::TAU;
                        [cx + rx * a.cos(), cy + ry * a.sin()]
                    })
                    .collect();
                vec![pts]
            }
            Item::Arrow { from, to, head } => {
                let len = head.unwrap_or((pen_size * 6.0).max(24.0));
                let (dx, dy) = (to[0] - from[0], to[1] - from[1]);
                let d = (dx * dx + dy * dy).sqrt().max(1e-3);
                let (ux, uy) = (dx / d, dy / d);
                let wing = |s: f32| {
                    let (c, n) = (0.5f32.cos(), s * 0.5f32.sin());
                    [to[0] - len * (ux * c - uy * n), to[1] - len * (uy * c + ux * n)]
                };
                vec![densify(&[*from, *to, wing(1.0), *to, wing(-1.0)])]
            }
            Item::Text { text, x, y, height } => text_strokes(text, *x, *y, *height),
        }
    }
}

/// Adds points along each segment so no two consecutive points are more than 6 units apart.
fn densify(points: &[Pt]) -> Vec<Pt> {
    let mut out = Vec::new();
    for w in points.windows(2) {
        let (a, b) = (w[0], w[1]);
        let d = ((b[0] - a[0]).powi(2) + (b[1] - a[1]).powi(2)).sqrt();
        let n = (d / 6.0).ceil().max(1.0) as usize;
        for i in 0..n {
            let t = i as f32 / n as f32;
            out.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]);
        }
    }
    if let Some(last) = points.last() {
        out.push(*last);
    }
    out
}

/// The Hershey "futural" font: one line per glyph, ASCII 32 upwards.
const FONT: &str = include_str!("../fonts/futural.jhf");

/// Strokes for one glyph in font units (baseline at y = 9, capitals from y = -12), plus its
/// left and right bounds.
fn glyph(ch: char) -> Option<(f32, f32, Vec<Vec<Pt>>)> {
    let index = (ch as usize).checked_sub(32)?;
    let line = FONT.lines().nth(index)?;
    let data = line.get(8..)?.as_bytes();
    let v = |b: u8| b as f32 - b'R' as f32;
    let (left, right) = (v(*data.first()?), v(*data.get(1)?));
    let mut strokes = vec![Vec::new()];
    for pair in data[2..].chunks(2) {
        if pair.len() < 2 {
            break;
        }
        if pair == b" R" {
            strokes.push(Vec::new());
        } else {
            strokes.last_mut()?.push([v(pair[0]), v(pair[1])]);
        }
    }
    strokes.retain(|s| !s.is_empty());
    Some((left, right, strokes))
}

/// Strokes that write `text` with its baseline starting at (x, y) and capitals `height` tall.
pub fn text_strokes(text: &str, x: f32, y: f32, height: f32) -> Vec<Vec<Pt>> {
    let scale = height / 21.0;
    let mut pen_x = x;
    let mut line_y = y;
    let mut out = Vec::new();
    for ch in text.chars() {
        if ch == '\n' {
            pen_x = x;
            line_y += height * 1.7;
            continue;
        }
        let Some((left, right, strokes)) = glyph(ch).or_else(|| glyph('?')) else { continue };
        for s in strokes {
            let pts: Vec<Pt> = s
                .iter()
                .map(|p| [pen_x + (p[0] - left) * scale, line_y + (p[1] - 9.0) * scale])
                .collect();
            out.push(densify(&pts));
        }
        pen_x += (right - left) * scale;
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn letters_have_strokes() {
        let (l, r, s) = glyph('A').unwrap();
        assert!(r > l);
        assert_eq!(s.len(), 3);
        assert!(glyph(' ').unwrap().2.is_empty());
    }

    #[test]
    fn text_is_laid_out_left_to_right() {
        let strokes = text_strokes("HI", 100.0, 200.0, 21.0);
        let max_y = strokes.iter().flatten().map(|p| p[1]).fold(f32::MIN, f32::max);
        assert!((max_y - 200.0).abs() < 0.01, "baseline at y");
    }
}
