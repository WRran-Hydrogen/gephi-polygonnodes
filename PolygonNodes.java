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

Portions Copyrighted 2011 Gephi Consortium.
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

/**
 * Extends and replaces default node renderer and implements polygon shaped nodes.
 * <p>
 * Allows for nodes to be rendered as a regular polygon with an arbitrary number of sides by 
 * adding a column of Integers in the data table named "Polygon." The value corresponds to the number of sides.
 * NOTE: the renderer must be enabled in the "Manage Renderers" tab.
 * @author zde <zde6919@rit.edu>
 */
@ServiceProvider(service = Renderer.class)
public class PolygonNodes extends NodeRenderer {
    
    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(PolygonNodes.class, "PolygonNodes.name");
    }

    @Override
    public void render(Item item, RenderTarget target, PreviewProperties properties) {
        if (target instanceof ProcessingTarget) {
            GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
            int renderAsNgon = -1;
            for (Node n : graphModel.getGraph().getNodes()) {
                try {
                    if (n.getNodeData().getId().equals(item.getSource().toString())
                        && (Integer) n.getNodeData().getAttributes().getValue("Polygon") >= 3
                        && properties.getBooleanValue("PolygonNodes.property.enable")) {
                        renderAsNgon = (Integer) n.getNodeData().getAttributes().getValue("Polygon");
                        break;
                    }
                }
                catch (Exception e) {}
            }
            if (renderAsNgon != -1) {
                renderProcessing(item, (ProcessingTarget) target, properties, renderAsNgon);
            } else {
                super.render(item, target, properties);
            }
        } else if (target instanceof SVGTarget) {
            renderSVG(item, (SVGTarget) target, properties);
        } else if (target instanceof PDFTarget) {
            renderPDF(item, (PDFTarget) target, properties);
        } else {
            super.render(item, target, properties);
        }
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
        for (int i = 0; i < numSides; i++){
            double angle = 2 * Math.PI / numSides;
            float calcX, calcY;
            if (numSides % 2 == 0) {
                calcX = (float)(x + (size * .6) * Math.cos(i * angle - Math.PI/4));
                calcY = (float)(y - (size * .6) * Math.sin(i * angle - Math.PI/4));
            } else {
                calcX = (float)(x + (size * .6) * Math.cos(i * angle));
                calcY = (float)(y - (size * .6) * Math.sin(i * angle));
            }
            graphics.vertex(calcX, calcY);
        }
        graphics.endShape(PGraphics.CLOSE);
    }

    @Override
    public void renderPDF(Item item, PDFTarget target, PreviewProperties properties) {
        //Not implemented
    }

    @Override
    public void renderSVG(Item item, SVGTarget target, PreviewProperties properties) {
        //Not implemented
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
