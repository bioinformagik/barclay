/*
* Copyright 2012-2016 Broad Institute, Inc.
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.broadinstitute.barclay.help;

import java.util.List;
import java.util.Map;

/**
 * GSON-friendly version of the argument bindings
 */
public class GSONArgument {

    String summary;
    String name;
    String synonyms;
    String type;
    String required;
    String fulltext;
    String defaultValue;
    String minValue;
    String maxValue;
    String minRecValue;
    String maxRecValue;
    String kind;
    List<Map<String, Object>> options;

    public void populate(   final String summary,
                            final String name,
                            final String synonyms,
                            final String type,
                            final String required,
                            final String fulltext,
                            final String defaultValue,
                            final String minValue,
                            final String maxValue,
                            final String minRecValue,
                            final String maxRecValue,
                            final String kind,
                            final List<Map<String, Object>> options
    ) {
        this.summary = summary;
        this.name = name;
        this.synonyms = synonyms;
        this.type = type;
        this.required = required;
        this.fulltext = fulltext;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.minRecValue = minRecValue;
        this.maxRecValue = maxRecValue;
        this.kind = kind;
        this.options = options;
    }

}
