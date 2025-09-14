package org.gephi.plugins.gephinodes;

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


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.Locale;


import org.gephi.graph.api.Node;
import org.gephi.preview.api.G2DTarget;
import org.gephi.preview.api.Item;
import org.gephi.preview.api.PDFTarget;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.RenderTarget;
import org.gephi.preview.api.SVGTarget;
import org.gephi.preview.plugin.items.NodeItem;
import org.gephi.preview.plugin.renderers.NodeRenderer;
import org.gephi.preview.spi.Renderer;
import org.gephi.preview.types.DependantColor;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.NbBundle.Messages;

@ServiceProvider(service = Renderer.class, position = 10)
@Messages({
    "PolygonNodes.name=Polygon & Multiple Shapes (replaces Nodes)",
    "ShapeNodes.property.enable=Enable multiple shapes",
    "ShapeNodes.property.shapeColumn=Shape column name",
    "ShapeNodes.property.polygonColumn=Polygon-sides column name",
    "ShapeNodes.property.defaultShape=Default shape",
    "ShapeNodes.property.starPoints=Star points"
})
public class PolygonNodes extends NodeRenderer {

    private static final String PROP_ENABLE        = "ShapeNodes.property.enable";
    private static final String PROP_SHAPE_COLUMN  = "ShapeNodes.property.shapeColumn";
    private static final String PROP_POLY_COLUMN   = "ShapeNodes.property.polygonColumn";
    private static final String PROP_DEFAULT_SHAPE = "ShapeNodes.property.defaultShape";
    private static final String PROP_STAR_POINTS   = "ShapeNodes.property.starPoints";

    private enum ShapeKind {
        CIRCLE, TRIANGLE, SQUARE, DIAMOND,
        POLYGON, PENTAGON, HEXAGON, HEPTAGON, OCTAGON, STAR
    }


    @Override
    public String getDisplayName() {
        return Bundle.PolygonNodes_name();
    }


    @Override
    public boolean isRendererForitem(Item item, PreviewProperties props) {
        Boolean enabled = props.getValue(PROP_ENABLE);
        boolean enabledValue = enabled != null ? enabled : true;
        return enabledValue && Item.NODE.equals(item.getType());
    }

    @Override public void preProcess(PreviewModel previewModel) { super.preProcess(previewModel); }

    @Override
    public void render(Item item, RenderTarget target, PreviewProperties props) {
        Node node = (item.getSource() instanceof Node) ? (Node) item.getSource() : null;
        ResolvedShape rs = resolveShape(node, props);

        if (rs == null || rs.kind == ShapeKind.CIRCLE) {
            super.render(item, target, props);
            return;
        }
        if (target instanceof G2DTarget) {
            renderG2D(item, (G2DTarget) target, props, rs);
        } else if (target instanceof SVGTarget) {
            renderSVG(item, (SVGTarget) target, props, rs);
        } else if (target instanceof PDFTarget) {
            // 先回退，等 B 步增加 PDF 支持
            super.render(item, target, props);
        } else {
            super.render(item, target, props);
        }
    }

    @Override
    public PreviewProperty[] getProperties() {
        PreviewProperty[] base = super.getProperties();
        PreviewProperty[] extra = new PreviewProperty[] {
            PreviewProperty.createProperty(this, PROP_ENABLE, Boolean.class,
                "Enable multiple shapes", PreviewProperty.CATEGORY_NODES,
                "Enable node shape mapping by attribute").setValue(Boolean.TRUE),
            PreviewProperty.createProperty(this, PROP_SHAPE_COLUMN, String.class,
                "Shape column name", PreviewProperty.CATEGORY_NODES,
                "String column: circle/triangle/square/diamond/polygon/pentagon/hexagon/heptagon/octagon/star")
                .setValue("shape"),
            PreviewProperty.createProperty(this, PROP_POLY_COLUMN, String.class,
                "Polygon-sides column name", PreviewProperty.CATEGORY_NODES,
                "Integer column for polygon sides (>=3)").setValue("polygon"),
            PreviewProperty.createProperty(this, PROP_DEFAULT_SHAPE, String.class,
                "Default shape", PreviewProperty.CATEGORY_NODES,
                "Fallback when no valid attribute").setValue("circle"),
            PreviewProperty.createProperty(this, PROP_STAR_POINTS, Integer.class,
                "Star points", PreviewProperty.CATEGORY_NODES,
                "Number of points for star (>=3)").setValue(Integer.valueOf(5))
        };
        PreviewProperty[] merged = new PreviewProperty[base.length + extra.length];
        System.arraycopy(base, 0, merged, 0, base.length);
        System.arraycopy(extra, 0, merged, base.length, extra.length);
        return merged;
    }

    // ---------- G2D ----------
    private void renderG2D(Item item, G2DTarget target, PreviewProperties props, ResolvedShape rs) {
        Float x = item.getData(NodeItem.X);
        Float y = item.getData(NodeItem.Y);
        Float size = item.getData(NodeItem.SIZE);
        Color fill = item.getData(NodeItem.COLOR);
        Color border = ((DependantColor) props.getValue(PreviewProperty.NODE_BORDER_COLOR)).getColor(fill);
        float borderWidth = props.getFloatValue(PreviewProperty.NODE_BORDER_WIDTH);

        Path2D.Float path = buildPath(x, y, size, rs);

        Graphics2D g2 = target.getGraphics();
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

    // ---------- SVG ----------
    private void renderSVG(Item item, SVGTarget target, PreviewProperties props, ResolvedShape rs) {
        Float x = item.getData(NodeItem.X);
        Float y = item.getData(NodeItem.Y);
        Float size = item.getData(NodeItem.SIZE);
        Color fill = item.getData(NodeItem.COLOR);
        Color border = ((DependantColor) props.getValue(PreviewProperty.NODE_BORDER_COLOR)).getColor(fill);
        float borderWidth = props.getFloatValue(PreviewProperty.NODE_BORDER_WIDTH);

        Path2D.Float path = buildPath(x, y, size, rs);
        String d = toSvgPath(path);

        org.w3c.dom.Document doc = target.getDocument();     // 0.10.x 通常提供 Batik DOM
        org.w3c.dom.Element root = doc.getDocumentElement(); // 或 target.getTopElement()
        String svgNS = root.getNamespaceURI() != null ? root.getNamespaceURI() : "http://www.w3.org/2000/svg";

        org.w3c.dom.Element pathEl = doc.createElementNS(svgNS, "path");
        pathEl.setAttribute("d", d);
        pathEl.setAttribute("fill", rgb(fill));
        if (borderWidth > 0f) {
            pathEl.setAttribute("stroke", rgb(border));
            pathEl.setAttribute("stroke-width", Float.toString(borderWidth));
        } else {
            pathEl.setAttribute("stroke", "none");
        }
        root.appendChild(pathEl);
    }

    // ---------- Path builders ----------
    private Path2D.Float buildPath(float cx, float cy, float r, ResolvedShape rs) {
        switch (rs.kind) {
            case TRIANGLE:  return regularPolygonPath(cx, cy, r, 3, -Math.PI / 2);
            case SQUARE:    return regularPolygonPath(cx, cy, r, 4,  Math.PI / 4);
            case DIAMOND:   return diamondPath(cx, cy, r);
            case PENTAGON:  return regularPolygonPath(cx, cy, r, 5, -Math.PI / 2);
            case HEXAGON:   return regularPolygonPath(cx, cy, r, 6, -Math.PI / 2);
            case HEPTAGON:  return regularPolygonPath(cx, cy, r, 7, -Math.PI / 2);
            case OCTAGON:   return regularPolygonPath(cx, cy, r, 8, -Math.PI / 2);
            case POLYGON:   return regularPolygonPath(cx, cy, r, Math.max(3, rs.sides), -Math.PI / 2);
            case STAR:      return starPath(cx, cy, r, Math.max(3, rs.starPoints));
            default:        return null;
        }
    }

    private Path2D.Float regularPolygonPath(float cx, float cy, float r, int n, double phase) {
        Path2D.Float p = new Path2D.Float();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n + phase;
            double px = cx + r * Math.cos(a);
            double py = cy + r * Math.sin(a);
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.closePath();
        return p;
    }

    private Path2D.Float diamondPath(float cx, float cy, float r) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(cx,     cy - r);
        p.lineTo(cx + r, cy);
        p.lineTo(cx,     cy + r);
        p.lineTo(cx - r, cy);
        p.closePath();
        return p;
    }

    private Path2D.Float starPath(float cx, float cy, float r, int points) {
        double rOuter = r, rInner = r * 0.5;
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

    // ---------- Shape resolution ----------
    private static final class ResolvedShape {
        final ShapeKind kind; final int sides; final int starPoints;
        ResolvedShape(ShapeKind k, int s, int sp) { this.kind = k; this.sides = s; this.starPoints = sp; }
    }

    private ResolvedShape resolveShape(Node node, PreviewProperties props) {
        Boolean enabled = props.getValue(PROP_ENABLE);
        if (enabled != null && !enabled) return null;

        String shapeCol = valOr(props.getValue(PROP_SHAPE_COLUMN), "shape");
        String polyCol  = valOr(props.getValue(PROP_POLY_COLUMN),  "polygon");
        String defShape = valOr(props.getValue(PROP_DEFAULT_SHAPE), "circle");
        Integer starPts = props.getValue(PROP_STAR_POINTS);
        int starPtsValue = starPts != null ? starPts : 5;

        ShapeKind kind = parseShapeName(defShape);
        int sides = 0;

        if (node != null) {
            Object sv = safeAttr(node, shapeCol);
            if (sv != null) {
                ShapeKind k = parseShapeName(String.valueOf(sv));
                if (k != null) kind = k;
            }
            if (kind == ShapeKind.POLYGON || kind == null || kind == ShapeKind.CIRCLE) {
                Integer si = parseInt(safeAttr(node, polyCol));
                if (si != null && si >= 3) { kind = ShapeKind.POLYGON; sides = si; }
            }
        }
        if (kind == null) kind = ShapeKind.CIRCLE;
        return new ResolvedShape(kind, sides, starPtsValue);
    }

    private ShapeKind parseShapeName(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return ShapeKind.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Object safeAttr(Node n, String col) {
        try { if (col == null || col.isBlank()) return null; return n.getAttribute(col); }
        catch (Exception ignore) { return null; }
    }
    private Integer parseInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return null; }
    }
    private String valOr(String v, String def) { return (v == null || v.isBlank()) ? def : v; }

    private String toSvgPath(Path2D path) {
        StringBuilder sb = new StringBuilder();
        PathIterator it = path.getPathIterator(null, 0.25);
        double[] c = new double[6];
        while (!it.isDone()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO: sb.append('M').append(c[0]).append(' ').append(c[1]); break;
                case PathIterator.SEG_LINETO: sb.append(' ').append('L').append(' ').append(c[0]).append(' ').append(c[1]); break;
                case PathIterator.SEG_CLOSE:  sb.append(' ').append('Z'); break;
                default: break;
            }
            it.next();
        }
        return sb.toString();
    }
    private String rgb(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }
}
