/* Copyright (c) 2011 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.dma.epd.common.prototype.gui.constants;

import java.awt.Color;

import dk.dma.epd.common.graphics.GraphicsUtil;

/**
 * @author Janus Varmarken
 */
public final class ColorConstants {
    
    public static final int HEADING_ALPHA = 255;
    
    public static final Color VESSEL_COLOR = new Color(74, 97, 205, 255);

    public static final Color VESSEL_HEADING_COLOR = GraphicsUtil.transparentColor(VESSEL_COLOR, HEADING_ALPHA);
    
    public static final Color OWNSHIP_COLOR = Color.BLACK;

    public static final Color OWNSHIP_HEADING_COLOR = GraphicsUtil.transparentColor(OWNSHIP_COLOR, HEADING_ALPHA);
}
