/*
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.awt;

import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.MultipleGradientPaint.ColorSpaceType;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

/**
 * Provides the actual implementation for the RadialGradientPaint.
 * This is where the pixel processing is done.  A RadialGradientPaint
 * only supports circular gradients, but it should be possible to scale
 * the circle to look approximately elliptical, by means of a
 * gradient transform passed into the RadialGradientPaint constructor.
 *
 * @author Nicholas Talian, Vincent Hardy, Jim Graham, Jerry Evans
 */
final class RadialGradientPaintContext extends MultipleGradientPaintContext {

    /** True when (focus == center).  */
    private boolean isSimpleFocus = false;

    /** True when (cycleMethod == NO_CYCLE). */
    private boolean isNonCyclic = false;

    /** Radius of the outermost circle defining the 100% gradient stop. */
    private float radius;

    /** Variables representing center and focus points. */
    private float centerX, centerY, focusX, focusY;

    /** Radius of the gradient circle squared. */
    private float radiusSq;

    /** Constant part of X, Y user space coordinates. */
    private float constA, constB;

    /** Constant second order delta for simple loop. */
    private float gDeltaDelta;

    /**
     * This value represents the solution when focusX == X.  It is called
     * trivial because it is easier to calculate than the general case.
     */
    private float trivial;

    /** Amount for offset when clamping focus. */
    private static final float SCALEBACK = .99f;

    /**
     * Constructor for RadialGradientPaintContext.
     *
     * @param paint the {@code RadialGradientPaint} from which this context
     *              is created
     * @param cm the {@code ColorModel} that receives
     *           the {@code Paint} data (this is used only as a hint)
     * @param deviceBounds the device space bounding box of the
     *                     graphics primitive being rendered
     * @param userBounds the user space bounding box of the
     *                   graphics primitive being rendered
     * @param t the {@code AffineTransform} from user
     *          space into device space (gradientTransform should be
     *          concatenated with this)
     * @param hints the hints that the context object uses to choose
     *              between rendering alternatives
     * @param cx the center X coordinate in user space of the circle defining
     *           the gradient.  The last color of the gradient is mapped to
     *           the perimeter of this circle.
     * @param cy the center Y coordinate in user space of the circle defining
     *           the gradient.  The last color of the gradient is mapped to
     *           the perimeter of this circle.
     * @param r the radius of the circle defining the extents of the
     *          color gradient
     * @param fx the X coordinate in user space to which the first color
     *           is mapped
     * @param fy the Y coordinate in user space to which the first color
     *           is mapped
     * @param fractions the fractions specifying the gradient distribution
     * @param colors the gradient colors
     * @param cycleMethod either NO_CYCLE, REFLECT, or REPEAT
     * @param colorSpace which colorspace to use for interpolation,
     *                   either SRGB or LINEAR_RGB
     */
    RadialGradientPaintContext(RadialGradientPaint paint,
                               ColorModel cm,
                               Rectangle deviceBounds,
                               Rectangle2D userBounds,
                               AffineTransform t,
                               RenderingHints hints,
                               float cx, float cy,
                               float r,
                               float fx, float fy,
                               float[] fractions,
                               Color[] colors,
                               CycleMethod cycleMethod,
                               ColorSpaceType colorSpace)
    {
        super(paint, cm, deviceBounds, userBounds, t, hints,
              fractions, colors, cycleMethod, colorSpace);

        centerX = cx;
        centerY = cy;
        focusX = fx;
        focusY = fy;
        radius = r;

        this.isSimpleFocus = (focusX == centerX) && (focusY == centerY);
        this.isNonCyclic = (cycleMethod == CycleMethod.NO_CYCLE);

        radiusSq = radius * radius;

        float dX = focusX - centerX;
        float dY = focusY - centerY;

        double distSq = (dX * dX) + (dY * dY);

        if (distSq > radiusSq * SCALEBACK) {
            float scalefactor = (float)Math.sqrt(radiusSq * SCALEBACK / distSq);
            dX = dX * scalefactor;
            dY = dY * scalefactor;
            focusX = centerX + dX;
            focusY = centerY + dY;
        }

        trivial = (float)Math.sqrt(radiusSq - (dX * dX));

        constA = a02 - centerX;
        constB = a12 - centerY;

        gDeltaDelta = 2 * ( a00 *  a00 +  a10 *  a10) / radiusSq;
    }

    /**
     * Return a Raster containing the colors generated for the graphics
     * operation.
     *
     * @param x,y,w,h the area in device space for which colors are
     * generated.
     */
    protected void fillRaster(int[] pixels, int off, int adjust,
                              int x, int y, int w, int h)
    {
        if (isSimpleFocus && isNonCyclic && isSimpleLookup) {
            simpleNonCyclicFillRaster(pixels, off, adjust, x, y, w, h);
        } else {
            cyclicCircularGradientFillRaster(pixels, off, adjust, x, y, w, h);
        }
    }

    /**
     * This code works in the simplest of cases, where the focus == center
     * point, the gradient is noncyclic, and the gradient lookup method is
     * fast (single array index, no conversion necessary).
     */
    private void simpleNonCyclicFillRaster(int[] pixels, int off, int adjust,
                                           int x, int y, int w, int h)
    {
        /* We calculate sqrt(X^2 + Y^2) relative to the radius
         * size to get the fraction for the color to use.
         *
         * Each step along the scanline adds (a00, a10) to (X, Y).
         * If we precalculate:
         *   gRel = X^2+Y^2
         * for the start of the row, then for each step we need to
         * calculate:
         *   gRel' = (X+a00)^2 + (Y+a10)^2
         *         = X^2 + 2*X*a00 + a00^2 + Y^2 + 2*Y*a10 + a10^2
         *         = (X^2+Y^2) + 2*(X*a00+Y*a10) + (a00^2+a10^2)
         *         = gRel + 2*(X*a00+Y*a10) + (a00^2+a10^2)
         *         = gRel + 2*DP + SD
         * (where DP = dot product between X,Y and a00,a10
         *  and   SD = dot product square of the delta vector)
         * For the step after that we get:
         *   gRel'' = (X+2*a00)^2 + (Y+2*a10)^2
         *          = X^2 + 4*X*a00 + 4*a00^2 + Y^2 + 4*Y*a10 + 4*a10^2
         *          = (X^2+Y^2) + 4*(X*a00+Y*a10) + 4*(a00^2+a10^2)
         *          = gRel  + 4*DP + 4*SD
         *          = gRel' + 2*DP + 3*SD
         * The increment changed by:
         *     (gRel'' - gRel') - (gRel' - gRel)
         *   = (2*DP + 3*SD) - (2*DP + SD)
         *   = 2*SD
         * Note that this value depends only on the (inverse of the)
         * transformation matrix and so is a constant for the loop.
         * To make this all relative to the unit circle, we need to
         * divide all values as follows:
         *   [XY] /= radius
         *   gRel /= radiusSq
         *   DP   /= radiusSq
         *   SD   /= radiusSq
         */
        float rowX = (a00*x) + (a01*y) + constA;
        float rowY = (a10*x) + (a11*y) + constB;

        float gDeltaDelta = this.gDeltaDelta;

        adjust += w;

        int rgbclip = gradient[fastGradientArraySize];

        for (int j = 0; j < h; j++) {
            float gRel   =      (rowX * rowX + rowY * rowY) / radiusSq;
            float gDelta = (2 * ( a00 * rowX +  a10 * rowY) / radiusSq +
                            gDeltaDelta/2);

            /* Use optimized loops for any cases where gRel >= 1.
             * We do not need to calculate sqrt(gRel) for these
             * values since sqrt(N>=1) == (M>=1).
             * Note that gRel follows a parabola which can only be < 1
             * for a small region around the center on each scanline. In
             * particular:
             *   gDeltaDelta is always positive
             *   gDelta is <0 until it crosses the midpoint, then >0
             * To the left and right of that region, it will always be
             * >=1 out to infinity, so we can process the line in 3
             * regions:
             *   out to the left  - quick fill until gRel < 1, updating gRel
             *   in the heart     - slow fraction=sqrt fill while gRel < 1
             *   out to the right - quick fill rest of scanline, ignore gRel
             */
            int i = 0;
            while (i < w && gRel >= 1.0f) {
                pixels[off + i] = rgbclip;
                gRel += gDelta;
                gDelta += gDeltaDelta;
                i++;
            }
            while (i < w && gRel < 1.0f) {
                int gIndex;

                if (gRel <= 0) {
                    gIndex = 0;
                } else {
                    float fIndex = gRel * SQRT_LUT_SIZE;
                    int iIndex = (int) (fIndex);
                    float s0 = sqrtLut[iIndex];
                    float s1 = sqrtLut[iIndex+1] - s0;
                    fIndex = s0 + (fIndex - iIndex) * s1;
                    gIndex = (int) (fIndex * fastGradientArraySize);
                }

                pixels[off + i] = gradient[gIndex];

                gRel += gDelta;
                gDelta += gDeltaDelta;
                i++;
            }
            while (i < w) {
                pixels[off + i] = rgbclip;
                i++;
            }

            off += adjust;
            rowX += a01;
            rowY += a11;
        }
    }

    private static final int SQRT_LUT_SIZE = (1 << 11);
    private static float[] sqrtLut = new float[SQRT_LUT_SIZE+1];
    static {
        for (int i = 0; i < sqrtLut.length; i++) {
            sqrtLut[i] = (float) Math.sqrt(i / ((float) SQRT_LUT_SIZE));
        }
    }

    /**
     * Fill the raster, cycling the gradient colors when a point falls outside
     * of the perimeter of the 100% stop circle.
     *
     * This calculation first computes the intersection point of the line
     * from the focus through the current point in the raster, and the
     * perimeter of the gradient circle.
     *
     * Then it determines the percentage distance of the current point along
     * that line (focus is 0%, perimeter is 100%).
     *
     * Equation of a circle centered at (a,b) with radius r:
     *     (x-a)^2 + (y-b)^2 = r^2
     * Equation of a line with slope m and y-intercept b:
     *     y = mx + b
     * Replacing y in the circle equation and solving using the quadratic
     * formula produces the following set of equations.  Constant factors have
     * been extracted out of the inner loop.
     */
    private void cyclicCircularGradientFillRaster(int[] pixels, int off,
                                                  int adjust,
                                                  int x, int y,
                                                  int w, int h)
    {
        final double constC =
            -radiusSq + (centerX * centerX) + (centerY * centerY);

        double A, B, C;

        double slope, yintcpt;

        double solutionX, solutionY;

        final float constX = (a00*x) + (a01*y) + a02;
        final float constY = (a10*x) + (a11*y) + a12;

        final float precalc2 =  2 * centerY;
        final float precalc3 = -2 * centerX;

        float g;

        float det;

        float currentToFocusSq;

        float intersectToFocusSq;

        float deltaXSq, deltaYSq;

        int indexer = off;

        int pixInc = w+adjust;

        for (int j = 0; j < h; j++) {

            float X = (a01*j) + constX;
            float Y = (a11*j) + constY;

            for (int i = 0; i < w; i++) {

                if (X == focusX) {
                    solutionX = focusX;
                    solutionY = centerY;
                    solutionY += (Y > focusY) ? trivial : -trivial;
                } else {
                    slope = (Y - focusY) / (X - focusX);
                    yintcpt = Y - (slope * X);

                    A = (slope * slope) + 1;
                    B = precalc3 + (-2 * slope * (centerY - yintcpt));
                    C = constC + (yintcpt* (yintcpt - precalc2));

                    det = (float)Math.sqrt((B * B) - (4 * A * C));
                    solutionX = -B;

                    solutionX += (X < focusX)? -det : det;
                    solutionX = solutionX / (2 * A); 
                    solutionY = (slope * solutionX) + yintcpt;
                }


                deltaXSq = X - focusX;
                deltaXSq = deltaXSq * deltaXSq;

                deltaYSq = Y - focusY;
                deltaYSq = deltaYSq * deltaYSq;

                currentToFocusSq = deltaXSq + deltaYSq;

                deltaXSq = (float)solutionX - focusX;
                deltaXSq = deltaXSq * deltaXSq;

                deltaYSq = (float)solutionY - focusY;
                deltaYSq = deltaYSq * deltaYSq;

                intersectToFocusSq = deltaXSq + deltaYSq;

                g = (float)Math.sqrt(currentToFocusSq / intersectToFocusSq);

                pixels[indexer + i] = indexIntoGradientsArrays(g);

                X += a00;
                Y += a10;
            } 

            indexer += pixInc;
        } 
    }
}