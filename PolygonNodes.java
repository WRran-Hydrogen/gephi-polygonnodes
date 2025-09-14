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
 */
package org.zeager.polygonnodes;

import java.awt.Color;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.preview.api.*;
import org.gephi.preview.plugin.items.NodeItem;
import org.gephi.preview.plugin.renderers.NodeRenderer;
import org.gephi.preview.spi.Renderer;
import org.gephi.preview.types.DependantColor;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import processing.core.*;

// 新增：PDF/SVG 所需导入
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfGState;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@ServiceProvider(service = Renderer.class)
public class PolygonNodes extends NodeRenderer {

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(PolygonNodes.class, "PolygonNodes.name");
    }

    @Override
    public void render(Item item, RenderTarget target, PreviewProperties properties) {
        // 统一判断当前节点是否需要渲染为 n-gon
        int numSides = resolveNumSides(item, properties);

        if (target instanceof ProcessingTarget) {
            if (numSides != -1) {
                renderProcessing(item, (ProcessingTarget) target, properties, numSides);
            } else {
                super.render(item, target, properties);
            }
        } else if (target instanceof SVGTarget) {
            if (numSides != -1) {
                renderSVG(item, (SVGTarget) target, properties, numSides);
            } else {
                super.render(item, target, properties);
            }
        } else if (target instanceof PDFTarget) {
            if (numSides != -1) {
                renderPDF(item, (PDFTarget) target, properties, numSides);
            } else {
                super.render(item, target, properties);
            }
        } else {
            super.render(item, target, properties);
        }
    }

    /**
     * 统一解析当前 item 是否应渲染为多边形，以及多边形边数。
     * 返回 -1 表示不渲染为多边形。
     */
    private int resolveNumSides(Item item, PreviewProperties properties) {
        try {
            if (!properties.getBooleanValue("PolygonNodes.property.enable")) {
                return -1;
            }
        } catch (Exception e) {
            return -1;
        }

        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        String itemId = item.getSource().toString();

        for (Node n : graphModel.getGraph().getNodes()) {
            try {
                if (n.getNodeData().getId().equals(itemId)) {
                    Object val = n.getNodeData().getAttributes().getValue("Polygon");
                    if (val instanceof Integer) {
                        int sides = (Integer) val;
                        if (sides >= 3) {
                            return sides;
                        }
                    }
                    break;
                }
            } catch (Exception ignore) {
            }
        }
        return -1;
    }

    public void renderProcessing(Item item, ProcessingTarget target, PreviewProperties properties, int numSides) {
        //Params
        Float x = item.getData(NodeItem.X);
        Float y = item.getData(NodeItem.Y);
        Float size = item.getData(NodeItem.SIZE);
        Color color = item.getData(NodeItem.COLOR);
        Color borderColor = ((DependantColor) properties.getValue(PreviewProperty.NODE_BORDER_COLOR)).getColor(color);
        float borderSize = properties.getFloatValue(PreviewProperty.NODE_BORDER_WIDTH);
        int alpha = (int) ((properties.getFloatValue(PreviewProperty.NODE_OPACITY) / 100f) * 255f);
        if (alpha > 255) {
            alpha = 255;
        }

        //Graphics
        PGraphics graphics = target.getGraphics();

        if (borderSize > 0) {
            graphics.stroke(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), alpha);
            graphics.strokeWeight(borderSize);
        } else {
            graphics.noStroke();
        }
        graphics.fill(color.getRed(), color.getGreen(), color.getBlue(), alpha);

        // Draw n-gon
        graphics.beginShape();
        for (int i = 0; i < numSides; i++) {
            double angle = 2 * Math.PI / numSides;
            float calcX, calcY;
            if (numSides % 2 == 0) {
                calcX = (float) (x + (size * .6) * Math.cos(i * angle - Math.PI / 4));
                calcY = (float) (y - (size * .6) * Math.sin(i * angle - Math.PI / 4));
            } else {
                calcX = (float) (x + (size * .6) * Math.cos(i * angle));
                calcY = (float) (y - (size * .6) * Math.sin(i * angle));
            }
            graphics.vertex(calcX, calcY);
        }
        graphics.endShape(PGraphics.CLOSE);
    }

    // ------------ 新增：PDF 导出 ------------
    public void renderPDF(Item item, PDFTarget target, PreviewProperties properties, int numSides) {
        // 基本参数与颜色、透明度
        Float x = item.getData(NodeItem.X);
        Float y = item.getData(NodeItem.Y);
        Float size = item.getData(NodeItem.SIZE);
        Color color = item.getData(NodeItem.COLOR);
        Color borderColor = ((DependantColor) properties.getValue(PreviewProperty.NODE_BORDER_COLOR)).getColor(color);
        float borderSize = properties.getFloatValue(PreviewProperty.NODE_BORDER_WIDTH);
        float alphaF = properties.getFloatValue(PreviewProperty.NODE_OPACITY) / 100f;
        if (alphaF > 1f) alphaF = 1f;

        PdfContentByte cb = target.getContentByte(); // PDF 画布（iText） [3](https://gephi.org/gephi/0.9.0/apidocs/org/gephi/preview/api/PDFTarget.html)
        cb.saveState();
        try {
            // 透明度
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(alphaF);
            gs.setStrokeOpacity(alphaF);
            cb.setGState(gs);

            // 颜色与线宽
            cb.setRGBColorFill(color.getRed(), color.getGreen(), color.getBlue());
            if (borderSize > 0f) {
                cb.setRGBColorStroke(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue());
                cb.setLineWidth(borderSize);
            }

            // 路径
            double angle = 2 * Math.PI / numSides;
            for (int i = 0; i < numSides; i++) {
                float calcX, calcY;
                if (numSides % 2 == 0) {
                    calcX = (float) (x + (size * .6) * Math.cos(i * angle - Math.PI / 4));
                    calcY = (float) (y - (size * .6) * Math.sin(i * angle - Math.PI / 4));
                } else {
                    calcX = (float) (x + (size * .6) * Math.cos(i * angle));
                    calcY = (float) (y - (size * .6) * Math.sin(i * angle));
                }
                if (i == 0) {
                    cb.moveTo(calcX, calcY);
                } else {
                    cb.lineTo(calcX, calcY);
                }
            }
            cb.closePath();

            if (borderSize > 0f) {
                cb.fillStroke();
            } else {
                cb.fill();
            }
        } finally {
            cb.restoreState();
        }
        // iText 的这些 API 可用于在 Gephi PDF target 上绘制路径与设置 alpha。[4](https://gephi.org/javadoc/0.9.3/org/gephi/preview/api/PDFTarget.html)[5](https://www.javatips.net/api/com.itextpdf.text.pdf.pdfcontentbyte)
    }

    // ------------ 新增：SVG 导出 ------------
    public void renderSVG(Item item, SVGTarget target, PreviewProperties properties, int numSides) {
        Float x = item.getData(NodeItem.X);
        Float y = item.getData(NodeItem.Y);
        Float size = item.getData(NodeItem.SIZE);
        Color color = item.getData(NodeItem.COLOR);
        Color borderColor = ((DependantColor) properties.getValue(PreviewProperty.NODE_BORDER_COLOR)).getColor(color);
        float borderSize = properties.getFloatValue(PreviewProperty.NODE_BORDER_WIDTH);
        float alphaF = properties.getFloatValue(PreviewProperty.NODE_OPACITY) / 100f;
        if (alphaF > 1f) alphaF = 1f;

        Document doc = target.getDocument(); // Batik 的 DOM 文档 [1](https://gephi.org/gephi/0.9.2/apidocs/org/gephi/preview/api/RenderTarget.html)
        String svgNS = doc.getDocumentElement().getNamespaceURI();
        Element polygon = doc.createElementNS(svgNS, "polygon");

        // points 属性
        StringBuilder pts = new StringBuilder();
        double angle = 2 * Math.PI / numSides;
        for (int i = 0; i < numSides; i++) {
            float calcX, calcY;
            if (numSides % 2 == 0) {
                calcX = (float) (x + (size * .6) * Math.cos(i * angle - Math.PI / 4));
                calcY = (float) (y - (size * .6) * Math.sin(i * angle - Math.PI / 4));
            } else {
                calcX = (float) (x + (size * .6) * Math.cos(i * angle));
                calcY = (float) (y - (size * .6) * Math.sin(i * angle));
            }
            if (i > 0) pts.append(' ');
            pts.append(calcX).append(',').append(calcY);
        }
        polygon.setAttribute("points", pts.toString());

        // 颜色、透明度、描边
        polygon.setAttribute("fill", rgb(color));
        polygon.setAttribute("fill-opacity", String.valueOf(alphaF));
        if (borderSize > 0f) {
            polygon.setAttribute("stroke", rgb(borderColor));
            polygon.setAttribute("stroke-width", String.valueOf(borderSize));
            polygon.setAttribute("stroke-opacity", String.valueOf(alphaF));
        } else {
            polygon.setAttribute("stroke", "none");
        }

        // 追加到根 <svg>（也可根据需要追加到某个 <g> 层）
        doc.getDocumentElement().appendChild(polygon);
    }

    private static String rgb(Color c) {
        return "rgb(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + ")";
    }

    @Override
    public PreviewProperty[] getProperties() {
        //Creates the same properties as the default renderer 
        //but adds a new one to control polygon shaped nodes rendering
        PreviewProperty[] props = super.getProperties();
        PreviewProperty[] newProps = new PreviewProperty[props.length + 1];

        System.arraycopy(props, 0, newProps, 0, props.length);

        newProps[newProps.length - 1] = PreviewProperty.createProperty(this, "PolygonNodes.property.enable", Boolean.class,
                NbBundle.getMessage(PolygonNodes.class, "PolygonNodes.property.name"),
                NbBundle.getMessage(PolygonNodes.class, "PolygonNodes.property.description"),
                PreviewProperty.CATEGORY_NODES).setValue(true);
        return newProps;
    }

    @Override
    public boolean isRendererForitem(Item item, PreviewProperties properties) {
        return item.getType().equals(Item.NODE);
    }

    @Override
    public void preProcess(PreviewModel previewModel) {
        //Not implemented
    }
}

