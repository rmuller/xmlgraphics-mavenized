/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.render.shading;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.xmlgraphics.java2d.color.ColorUtil;

import org.apache.fop.pdf.PDFDeviceColorSpace;
import org.apache.fop.render.ps.svg.PSSVGGraphics2D;

public abstract class GradientFactory {

    static GradientRegistrar registrar;

    /**
     * Constructor
     * @param registrar The object used to register new embedded objects in the
     * output format.
     */
    public static GradientFactory newInstance(GradientRegistrar theRegistrar) {
        registrar = theRegistrar;
        if (registrar instanceof PSSVGGraphics2D) {
            return new PSGradientFactory();
        } else {
            return new PDFGradientFactory();
        }
    }

    /**
     * Creates a new gradient
     * @param radial Determines whether the gradient is radial
     * @param theColorspace The colorspace used in PDF and Postscript
     * @param theColors The colors to be used in the gradient
     * @param theBounds The bounds of each color
     * @param theCoords The co-ordinates of the gradient
     * @param theMatrix The matrix for any transformations
     * @return Returns the Pattern object of the gradient
     */
    public abstract Pattern createGradient(boolean radial,
            PDFDeviceColorSpace theColorspace, List<Color> theColors, List<Double> theBounds,
            List<Double> theCoords, List<Double> theMatrix);

    protected Pattern makeGradient(boolean radial, PDFDeviceColorSpace theColorspace,
                                   List<Color> theColors, List<Double> theBounds,
                                   List<Double> theCoords, List<Double> theMatrix) {
        Shading myShad;
        Function myfunky;
        Function myfunc;
        List<Double> theCzero;
        List<Double> theCone;
        double interpolation = 1.000;
        List<Function> theFunctions = new ArrayList<Function>();

        int currentPosition;
        int lastPosition = theColors.size() - 1;


        // if 5 elements, the penultimate element is 3.
        // do not go beyond that, because you always need
        // to have a next color when creating the function.

        for (currentPosition = 0; currentPosition < lastPosition;
                currentPosition++) {    // for every consecutive color pair
            Color currentColor = theColors.get(currentPosition);
            Color nextColor = theColors.get(currentPosition + 1);

            // colorspace must be consistent, so we simply convert to sRGB where necessary
            if (!currentColor.getColorSpace().isCS_sRGB()) {
                //Convert to sRGB
                currentColor = ColorUtil.toSRGBColor(currentColor);
                theColors.set(currentPosition, currentColor);
            }
            if (!nextColor.getColorSpace().isCS_sRGB()) {
                //Convert to sRGB
                nextColor = ColorUtil.toSRGBColor(nextColor);
                theColors.set(currentPosition + 1, nextColor);
            }

            theCzero = toColorVector(currentColor);
            theCone = toColorVector(nextColor);

            myfunc = makeFunction(2, null, null, theCzero, theCone,
                    interpolation);

            theFunctions.add(myfunc);

        }                               // end of for every consecutive color pair

        myfunky = makeFunction(3, null, null, theFunctions, theBounds,
                null);

        if (radial) {
            if (theCoords.size() == 6) {
                // make Shading of Type 2 or 3
                myShad = makeShading(3, theColorspace, null, null, false, theCoords,
                                    null, myfunky, null);
            } else {    // if the center x, center y, and radius specifiy
                // the gradient, then assume the same center x, center y,
                // and radius of zero for the other necessary component
                List<Double> newCoords = new ArrayList<Double>();
                newCoords.add(theCoords.get(0));
                newCoords.add(theCoords.get(1));
                newCoords.add(theCoords.get(2));
                newCoords.add(theCoords.get(0));
                newCoords.add(theCoords.get(1));
                newCoords.add(Double.valueOf(0.0));

                myShad = makeShading(3, theColorspace, null, null, false, newCoords,
                        null, myfunky, null);
            }
        } else {
            myShad = makeShading(2, theColorspace, null, null, false, theCoords,
                    null, myfunky, null);
        }
        return makePattern(2, myShad, null, null, theMatrix);
    }

    public abstract Function makeFunction(int functionType, List<Double> theDomain,
            List<Double> theRange, List<Function> theFunctions,
            List<Double> theBounds, List<Double> theEncode);

    public abstract Function makeFunction(int functionType, List<Double> theDomain,
            List<Double> theRange, List<Double> theCZero, List<Double> theCOne,
            double theInterpolationExponentN);

    public abstract Shading makeShading(int theShadingType,
            PDFDeviceColorSpace theColorSpace, List<Double> theBackground, List<Double> theBBox,
            boolean theAntiAlias, List<Double> theCoords, List<Double> theDomain,
            Function theFunction, List<Integer> theExtend);

    public abstract Pattern makePattern(int thePatternType, Shading theShading, List theXUID,
            StringBuffer theExtGState, List<Double> theMatrix);

    private List<Double> toColorVector(Color nextColor) {
        List<Double> vector = new java.util.ArrayList<Double>();
        float[] comps = nextColor.getColorComponents(null);
        for (int i = 0, c = comps.length; i < c; i++) {
            vector.add(Double.valueOf(comps[i]));
        }
        return vector;
    }
}
