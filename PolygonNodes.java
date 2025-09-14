/*
Copyright 2008-2011 Gephi
Authors : Eduardo Ramos <eduramiba@gmail.com>
Website : http://www.gephi.org

This file is part of Gephi.

DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

Copyright 2011 Gephi Consortium. All rights reserved.

The contents of this file are subject to the terms of either the GNU
General Public License Version 3 only ("GPL") or the Common
Development and Distribution License("CDDL") (collectively, the
"License"). You may not use this file except in compliance with the
License. You can obtain a copy of the License at
http://gephi.org/about/legal/license-notice/
or /cddl-1.0.txt and /gpl-3.0.txt. See the License for the
specific language governing permissions and limitations under the
License.  When distributing the software, include this License Header
Notice in each file and include the License files at
/cddl-1.0.txt and /gpl-3.0.txt. If applicable, add the following below the
License Header, with the fields enclosed by brackets [] replaced by
your own identifying information:
"Portions Copyrighted [year] [name of copyright owner]"

If you wish your version of this file to be governed by only the CDDL
or only the GPL Version 3, indicate your decision by adding
"[Contributor] elects to include this software in this distribution
under the [CDDL or GPL Version 3] license." If you do not indicate a
single choice of license, a recipient has the option to distribute
your version of this file under either the CDDL, the GPL Version 3 or
to extend the choice of license to its licensees as provided above.
However, if you add GPL Version 3 code and therefore, elected the GPL
Version 3 license, then the option applies only if the new code is
made subject to such option by the copyright holder.

Contributor(s):
zde <zde6919@rit.edu>
yao <xy3717@foxmail.com>
 */

// SPDX-License-Identifier: GPL-2.0-or-later
// Polygon-shaped & multiple-shape node renderer for Gephi 0.10.x (JDK 17)

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.Locale;

import org.gephi.graph.api.Node;
import org.gephi.preview.api.G2DTarget;
import org.gephi.preview.api.Item;
import org.gephi.preview.api.PDFTarget;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.RenderTarget;
import org.gephi.preview.spi.Renderer;
import org.gephi.preview.api.SVGTarget;
import org.gephi.preview.plugin.items.NodeItem;
import org.gephi.preview.plugin.renderers.NodeRenderer;
import org.gephi.preview.types.DependantColor;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = Renderer.class)
public class PolygonNodes extends NodeRenderer {

    // ======== Property keys (appear in Preview -> Settings) ========
    private static final String PROP_ENABLE = "ShapeNodes.property.enable";
    private static final String PROP_SHAPE_COLUMN = "ShapeNodes.property.shapeColumn";
    private static final String PROP_POLYGON_COLUMN = "ShapeNodes.property.polygonColumn";
    private static final String PROP_DEFAULT_SHAPE = "ShapeNodes.property.defaultShape";
    private static final String PROP_STAR_POINTS = "ShapeNodes.property.starPoints";

    // ======== Supported shapes ========
    private enum ShapeKind {
        CIRCLE, TRIANGLE, SQUARE, DIAMOND, POLYGON, PENTAGON, HEXAGON, HEPTAGON, OCTAGON, STAR
    }

    @Override
    public String getDisplayName() {
        // 将在 Manage Renderers 中显示的名称
        return NbBundle.getMessage(PolygonNodes.class, "PolygonNodes.name", "Polygon shaped nodes");
    }

    @Override
    public boolean isRendererForitem(Item item, PreviewProperties properties) {
        // 只处理节点项目，并受启用开关控制
        Boolean enabled = properties.getValue(PROP_ENABLE);
        boolean enabledValue = enabled != null ? enabled : true;
        return enabledValue && Item.NODE.equals(item.getType());
    }

    @Override
    public void preProcess(PreviewModel previewModel) {
        // 这里不需要额外预处理；如需复杂逻辑，可在此填充 item 的缓存数据
        super.preProcess(previewModel);
    }

    @Override
    public void render(Item item, RenderTarget target, PreviewProperties props) {
        // 读取当前节点的形状配置
        Node node = (item.getSource() instanceof Node) ? (Node) item.getSource() : null;
        ResolvedShape rs = resolveShape(node, props);

        // 未能解析出自定义形状 -> 走默认节点渲染（圆）
        if (rs == null || rs.kind == ShapeKind.CIRCLE) {
            super.render(item, target, props);
            return;
        }

        if (target instanceof G2DTarget) {
            renderG2D(item, (G2DTarget) target, props, rs);
        } else if (target instanceof SVGTarget) {
            // 简单回退：对 SVG/PDF 仍然交给父类，保持兼容
            super.render(item, target, props);
        } else if (target instanceof PDFTarget) {
            super.render(item, target, props);
        } else {
            // 未知目标，安全回退
            super.render(item, target, props);
        }
    }

    @Override
    public PreviewProperty[] getProperties() {
        // 在 Preview -> Settings 面板暴露可调属性
        return new PreviewProperty[] {
            PreviewProperty.createProperty(
                this, PROP_ENABLE, Boolean.class,
                "Enable multiple shapes", PreviewProperty.CATEGORY_NODES,
                "Enable node shape mapping by attribute"
            ).setValue(Boolean.TRUE),
            PreviewProperty.createProperty(
                this, PROP_SHAPE_COLUMN, String.class,
                "Shape column name", PreviewProperty.CATEGORY_NODES,
                "Node string column holding shape (e.g. shape). Values: circle, triangle, square, diamond, polygon, pentagon, hexagon, heptagon, octagon, star"
            ).setValue("shape"),
            PreviewProperty.createProperty(
                this, PROP_POLYGON_COLUMN, String.class,
                "Polygon-sides column name", PreviewProperty.CATEGORY_NODES,
                "Node integer column holding the polygon number of sides (>=3). Used when shape is polygon/unspecified."
            ).setValue("polygon"),
            PreviewProperty.createProperty(
                this, PROP_DEFAULT_SHAPE, String.class,
                "Default shape", PreviewProperty.CATEGORY_NODES,
                "Fallback shape name when no valid attribute is set (circle/triangle/square/diamond/polygon/pentagon/hexagon/heptagon/octagon/star)."
            ).setValue("circle"),
            PreviewProperty.createProperty(
                this, PROP_STAR_POINTS, Integer.class,
                "Star points", PreviewProperty.CATEGORY_NODES,
                "Number of points for star shape (>=3)."
            ).setValue(Integer.valueOf(5))
        };
    }

    // ======================== Rendering (G2D) ========================

    private void renderG2D(Item item, G2DTarget target, PreviewProperties props, ResolvedShape rs) {
        // 基本几何与样式
        Float x = item.getData(NodeItem.X);
        Float y = item.getData(NodeItem.Y);
        Float size = item.getData(NodeItem.SIZE); // 预览里大小单位（与默认节点渲染一致）
        Color fill = item.getData(NodeItem.COLOR);

        Color border = ((DependantColor) props.getValue(PreviewProperty.NODE_BORDER_COLOR)).getColor(fill);
        float borderWidth = props.getFloatValue(PreviewProperty.NODE_BORDER_WIDTH);

        Graphics2D g2 = target.getGraphics();
        Path2D.Float path;
        switch (rs.kind) {
            case TRIANGLE:
                path = regularPolygonPath(x, y, size, 3, -Math.PI / 2);
                break;
            case SQUARE:
                path = regularPolygonPath(x, y, size, 4, Math.PI / 4); // 旋转成菱形方向的正方形用于对齐视觉
                break;
            case DIAMOND:
                path = diamondPath(x, y, size);
                break;
            case PENTAGON:
                path = regularPolygonPath(x, y, size, 5, -Math.PI / 2);
                break;
            case HEXAGON:
                path = regularPolygonPath(x, y, size, 6, -Math.PI / 2);
                break;
            case HEPTAGON:
                path = regularPolygonPath(x, y, size, 7, -Math.PI / 2);
                break;
            case OCTAGON:
                path = regularPolygonPath(x, y, size, 8, -Math.PI / 2);
                break;
            case POLYGON:
                path = regularPolygonPath(x, y, size, Math.max(3, rs.sides), -Math.PI / 2);
                break;
            case STAR:
                path = starPath(x, y, size, Math.max(3, rs.starPoints));
                break;
            case CIRCLE:
            default:
                path = null; // 已在上层回退
                break;
        }

        if (path == null) {
            super.render(item, target, props);
            return;
        }

        // 先填充，再描边（与默认节点样式保持一致的边界表现）
        g2.setColor(fill);
        g2.fill(path);

        if (borderWidth > 0f) {
            Stroke old = g2.getStroke();
            g2.setStroke(new BasicStroke(borderWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g2.setColor(border);
            g2.draw(path);
            g2.setStroke(old);
        }
    }

    private Path2D.Float regularPolygonPath(float cx, float cy, float radius, int n, double phase) {
        Path2D.Float poly = new Path2D.Float();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n + phase;
            double px = cx + radius * Math.cos(a);
            double py = cy + radius * Math.sin(a);
            if (i == 0) poly.moveTo(px, py); else poly.lineTo(px, py);
        }
        poly.closePath();
        return poly;
    }

    private Path2D.Float diamondPath(float cx, float cy, float r) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(cx, cy - r);
        p.lineTo(cx + r, cy);
        p.lineTo(cx, cy + r);
        p.lineTo(cx - r, cy);
        p.closePath();
        return p;
    }

    private Path2D.Float starPath(float cx, float cy, float r, int points) {
        // “星形”= 外半径 r，内半径 r/2（可根据需要改为属性）
        double rOuter = r;
        double rInner = r * 0.5;
        int n = Math.max(3, points);
        Path2D.Float p = new Path2D.Float();
        for (int i = 0; i < n * 2; i++) {
            double a = Math.PI * i / n - Math.PI / 2;
            double rr = (i % 2 == 0) ? rOuter : rInner;
            double px = cx + rr * Math.cos(a);
            double py = cy + rr * Math.sin(a);
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.closePath();
        return p;
    }

    // ======================== Shape resolution ========================

    private static final class ResolvedShape {
        final ShapeKind kind;
        final int sides;
        final int starPoints;
        ResolvedShape(ShapeKind k, int s, int sp) { this.kind = k; this.sides = s; this.starPoints = sp; }
    }

    private ResolvedShape resolveShape(Node node, PreviewProperties props) {
        Boolean enabled = props.getValue(PROP_ENABLE);
        boolean enabledValue = enabled != null ? enabled : true;
        if (!enabledValue) return null;

        String shapeCol = props.getValue(PROP_SHAPE_COLUMN);
        if (shapeCol == null) shapeCol = "Shape";
        String polyCol = props.getValue(PROP_POLYGON_COLUMN);
        if (polyCol == null) polyCol = "Polygon";
        String defaultShape = props.getValue(PROP_DEFAULT_SHAPE);
        if (defaultShape == null) defaultShape = "Circle";
        Integer starPts = props.getValue(PROP_STAR_POINTS);
        int starPtsValue = starPts != null ? starPts : 5;

        // 默认
        ShapeKind kind = parseShapeName(defaultShape);
        int sides = 0;

        if (node != null) {
            // 先按 Shape 列解析
            Object sv = safeGetAttribute(node, shapeCol);
            if (sv != null) {
                ShapeKind k = parseShapeName(String.valueOf(sv));
                if (k != null) kind = k;
            }
            // 再按 Polygon 列解析边数（仅当目标是 POLYGON 或未指定具体名称时使用）
            if (kind == ShapeKind.POLYGON || kind == null || kind == ShapeKind.CIRCLE) {
                Object pv = safeGetAttribute(node, polyCol);
                Integer sidesInt = parseInt(pv);
                if (sidesInt != null && sidesInt >= 3) {
                    kind = ShapeKind.POLYGON;
                    sides = sidesInt;
                }
            }
        }

        if (kind == null) kind = ShapeKind.CIRCLE; // 保底回退
        return new ResolvedShape(kind, sides, starPtsValue);
    }

    private Object safeGetAttribute(Node n, String col) {
        try {
            if (col == null || col.isBlank()) return null;
            return n.getAttribute(col);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Integer parseInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return null; }
    }

    private ShapeKind parseShapeName(String name) {
        if (name == null) return null;
        String s = name.trim().toUpperCase(Locale.ROOT);
        switch (s) {
            case "CIRCLE":
                return ShapeKind.CIRCLE;
            case "TRIANGLE":
                return ShapeKind.TRIANGLE;
            case "SQUARE":
                return ShapeKind.SQUARE;
            case "DIAMOND":
                return ShapeKind.DIAMOND;
            case "PENTAGON":
                return ShapeKind.PENTAGON;
            case "HEXAGON":
                return ShapeKind.HEXAGON;
            case "HEPTAGON":
                return ShapeKind.HEPTAGON;
            case "OCTAGON":
                return ShapeKind.OCTAGON;
            case "STAR":
                return ShapeKind.STAR;
            case "POLYGON":
                return ShapeKind.POLYGON;
            default:
                return null;
        }
    }
}
