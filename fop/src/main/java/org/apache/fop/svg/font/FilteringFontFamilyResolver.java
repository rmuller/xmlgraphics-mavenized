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

/* $Id: FilteringFontFamilyResolver.java 1570362 2014-02-20 21:45:20Z vhennebert $ */

package org.apache.fop.svg.font;

import java.io.InputStream;

import org.apache.batik.bridge.FontFace;
import org.apache.batik.gvt.font.GVTFontFamily;


public class FilteringFontFamilyResolver implements FOPFontFamilyResolver {

    private final FOPFontFamilyResolver delegate;

    public FilteringFontFamilyResolver(FOPFontFamilyResolver delegate) {
        this.delegate = delegate;
    }

    public FOPGVTFontFamily resolve(String familyName) {
        return delegate.resolve(familyName);
    }

    public GVTFontFamily resolve(String familyName, FontFace fontFace) {
        return delegate.resolve(familyName, fontFace);
    }

    public GVTFontFamily loadFont(InputStream in, FontFace fontFace) throws Exception {
        return delegate.loadFont(in, fontFace);
    }

    public FOPGVTFontFamily getDefault() {
        return delegate.getDefault();
    }

    public FOPGVTFontFamily getFamilyThatCanDisplay(char c) {
        return delegate.getFamilyThatCanDisplay(c);
    }

}
