package org.broadinstitute.barclay.help;

import com.sun.javadoc.ClassDoc;

import java.io.IOException;
import java.util.*;

public class TestDocumentationHandler extends GenericDocumentationHandler {

    @Override
    protected void addCustomBindings(final Class<?> classToProcess, final ClassDoc classDoc, final Map<String, Object> root) {
        if (root.get("testPlugin") == null) {
            root.put("testPlugin", new HashSet<HashMap<String, Object>>());
        }
    }

    @Override
    protected String getTagFilterPrefix(){ return "MyTag"; }

}
