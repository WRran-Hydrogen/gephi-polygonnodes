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

package org.zeager.polygonnodes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.geom.Path2D;

import org.gephi.graph.api.Column;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
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

import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

// PDFBox (Gephi 0.10.x 使用 PDFBox 而非 iText)
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtGState;

/**
 * 让节点以正多边形渲染：在 Data Laboratory 添加整型列 "Polygon"，值为边数 (>=3)
 * 在 Preview 的 "Manage Renderers" 中启用本渲染器即可。
 *
 * 适配 Gephi 0.10.1：
 * - 屏显：G2DTarget + Graphics2D
 * - SVG：Batik Document 追加 <polygon>
 * - PDF：PDFBox PDPageContentStream（含透明/描边）
 */
@ServiceProvider(service = Renderer.class)
public class PolygonNodes extends NodeRenderer {

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(PolygonNodes.class, "PolygonNodes.name");
    }

    @Override
    public void render(Item item, RenderTarget target, PreviewProperties properties) {
        final int numSides = resolveNumSides(item, properties);
        if (numSides < 3) {
            // 不满足多边形条件 -> 回落到默认节点渲染
            super.render(item, target, properties);
            return;
        }

        if (target instanceof G2DTarget) {
            renderG2D(item, (G2DTarget) target, properties, numSides);
        } else if (target instanceof SVGTarget) {
            renderSVG(item, (SVGTarget) target, properties, numSides);
        } else if (target instanceof PDFTarget) {
            renderPDF(item, (PDFTarget) target, properties, numSides);
        } else {
            // 其它目标 -> 走父类默认
            super.render(item, target, properties);
        }
    }

    // 统一解析当前节点是否渲染为多边形，以及边数
    private int resolveNumSides(Item item, PreviewProperties properties) {
        try {
            Boolean enabled = properties.getBooleanValue("PolygonNodes.property.enable");
            if (enabled == null || !enabled) return -1;
        } catch (Exception e) {
            return -1;
        }

        // 直接从 item 的 source 取 Graph API 的 Node
        Object src = item.getSource();
        if (!(src instanceof Node)) return -1;
        Node node = (Node) src;

        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        if (graphModel == null) return -1;

        Column polygonCol = graphModel.getNodeTable().getColumn("Polygon");
        if (polygonCol == null) return -1;

        Object val = node.getAttribute(polygonCol);
        if (val instanceof Number) {
            int sides = ((Number) val).intValue();
            return sides >= 3 ? sides : -1;
        }
        return -1;
    }

    /* -------------------- G2D（预览/PNG 等） -------------------- */
    private void renderG2D(Item item, G2DTarget target, PreviewProperties properties, int numSides) {
        // 基本参数
        Float x = item.getData(NodeItem.X);
        Float y = item.getData(NodeItem.Y);
        Float size = item.getData(NodeItem.SIZE);
        Color color = item.getData(NodeItem.COLOR);
        Color borderColor = ((DependantColor) properties.getValue(PreviewProperty.NODE_BORDER_COLOR)).getColor(color);
        float borderSize = properties.getFloatValue(PreviewProperty.NODE_BORDER_WIDTH);
        float alphaF = clamp01(properties.getFloatValue(PreviewProperty.NODE_OPACITY) / 100f);

        Graphics2D g2 = getGraphics2D(target);

        // 多边形路径
        Path2D.Float path = buildPolygonPath(x, y, size, numSides);

        // 透明度
        Composite oldCmp = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaF));

        // 填充
        g2.setColor(color);
        g2.fill(path);

        // 描边
        if (borderSize > 0f) {
            g2.setStroke(new BasicStroke(borderSize));
            g2.setColor(borderColor);
            g2.draw(path);
        }

        // 还原透明度
        g2.setComposite(oldCmp);
    }

    // 兼容可能存在的 getGraphics / getGraphics2D 命名差异（反射优先取 getGraphics）
    private Graphics2D getGraphics2D(G2DTarget target) {
        try {
            try {
                return (Graphics2D) target.getClass().getMethod("getGraphics").invoke(target);
            } catch (NoSuchMethodException e) {
                return (Graphics2D) target.getClass().getMethod("getGraphics2D").invoke(target);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot acquire Graphics2D from G2DTarget", e);
        }
    }

    /* -------------------- SVG 导出 -------------------- */
    private void renderSVG(Item item, SVGTarget target, PreviewProperties properties, int numSides) {
        Float x = item.getData(NodeItem.X);
        Float y = item.getData(NodeItem.Y);
        Float size = item.getData(NodeItem.SIZE);
        Color color = item.getData(NodeItem.COLOR);
        Color borderColor = ((DependantColor) properties.getValue(PreviewProperty.NODE_BORDER_COLOR)).getColor(color);
        float borderSize = properties.getFloatValue(PreviewProperty.NODE_BORDER_WIDTH);
        float alphaF = clamp01(properties.getFloatValue(PreviewProperty.NODE_OPACITY) / 100f);

        Document doc = target.getDocument(); // Batik Document
        String svgNS = doc.getDocumentElement().getNamespaceURI();
        Element polygon = doc.createElementNS(svgNS, "polygon");

        // points
        String points = buildPolygonPointsAttribute(x, y, size, numSides);
        polygon.setAttribute("points", points);

        // 填充 & 透明
        polygon.setAttribute("fill", rgb(color));
        polygon.setAttribute("fill-opacity", String.valueOf(alphaF));

        // 描边
        if (borderSize > 0f) {
            polygon.setAttribute("stroke", rgb(borderColor));
            polygon.setAttribute("stroke-width", String.valueOf(borderSize));
            polygon.setAttribute("stroke-opacity", String.valueOf(alphaF));
        } else {
            polygon.setAttribute("stroke", "none");
        }

        // 追加到 <svg> 根元素（如需控制层次，可追加到指定 <g>）
        doc.getDocumentElement().appendChild(polygon);
    }

    /* -------------------- PDF 导出（PDFBox） -------------------- */
    private void renderPDF(Item item, PDFTarget target, PreviewProperties properties, int numSides) {
        Float x = item.getData(NodeItem.X);
        Float y = item.getData(NodeItem.Y);
        Float size = item.getData(NodeItem.SIZE);
        Color color = item.getData(NodeItem.COLOR);
        Color borderColor = ((DependantColor) properties.getValue(PreviewProperty.NODE_BORDER_COLOR)).getColor(color);
        float borderSize = properties.getFloatValue(PreviewProperty.NODE_BORDER_WIDTH);
        float alphaF = clamp01(properties.getFloatValue(PreviewProperty.NODE_OPACITY) / 100f);

        // 取得 PDFBox 的 PDPageContentStream（0.10.x 的 PDFTarget 提供 PDFBox 对象）
        // 具体方法名在 0.10.x 为 getContentStream（若你本地 API 名称不同，下面反射会尝试常见别名）
        PDPageContentStream cs = getPDPageContentStream(target);

        try {
            // 透明度
            PDExtGState gs = new PDExtGState();
            gs.setNonStrokingAlphaConstant(alphaF);
            gs.setStrokingAlphaConstant(alphaF);
            cs.setGraphicsStateParameters(gs);

            // 颜色与线宽
            cs.setNonStrokingColor(color.getRed(), color.getGreen(), color.getBlue());
            if (borderSize > 0f) {
                cs.setStrokingColor(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue());
                cs.setLineWidth(borderSize);
            }

            // 路径：与预览一致（偶数边旋转 45°）
            final double angle = 2 * Math.PI / numSides;
            for (int i = 0; i < numSides; i++) {
                float[] pt = computeVertex(x, y, size, numSides, angle, i);
                if (i == 0) cs.moveTo(pt[0], pt[1]);
                else cs.lineTo(pt[0], pt[1]);
            }
            cs.closePath();

            if (borderSize > 0f) cs.fillAndStroke();
            else cs.fill();

        } catch (Exception e) {
            throw new IllegalStateException("Error while rendering polygon to PDF", e);
        }
    }

    // 通过反射获取 PDPageContentStream，以兼容可能的命名差异（getContentStream / getPageContentStream）
    private PDPageContentStream getPDPageContentStream(PDFTarget target) {
        try {
            try {
                return (PDPageContentStream) target.getClass().getMethod("getContentStream").invoke(target);
            } catch (NoSuchMethodException e1) {
                return (PDPageContentStream) target.getClass().getMethod("getPageContentStream").invoke(target);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot acquire PDPageContentStream from PDFTarget (0.10.x)", e);
        }
    }

    /* -------------------- 工具方法 -------------------- */

    private static Path2D.Float buildPolygonPath(float x, float y, float size, int numSides) {
        Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO);
        final double angle = 2 * Math.PI / numSides;

        for (int i = 0; i < numSides; i++) {
            float[] pt = computeVertex(x, y, size, numSides, angle, i);
            if (i == 0) path.moveTo(pt[0], pt[1]);
            else path.lineTo(pt[0], pt[1]);
        }
        path.closePath();
        return path;
    }

    private static String buildPolygonPointsAttribute(float x, float y, float size, int numSides) {
        StringBuilder sb = new StringBuilder();
        final double angle = 2 * Math.PI / numSides;
        for (int i = 0; i < numSides; i++) {
            float[] pt = computeVertex(x, y, size, numSides, angle, i);
            if (i > 0) sb.append(' ');
            sb.append(pt[0]).append(',').append(pt[1]);
        }
        return sb.toString();
    }

    // 与原 Processing 版本一致：偶数边整体旋转 45°
    private static float[] computeVertex(float x, float y, float size, int numSides, double angle, int i) {
        final double rot = (numSides % 2 == 0) ? (Math.PI / 4) : 0.0;
        float vx = (float) (x + (size * .6) * Math.cos(i * angle - rot));
        float vy = (float) (y - (size * .6) * Math.sin(i * angle - rot));
        return new float[]{vx, vy};
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static String rgb(Color c) {
        return "rgb(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + ")";
    }

    /* -------------------- 其余接口实现 -------------------- */

    @Override
    public PreviewProperty[] getProperties() {
        // 保持与默认节点渲染器相同的属性 + 启用多边形的开关
        PreviewProperty[] props = super.getProperties();
        PreviewProperty[] newProps = new PreviewProperty[props.length + 1];
        System.arraycopy(props, 0, newProps, 0, props.length);

        newProps[newProps.length - 1] = PreviewProperty.createProperty(
                this,
                "PolygonNodes.property.enable",
                Boolean.class,
                NbBundle.getMessage(PolygonNodes.class, "PolygonNodes.property.name"),
                NbBundle.getMessage(PolygonNodes.class, "PolygonNodes.property.description"),
                PreviewProperty.CATEGORY_NODES
        ).setValue(true);
        return newProps;
    }

    // 注意：接口方法名在 API 中就是 isRendererForitem（小写 i），保持一致
    @Override
    public boolean isRendererForitem(Item item, PreviewProperties properties) {
        return item.getType().equals(Item.NODE);
    }

    @Override
    public void preProcess(PreviewModel previewModel) {
        // Not implemented
    }
}
