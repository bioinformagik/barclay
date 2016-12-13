package org.broadinstitute.barclay.help;

import com.sun.javadoc.RootDoc;

//import org.broadinstitute.gatk.engine.CommandLineGATK;
//import org.broadinstitute.gatk.tools.walkers.qc.DocumentationTest;
//import org.broadinstitute.gatk.utils.exceptions.UserException;
import org.broadinstitute.barclay.help.DocumentedFeatureHandler;
import org.broadinstitute.barclay.help.HelpDoclet;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For testing of helpdocumentation.
 */
public class TestDoclet extends HelpDoclet {

    @Override
    protected DocumentedFeatureHandler createDocumentedFeatureHandler() {
        return new TestDocumentationHandler();
    }

    public static boolean start(RootDoc rootDoc) {
        try {
            return new TestDoclet().startProcessDocs(rootDoc);
        } catch (IOException e) {
            throw new DocException("Exception processing javadoc", e);
        }
    }

    /**
     * Trivial helper routine that returns the map of name and summary given the documentedFeatureObject
     * AND adds a super-category so that we can custom-order the categories in the index
     *
     * @param annotation
     * @return
     */
    @Override
    protected final Map<String, String> getGroupMap(DocumentedFeatureObject annotation) {
        Map<String, String> root = super.getGroupMap(annotation);

        /**
         * Add-on super-category definitions. The assignments depend on parsing the names
         * defined in HelpConstants.java so be careful of changing anything.
         * Also, the super-category value strings need to be the same as used in the
         * Freemarker template. This is all fairly clunky but the best I could do without
         * making major changes to the DocumentedFeatureObject. Doesn't help that
         * Freemarker makes any scripting horribly awkward.
         */
        final String supercatValue;
        if (annotation.groupName().endsWith(" Tools")) supercatValue = "tools";
        else if (annotation.groupName().endsWith(" Utilities")) supercatValue = "utilities";
        else if (annotation.groupName().startsWith("Engine ")) supercatValue = "engine";
        else if (annotation.groupName().endsWith(" (Exclude)")) supercatValue = "exclude";
        else supercatValue = "other";

        root.put("supercat", supercatValue);
        return root;
    }

}
