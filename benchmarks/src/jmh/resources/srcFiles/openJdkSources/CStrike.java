/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.font;

import java.awt.Rectangle;
import java.awt.geom.*;
import java.util.*;

public final class CStrike extends PhysicalStrike {

    private static native long createNativeStrikePtr(long nativeFontPtr,
                                                     double[] glyphTx,
                                                     double[] invDevTxMatrix,
                                                     int aaHint,
                                                     int fmHint);

    private static native void disposeNativeStrikePtr(long nativeStrikePtr);

    private static native StrikeMetrics getFontMetrics(long nativeStrikePtr);

    private static native void getGlyphImagePtrsNative(long nativeStrikePtr,
                                                       long[] glyphInfos,
                                                       int[] uniCodes, int len);

    private static native float getNativeGlyphAdvance(long nativeStrikePtr,
                                                      int glyphCode);

    private static native GeneralPath getNativeGlyphOutline(long nativeStrikePtr,
                                                            int glyphCode,
                                                            double x,
                                                            double y);

    private static native void getNativeGlyphImageBounds(long nativeStrikePtr,
                                                         int glyphCode,
                                                         Rectangle2D.Float result,
                                                         double x, double y);

    private final CFont nativeFont;
    private AffineTransform invDevTx;
    private final GlyphInfoCache glyphInfoCache;
    private final GlyphAdvanceCache glyphAdvanceCache;
    private long nativeStrikePtr;

    CStrike(final CFont font, final FontStrikeDesc inDesc) {
        nativeFont = font;
        desc = inDesc;
        glyphInfoCache = new GlyphInfoCache(font, desc);
        glyphAdvanceCache = new GlyphAdvanceCache();
        disposer = glyphInfoCache;

        if (inDesc.devTx != null && !inDesc.devTx.isIdentity()) {
            try {
                invDevTx = inDesc.devTx.createInverse();
            } catch (NoninvertibleTransformException ignored) {
            }
        }
    }

    public long getNativeStrikePtr() {
        if (nativeStrikePtr != 0) {
            return nativeStrikePtr;
        }

        final double[] glyphTx = new double[6];
        desc.glyphTx.getMatrix(glyphTx);

        final double[] invDevTxMatrix = new double[6];
        if (invDevTx == null) {
            invDevTxMatrix[0] = 1;
            invDevTxMatrix[3] = 1;
        } else {
            invDevTx.getMatrix(invDevTxMatrix);
        }

        final int aaHint = desc.aaHint;
        final int fmHint = desc.fmHint;

        synchronized (this) {
            if (nativeStrikePtr != 0) {
                return nativeStrikePtr;
            }
            nativeStrikePtr =
                createNativeStrikePtr(nativeFont.getNativeFontPtr(),
                                      glyphTx, invDevTxMatrix, aaHint, fmHint);
        }

        return nativeStrikePtr;
    }

    @SuppressWarnings("removal")
    protected synchronized void finalize() throws Throwable {
        if (nativeStrikePtr != 0) {
            disposeNativeStrikePtr(nativeStrikePtr);
        }
        nativeStrikePtr = 0;
    }


    @Override
    public int getNumGlyphs() {
        return nativeFont.getNumGlyphs();
    }

    @Override
    StrikeMetrics getFontMetrics() {
        if (strikeMetrics == null) {
            StrikeMetrics metrics = getFontMetrics(getNativeStrikePtr());
            if (invDevTx != null) {
                metrics.convertToUserSpace(invDevTx);
            }
            metrics.convertToUserSpace(desc.glyphTx);
            strikeMetrics = metrics;
        }
        return strikeMetrics;
    }

    @Override
    float getGlyphAdvance(final int glyphCode) {
        return getCachedNativeGlyphAdvance(glyphCode);
    }

    @Override
    float getCodePointAdvance(final int cp) {
        return getGlyphAdvance(nativeFont.getMapper().charToGlyph(cp));
    }

    @Override
    Point2D.Float getCharMetrics(final char ch) {
        return getGlyphMetrics(nativeFont.getMapper().charToGlyph(ch));
    }

    @Override
    Point2D.Float getGlyphMetrics(final int glyphCode) {
        return new Point2D.Float(getGlyphAdvance(glyphCode), 0.0f);
    }

    Rectangle2D.Float getGlyphOutlineBounds(int glyphCode) {
        GeneralPath gp = getGlyphOutline(glyphCode, 0f, 0f);
        Rectangle2D r2d = gp.getBounds2D();
        Rectangle2D.Float r2df;
        if (r2d instanceof Rectangle2D.Float) {
            r2df = (Rectangle2D.Float)r2d;
        } else {
            float x = (float)r2d.getX();
            float y = (float)r2d.getY();
            float w = (float)r2d.getWidth();
            float h = (float)r2d.getHeight();
            r2df = new Rectangle2D.Float(x, y, w, h);
        }
        return r2df;
    }

    void getGlyphImageBounds(int glyphCode, Point2D.Float pt, Rectangle result) {
        Rectangle2D.Float floatRect = new Rectangle2D.Float();

        if (invDevTx != null) {
            invDevTx.transform(pt, pt);
        }

        getGlyphImageBounds(glyphCode, pt.x, pt.y, floatRect);

        if (floatRect.width == 0 && floatRect.height == 0) {
            result.setRect(0, 0, 0, 0);
            return;
        }

        result.setRect(floatRect.x + pt.x, floatRect.y + pt.y, floatRect.width, floatRect.height);
    }

    private void getGlyphImageBounds(int glyphCode, float x, float y, Rectangle2D.Float floatRect) {
        getNativeGlyphImageBounds(getNativeStrikePtr(), glyphCode, floatRect, x, y);
    }

    GeneralPath getGlyphOutline(int glyphCode, float x, float y) {
        return getNativeGlyphOutline(getNativeStrikePtr(), glyphCode, x, y);
    }

    GeneralPath getGlyphVectorOutline(int[] glyphs, float x, float y) {
        throw new Error("not implemented yet");
    }

    long getGlyphImagePtr(int glyphCode) {
        synchronized (glyphInfoCache) {
            long ptr = glyphInfoCache.get(glyphCode);
            if (ptr != 0L) return ptr;

            long[] ptrs = new long[1];
            int[] codes = new int[1];
            codes[0] = glyphCode;

            getGlyphImagePtrs(codes, ptrs, 1);

            ptr = ptrs[0];
            glyphInfoCache.put(glyphCode, ptr);

            return ptr;
        }
    }

    void getGlyphImagePtrs(int[] glyphCodes, long[] images, int len) {
        synchronized (glyphInfoCache) {
            int missed = 0;
            for (int i = 0; i < len; i++) {
                int code = glyphCodes[i];

                final long ptr = glyphInfoCache.get(code);
                if (ptr != 0L) {
                    images[i] = ptr;
                } else {
                    images[i] = 0L;
                    missed++;
                }
            }

            if (missed == 0) {
                return; 
            }

            final int[] filteredCodes = new int[missed];
            final int[] filteredIndicies = new int[missed];

            int j = 0;
            int dupes = 0;
            for (int i = 0; i < len; i++) {
                if (images[i] != 0L) continue; 

                final int code = glyphCodes[i];

                if (glyphInfoCache.get(code) == -1L) {
                    filteredIndicies[j] = -1;
                    dupes++;
                    j++;
                    continue;
                }

                final int k = j - dupes;
                filteredCodes[k] = code;
                glyphInfoCache.put(code, -1L);
                filteredIndicies[j] = k;
                j++;
            }

            final int filteredRunLen = j - dupes;
            final long[] filteredImages = new long[filteredRunLen];

            getFilteredGlyphImagePtrs(filteredImages, filteredCodes, filteredRunLen);

            j = 0;
            for (int i = 0; i < len; i++) {
                if (images[i] != 0L && images[i] != -1L) {
                    continue; 
                }

                final int k = filteredIndicies[j];
                final int code = glyphCodes[i];
                if (k == -1L) {
                    images[i] = glyphInfoCache.get(code);
                } else {
                    final long ptr = filteredImages[k];
                    images[i] = ptr;
                    glyphInfoCache.put(code, ptr);
                }

                j++;
            }
        }
    }

    private void getFilteredGlyphImagePtrs(long[] glyphInfos,
                                           int[] uniCodes, int len)
    {
        getGlyphImagePtrsNative(getNativeStrikePtr(), glyphInfos, uniCodes, len);
    }

    private float getCachedNativeGlyphAdvance(int glyphCode) {
        synchronized(glyphAdvanceCache) {
            float advance = glyphAdvanceCache.get(glyphCode);
            if (advance != 0) {
                return advance;
            }

            advance = getNativeGlyphAdvance(getNativeStrikePtr(), glyphCode);
            glyphAdvanceCache.put(glyphCode, advance);
            return advance;
        }
    }

    private static class GlyphInfoCache extends CStrikeDisposer {
        private static final int FIRST_LAYER_SIZE = 256;
        private static final int SECOND_LAYER_SIZE = 16384; 

        private boolean disposed = false;

        private final long[] firstLayerCache;
        private SparseBitShiftingTwoLayerArray secondLayerCache;
        private HashMap<Integer, Long> generalCache;

        GlyphInfoCache(final Font2D nativeFont, final FontStrikeDesc desc) {
            super(nativeFont, desc);
            firstLayerCache = new long[FIRST_LAYER_SIZE];
        }

        public synchronized long get(final int index) {
            if (index < 0) {
                if (-index < SECOND_LAYER_SIZE) {
                    if (secondLayerCache == null) {
                        return 0L;
                    }
                    return secondLayerCache.get(-index);
                }
            } else {
                if (index < FIRST_LAYER_SIZE) {
                    return firstLayerCache[index];
                }
            }

            if (generalCache == null) {
                return 0L;
            }
            final Long value = generalCache.get(Integer.valueOf(index));
            if (value == null) {
                return 0L;
            }
            return value.longValue();
        }

        public synchronized void put(final int index, final long value) {
            if (index < 0) {
                if (-index < SECOND_LAYER_SIZE) {
                    if (secondLayerCache == null) {
                        secondLayerCache = new SparseBitShiftingTwoLayerArray(SECOND_LAYER_SIZE, 7); 
                    }
                    secondLayerCache.put(-index, value);
                    return;
                }
            } else {
                if (index < FIRST_LAYER_SIZE) {
                    firstLayerCache[index] = value;
                    return;
                }
            }

            if (generalCache == null) {
                generalCache = new HashMap<Integer, Long>();
            }

            generalCache.put(Integer.valueOf(index), Long.valueOf(value));
        }

        public synchronized void dispose() {
            if (disposed) {
                return;
            }

            super.dispose();

            disposeLongArray(firstLayerCache);

            if (secondLayerCache != null) {
                final long[][] secondLayerLongArrayArray = secondLayerCache.cache;
                for (int i = 0; i < secondLayerLongArrayArray.length; i++) {
                    final long[] longArray = secondLayerLongArrayArray[i];
                    if (longArray != null) disposeLongArray(longArray);
                }
            }

            if (generalCache != null) {
                for (long longValue : generalCache.values()) {
                    if (longValue != -1 && longValue != 0) {
                        removeGlyphInfoFromCache(longValue);
                        StrikeCache.freeLongPointer(longValue);
                    }
                }
            }

            disposed = true;
        }

        private static void disposeLongArray(final long[] longArray) {
            for (int i = 0; i < longArray.length; i++) {
                final long ptr = longArray[i];
                if (ptr != 0 && ptr != -1) {
                    removeGlyphInfoFromCache(ptr);
                    StrikeCache.freeLongPointer(ptr); 
                }
            }
        }

        private static class SparseBitShiftingTwoLayerArray {
            final long[][] cache;
            final int shift;
            final int secondLayerLength;

            SparseBitShiftingTwoLayerArray(final int size, final int shift) {
                this.shift = shift;
                this.cache = new long[1 << shift][];
                this.secondLayerLength = size >> shift;
            }

            public long get(final int index) {
                final int firstIndex = index >> shift;
                final long[] firstLayerRow = cache[firstIndex];
                if (firstLayerRow == null) return 0L;
                return firstLayerRow[index - (firstIndex * (1 << shift))];
            }

            public void put(final int index, final long value) {
                final int firstIndex = index >> shift;
                long[] firstLayerRow = cache[firstIndex];
                if (firstLayerRow == null) {
                    cache[firstIndex] = firstLayerRow = new long[secondLayerLength];
                }
                firstLayerRow[index - (firstIndex * (1 << shift))] = value;
            }
        }
    }

    private static class GlyphAdvanceCache {
        private static final int FIRST_LAYER_SIZE = 256;
        private static final int SECOND_LAYER_SIZE = 16384; 

        private final float[] firstLayerCache = new float[FIRST_LAYER_SIZE];
        private SparseBitShiftingTwoLayerArray secondLayerCache;
        private HashMap<Integer, Float> generalCache;

        public synchronized float get(final int index) {
            if (index < 0) {
                if (-index < SECOND_LAYER_SIZE) {
                    if (secondLayerCache == null) return 0;
                    return secondLayerCache.get(-index);
                }
            } else {
                if (index < FIRST_LAYER_SIZE) {
                    return firstLayerCache[index];
                }
            }

            if (generalCache == null) return 0;
            final Float value = generalCache.get(Integer.valueOf(index));
            if (value == null) return 0;
            return value.floatValue();
        }

        public synchronized void put(final int index, final float value) {
            if (index < 0) {
                if (-index < SECOND_LAYER_SIZE) {
                    if (secondLayerCache == null) {
                        secondLayerCache = new SparseBitShiftingTwoLayerArray(SECOND_LAYER_SIZE, 7); 
                    }
                    secondLayerCache.put(-index, value);
                    return;
                }
            } else {
                if (index < FIRST_LAYER_SIZE) {
                    firstLayerCache[index] = value;
                    return;
                }
            }

            if (generalCache == null) {
                generalCache = new HashMap<Integer, Float>();
            }

            generalCache.put(Integer.valueOf(index), Float.valueOf(value));
        }

        private static class SparseBitShiftingTwoLayerArray {
            final float[][] cache;
            final int shift;
            final int secondLayerLength;

            SparseBitShiftingTwoLayerArray(final int size, final int shift) {
                this.shift = shift;
                this.cache = new float[1 << shift][];
                this.secondLayerLength = size >> shift;
            }

            public float get(final int index) {
                final int firstIndex = index >> shift;
                final float[] firstLayerRow = cache[firstIndex];
                if (firstLayerRow == null) return 0L;
                return firstLayerRow[index - (firstIndex * (1 << shift))];
            }

            public void put(final int index, final float value) {
                final int firstIndex = index >> shift;
                float[] firstLayerRow = cache[firstIndex];
                if (firstLayerRow == null) {
                    cache[firstIndex] = firstLayerRow =
                        new float[secondLayerLength];
                }
                firstLayerRow[index - (firstIndex * (1 << shift))] = value;
            }
        }
    }
}