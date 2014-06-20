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

/* $Id: MultiByteFont.java 1564017 2014-02-03 19:29:24Z vhennebert $ */

package org.apache.fop.fonts;

import java.awt.Rectangle;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.fop.apps.io.InternalResourceResolver;
import org.apache.fop.complexscripts.fonts.GlyphDefinitionTable;
import org.apache.fop.complexscripts.fonts.GlyphPositioningTable;
import org.apache.fop.complexscripts.fonts.GlyphSubstitutionTable;
import org.apache.fop.complexscripts.fonts.Positionable;
import org.apache.fop.complexscripts.fonts.Substitutable;
import org.apache.fop.complexscripts.util.GlyphSequence;
import org.apache.fop.util.CharUtilities;

/**
 * Generic MultiByte (CID) font
 */
public class MultiByteFont extends CIDFont implements Substitutable, Positionable {

    /** logging instance */
    private static final Log log
        = LogFactory.getLog(MultiByteFont.class);

    private String ttcName;
    private String encoding = "Identity-H";

    private int defaultWidth;
    private CIDFontType cidType = CIDFontType.CIDTYPE2;

    private final CIDSet cidSet;

    /* advanced typographic support */
    private GlyphDefinitionTable gdef;
    private GlyphSubstitutionTable gsub;
    private GlyphPositioningTable gpos;

    /* dynamic private use (character) mappings */
    private int numMapped;
    private int numUnmapped;
    private int nextPrivateUse = 0xE000;
    private int firstPrivate;
    private int lastPrivate;
    private int firstUnmapped;
    private int lastUnmapped;

    /** Contains the character bounding boxes for all characters in the font */
    protected Rectangle[] boundingBoxes;

    private boolean isOTFFile = false;

    // since for most users the most likely glyphs are in the first cmap segments we store their mapping.
    private static final int NUM_MOST_LIKELY_GLYPHS = 256;
    private int[] mostLikelyGlyphs = new int[NUM_MOST_LIKELY_GLYPHS];

    //A map to store each used glyph from the CID set against the glyph name.
    private LinkedHashMap<Integer, String> usedGlyphNames = new LinkedHashMap<Integer, String>();

    /**
     * Default constructor
     */
    public MultiByteFont(InternalResourceResolver resourceResolver, EmbeddingMode embeddingMode) {
        super(resourceResolver);
        setFontType(FontType.TYPE0);
        setEmbeddingMode(embeddingMode);
        if (embeddingMode != EmbeddingMode.FULL) {
            cidSet = new CIDSubset(this);
        } else {
            cidSet = new CIDFull(this);
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getDefaultWidth() {
        return defaultWidth;
    }

    /** {@inheritDoc} */
    @Override
    public String getRegistry() {
        return "Adobe";
    }

    /** {@inheritDoc} */
    @Override
    public String getOrdering() {
        return "UCS";
    }

    /** {@inheritDoc} */
    @Override
    public int getSupplement() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public CIDFontType getCIDType() {
        return cidType;
    }

    public void setIsOTFFile(boolean isOTFFile) {
        this.isOTFFile = isOTFFile;
    }

    public boolean isOTFFile() {
        return this.isOTFFile;
    }

    /**
     * Sets the CIDType.
     * @param cidType The cidType to set
     */
    public void setCIDType(CIDFontType cidType) {
        this.cidType = cidType;
    }

    /** {@inheritDoc} */
    @Override
    public String getEmbedFontName() {
        if (isEmbeddable()) {
            return FontUtil.stripWhiteSpace(super.getFontName());
        } else {
            return super.getFontName();
        }
    }

    /** {@inheritDoc} */
    public boolean isEmbeddable() {
        return !(getEmbedFileURI() == null && getEmbedResourceName() == null);
    }

    public boolean isSubsetEmbedded() {
        if (getEmbeddingMode() == EmbeddingMode.FULL) {
            return false;
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public CIDSet getCIDSet() {
        return this.cidSet;
    }

    public void mapUsedGlyphName(int gid, String value) {
        usedGlyphNames.put(gid, value);
    }

    public LinkedHashMap<Integer, String> getUsedGlyphNames() {
        return usedGlyphNames;
    }

    /** {@inheritDoc} */
    @Override
    public String getEncodingName() {
        return encoding;
    }

    /** {@inheritDoc} */
    public int getWidth(int i, int size) {
        if (isEmbeddable()) {
            int glyphIndex = cidSet.getOriginalGlyphIndex(i);
            return size * width[glyphIndex];
        } else {
            return size * width[i];
        }
    }

    /** {@inheritDoc} */
    public int[] getWidths() {
        int[] arr = new int[width.length];
        System.arraycopy(width, 0, arr, 0, width.length);
        return arr;
    }

    public Rectangle getBoundingBox(int glyphIndex, int size) {
        int index = isEmbeddable() ? cidSet.getOriginalGlyphIndex(glyphIndex) : glyphIndex;
        Rectangle bbox = boundingBoxes[index];
        return new Rectangle(bbox.x * size, bbox.y * size, bbox.width * size, bbox.height * size);
    }

    /**
     * Returns the glyph index for a Unicode character. The method returns 0 if there's no
     * such glyph in the character map.
     * @param c the Unicode character index
     * @return the glyph index (or 0 if the glyph is not available)
     */
    // [TBD] - needs optimization, i.e., change from linear search to binary search
    public int findGlyphIndex(int c) {
        int idx = c;
        int retIdx = SingleByteEncoding.NOT_FOUND_CODE_POINT;

        // for most users the most likely glyphs are in the first cmap segments (meaning the one with
        // the lowest unicode start values)
        if (idx < NUM_MOST_LIKELY_GLYPHS && mostLikelyGlyphs[idx] != 0) {
            return mostLikelyGlyphs[idx];
        }
        for (int i = 0; (i < cmap.length) && retIdx == 0; i++) {
            if (cmap[i].getUnicodeStart() <= idx
                    && cmap[i].getUnicodeEnd() >= idx) {

                retIdx = cmap[i].getGlyphStartIndex()
                    + idx
                    - cmap[i].getUnicodeStart();
                if (idx < NUM_MOST_LIKELY_GLYPHS) {
                    mostLikelyGlyphs[idx] = retIdx;
                }
            }
        }
        return retIdx;
    }

    /**
     * Add a private use mapping {PU,GI} to the existing character map.
     * N.B. Does not insert in order, merely appends to end of existing map.
     */
    private synchronized void addPrivateUseMapping(int pu, int gi) {
        assert findGlyphIndex(pu) == SingleByteEncoding.NOT_FOUND_CODE_POINT;
        CMapSegment[] oldCmap = cmap;
        int cmapLength = oldCmap.length;
        CMapSegment[] newCmap = new CMapSegment [ cmapLength + 1 ];
        System.arraycopy(oldCmap, 0, newCmap, 0, cmapLength);
        newCmap [ cmapLength ] = new CMapSegment(pu, pu, gi);
        cmap = newCmap;
    }

    /**
     * Given a glyph index, create a new private use mapping, augmenting the bfentries
     * table. This is needed to accommodate the presence of an (output) glyph index in a
     * complex script glyph substitution that does not correspond to a character in the
     * font's CMAP.  The creation of such private use mappings is deferred until an
     * attempt is actually made to perform the reverse lookup from the glyph index. This
     * is necessary in order to avoid exhausting the private use space on fonts containing
     * many such non-mapped glyph indices, if these mappings had been created statically
     * at font load time.
     * @param gi glyph index
     * @returns unicode scalar value
     */
    private int createPrivateUseMapping(int gi) {
        while ((nextPrivateUse < 0xF900)
                && (findGlyphIndex(nextPrivateUse) != SingleByteEncoding.NOT_FOUND_CODE_POINT)) {
            nextPrivateUse++;
        }
        if (nextPrivateUse < 0xF900) {
            int pu = nextPrivateUse;
            addPrivateUseMapping(pu, gi);
            if (firstPrivate == 0) {
                firstPrivate = pu;
            }
            lastPrivate = pu;
            numMapped++;
            if (log.isDebugEnabled()) {
                log.debug("Create private use mapping from "
                            + CharUtilities.format(pu)
                            + " to glyph index " + gi
                            + " in font '" + getFullName() + "'");
            }
            return pu;
        } else {
            if (firstUnmapped == 0) {
                firstUnmapped = gi;
            }
            lastUnmapped = gi;
            numUnmapped++;
            log.warn("Exhausted private use area: unable to map "
                       + numUnmapped + " glyphs in glyph index range ["
                       + firstUnmapped + "," + lastUnmapped
                       + "] (inclusive) of font '" + getFullName() + "'");
            return 0;
        }
    }

    /**
     * Returns the Unicode scalar value that corresponds to the glyph index. If more than
     * one correspondence exists, then the first one is returned (ordered by bfentries[]).
     * @param gi glyph index
     * @returns unicode scalar value
     */
    // [TBD] - needs optimization, i.e., change from linear search to binary search
    private int findCharacterFromGlyphIndex(int gi, boolean augment) {
        int cc = 0;
        for (int i = 0, n = cmap.length; i < n; i++) {
            CMapSegment segment = cmap [ i ];
            int s = segment.getGlyphStartIndex();
            int e = s + (segment.getUnicodeEnd() - segment.getUnicodeStart());
            if ((gi >= s) && (gi <= e)) {
                cc = segment.getUnicodeStart() + (gi - s);
                break;
            }
        }
        if ((cc == 0) && augment) {
            cc = createPrivateUseMapping(gi);
        }
        return cc;
    }

    private int findCharacterFromGlyphIndex(int gi) {
        return findCharacterFromGlyphIndex(gi, true);
    }

    protected BitSet getGlyphIndices() {
        BitSet bitset = new BitSet();
        bitset.set(0);
        bitset.set(1);
        bitset.set(2);
        for (int i = 0; i < cmap.length; i++) {
            int start = cmap[i].getUnicodeStart();
            int end = cmap[i].getUnicodeEnd();
            int glyphIndex = cmap[i].getGlyphStartIndex();
            while (start++ < end + 1) {
                bitset.set(glyphIndex++);
            }
        }
        return bitset;
    }

    protected char[] getChars() {
        // the width array is set when the font is built
        char[] chars = new char[width.length];
        for (int i = 0; i < cmap.length; i++) {
            int start = cmap[i].getUnicodeStart();
            int end = cmap[i].getUnicodeEnd();
            int glyphIndex = cmap[i].getGlyphStartIndex();
            while (start < end + 1) {
                chars[glyphIndex++] = (char) start++;
            }
        }
        return chars;
    }

    /** {@inheritDoc} */
    @Override
    public char mapChar(char c) {
        notifyMapOperation();
        int glyphIndex = findGlyphIndex(c);
        if (glyphIndex == SingleByteEncoding.NOT_FOUND_CODE_POINT) {
            warnMissingGlyph(c);
            if (!isOTFFile) {
                glyphIndex = findGlyphIndex(Typeface.NOT_FOUND);
            }
        }
        if (isEmbeddable()) {
            glyphIndex = cidSet.mapChar(glyphIndex, c);
        }
        return (char) glyphIndex;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasChar(char c) {
        return (findGlyphIndex(c) != SingleByteEncoding.NOT_FOUND_CODE_POINT);
    }

    /**
     * Sets the defaultWidth.
     * @param defaultWidth The defaultWidth to set
     */
    public void setDefaultWidth(int defaultWidth) {
        this.defaultWidth = defaultWidth;
    }

    /**
     * Returns the TrueType Collection Name.
     * @return the TrueType Collection Name
     */
    public String getTTCName() {
        return ttcName;
    }

    /**
     * Sets the the TrueType Collection Name.
     * @param ttcName the TrueType Collection Name
     */
    public void setTTCName(String ttcName) {
        this.ttcName = ttcName;
    }

    /**
     * Sets the width array.
     * @param wds array of widths.
     */
    public void setWidthArray(int[] wds) {
        this.width = wds;
    }

    /**
     * Sets the bounding boxes array.
     * @param boundingBoxes array of bounding boxes.
     */
    public void setBBoxArray(Rectangle[] boundingBoxes) {
        this.boundingBoxes = boundingBoxes;
    }

    /**
     * Returns a Map of used Glyphs.
     * @return Map Map of used Glyphs
     */
    public Map<Integer, Integer> getUsedGlyphs() {
        return cidSet.getGlyphs();
    }

    /**
     * Establishes the glyph definition table.
     * @param gdef the glyph definition table to be used by this font
     */
    public void setGDEF(GlyphDefinitionTable gdef) {
        if ((this.gdef == null) || (gdef == null)) {
            this.gdef = gdef;
        } else {
            throw new IllegalStateException("font already associated with GDEF table");
        }
    }

    /**
     * Obtain glyph definition table.
     * @return glyph definition table or null if none is associated with font
     */
    public GlyphDefinitionTable getGDEF() {
        return gdef;
    }

    /**
     * Establishes the glyph substitution table.
     * @param gsub the glyph substitution table to be used by this font
     */
    public void setGSUB(GlyphSubstitutionTable gsub) {
        if ((this.gsub == null) || (gsub == null)) {
            this.gsub = gsub;
        } else {
            throw new IllegalStateException("font already associated with GSUB table");
        }
    }

    /**
     * Obtain glyph substitution table.
     * @return glyph substitution table or null if none is associated with font
     */
    public GlyphSubstitutionTable getGSUB() {
        return gsub;
    }

    /**
     * Establishes the glyph positioning table.
     * @param gpos the glyph positioning table to be used by this font
     */
    public void setGPOS(GlyphPositioningTable gpos) {
        if ((this.gpos == null) || (gpos == null)) {
            this.gpos = gpos;
        } else {
            throw new IllegalStateException("font already associated with GPOS table");
        }
    }

    /**
     * Obtain glyph positioning table.
     * @return glyph positioning table or null if none is associated with font
     */
    public GlyphPositioningTable getGPOS() {
        return gpos;
    }

    /** {@inheritDoc} */
    public boolean performsSubstitution() {
        return gsub != null;
    }

    /** {@inheritDoc} */
    public CharSequence performSubstitution(CharSequence cs, String script, String language) {
        if (gsub != null) {
            GlyphSequence igs = mapCharsToGlyphs(cs);
            GlyphSequence ogs = gsub.substitute(igs, script, language);
            CharSequence ocs = mapGlyphsToChars(ogs);
            return ocs;
        } else {
            return cs;
        }
    }

    /** {@inheritDoc} */
    public CharSequence reorderCombiningMarks(
        CharSequence cs, int[][] gpa, String script, String language) {
        if (gdef != null) {
            GlyphSequence igs = mapCharsToGlyphs(cs);
            GlyphSequence ogs = gdef.reorderCombiningMarks(igs, gpa, script, language);
            CharSequence ocs = mapGlyphsToChars(ogs);
            return ocs;
        } else {
            return cs;
        }
    }

    /** {@inheritDoc} */
    public boolean performsPositioning() {
        return gpos != null;
    }

    /** {@inheritDoc} */
    public int[][]
        performPositioning(CharSequence cs, String script, String language, int fontSize) {
        if (gpos != null) {
            GlyphSequence gs = mapCharsToGlyphs(cs);
            int[][] adjustments = new int [ gs.getGlyphCount() ] [ 4 ];
            if (gpos.position(gs, script, language, fontSize, this.width, adjustments)) {
                return scaleAdjustments(adjustments, fontSize);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    public int[][] performPositioning(CharSequence cs, String script, String language) {
        throw new UnsupportedOperationException();
    }


    private int[][] scaleAdjustments(int[][] adjustments, int fontSize) {
        if (adjustments != null) {
            for (int i = 0, n = adjustments.length; i < n; i++) {
                int[] gpa = adjustments [ i ];
                for (int k = 0; k < 4; k++) {
                    gpa [ k ] = (gpa [ k ] * fontSize) / 1000;
                }
            }
            return adjustments;
        } else {
            return null;
        }
    }

    /**
     * Map sequence CS, comprising a sequence of UTF-16 encoded Unicode Code Points, to
     * an output character sequence GS, comprising a sequence of Glyph Indices. N.B. Unlike
     * mapChar(), this method does not make use of embedded subset encodings.
     * @param cs a CharSequence containing UTF-16 encoded Unicode characters
     * @returns a CharSequence containing glyph indices
     */
    private GlyphSequence mapCharsToGlyphs(CharSequence cs) {
        IntBuffer cb = IntBuffer.allocate(cs.length());
        IntBuffer gb = IntBuffer.allocate(cs.length());
        int gi;
        int giMissing = findGlyphIndex(Typeface.NOT_FOUND);
        for (int i = 0, n = cs.length(); i < n; i++) {
            int cc = cs.charAt(i);
            if ((cc >= 0xD800) && (cc < 0xDC00)) {
                if ((i + 1) < n) {
                    int sh = cc;
                    int sl = cs.charAt(++i);
                    if ((sl >= 0xDC00) && (sl < 0xE000)) {
                        cc = 0x10000 + ((sh - 0xD800) << 10) + ((sl - 0xDC00) << 0);
                    } else {
                        throw new IllegalArgumentException(
                            "ill-formed UTF-16 sequence, "
                               + "contains isolated high surrogate at index " + i);
                    }
                } else {
                    throw new IllegalArgumentException(
                        "ill-formed UTF-16 sequence, "
                          + "contains isolated high surrogate at end of sequence");
                }
            } else if ((cc >= 0xDC00) && (cc < 0xE000)) {
                throw new IllegalArgumentException(
                    "ill-formed UTF-16 sequence, "
                      + "contains isolated low surrogate at index " + i);
            }
            notifyMapOperation();
            gi = findGlyphIndex(cc);
            if (gi == SingleByteEncoding.NOT_FOUND_CODE_POINT) {
                warnMissingGlyph((char) cc);
                gi = giMissing;
            }
            cb.put(cc);
            gb.put(gi);
        }
        cb.flip();
        gb.flip();
        return new GlyphSequence(cb, gb, null);
    }

    /**
     * Map sequence GS, comprising a sequence of Glyph Indices, to output sequence CS,
     * comprising a sequence of UTF-16 encoded Unicode Code Points.
     * @param gs a GlyphSequence containing glyph indices
     * @returns a CharSequence containing UTF-16 encoded Unicode characters
     */
    private CharSequence mapGlyphsToChars(GlyphSequence gs) {
        int ng = gs.getGlyphCount();
        CharBuffer cb = CharBuffer.allocate(ng);
        int ccMissing = Typeface.NOT_FOUND;
        for (int i = 0, n = ng; i < n; i++) {
            int gi = gs.getGlyph(i);
            int cc = findCharacterFromGlyphIndex(gi);
            if ((cc == 0) || (cc > 0x10FFFF)) {
                cc = ccMissing;
                log.warn("Unable to map glyph index " + gi
                         + " to Unicode scalar in font '"
                         + getFullName() + "', substituting missing character '"
                         + (char) cc + "'");
            }
            if (cc > 0x00FFFF) {
                int sh;
                int sl;
                cc -= 0x10000;
                sh = ((cc >> 10) & 0x3FF) + 0xD800;
                sl = ((cc >>  0) & 0x3FF) + 0xDC00;
                cb.put((char) sh);
                cb.put((char) sl);
            } else {
                cb.put((char) cc);
            }
        }
        cb.flip();
        return cb;
    }

}

