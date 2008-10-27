/**
 * Copyright 2000-2006 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */
package marytts.modules;

// DOM classes
import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.datatypes.MaryXML;
import marytts.util.dom.NameNodeFilter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.NodeIterator;


/**
 * Transforms a full MaryXML document into an MBROLA format string
 *
 * @author Marc Schr&ouml;der
 */

public class RealisedDurationsExtractor extends InternalModule
{
    public RealisedDurationsExtractor()
    {
        super("Realised durations extractor",
              MaryDataType.AUDIO,
              MaryDataType.REALISED_DURATIONS,
              null);
    }

    public MaryData process(MaryData d)
    throws Exception
    {
        Document doc = d.getDocument();
        MaryData result = new MaryData(outputType(), d.getLocale());
        StringBuffer buf = new StringBuffer();
        buf.append("#\n");
        NodeIterator ni = ((DocumentTraversal)doc).createNodeIterator(doc, NodeFilter.SHOW_ELEMENT,
                new NameNodeFilter(new String[]{MaryXML.PHONE, MaryXML.BOUNDARY}),
                false);
        Element element = null;
        float end = 0.f;
        while ((element = (Element) ni.nextNode()) != null) {
            String sampa;
            String durString;
            if (element.getTagName().equals(MaryXML.PHONE)) {
                sampa = element.getAttribute("p");
                durString = element.getAttribute("d");
            } else {
                assert element.getTagName().equals(MaryXML.BOUNDARY);
                sampa = "_";
                durString = element.getAttribute("duration");
            }
            if (durString != null && !durString.equals("")) {
                float dur = Float.valueOf(durString) * 0.001f;
                end += dur;
                buf.append(end + " 125 "+sampa+"\n");
            }
        }

        result.setPlainText(buf.toString());
        return result;
    }

}
