/**
 * Copyright 2007 DFKI GmbH.
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

package de.dfki.lt.mary.unitselection.cart;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.StringTokenizer;

import de.dfki.lt.mary.unitselection.featureprocessors.FeatureDefinition;

/**
 * @author marc
 *
 */
public class FloatCART extends WagonCART {

    public FloatCART()
    {
        super();
    }
    
    /**
     * @param reader
     * @param featDefinition
     * @throws IOException
     */
    public FloatCART(BufferedReader reader, FeatureDefinition featDefinition)
            throws IOException {
        super(reader, featDefinition);
        // TODO Auto-generated constructor stub
    }

    /**
     * For a line representing a leaf in Wagon format, create a leaf.
     * This method decides which implementation of LeafNode is used, i.e.
     * which data format is appropriate.
     * This implementation creates an FloatArrayLeafNode, representing the leaf
     * as an array of floats.
     * Lines are of the form
     * ((<floatValue> <floatQuality>))
     * 
     * @param line a line from a wagon cart file, representing a leaf
     * @return a leaf node representing the line.
     */
    protected LeafNode createLeafNode(String line) {
        StringTokenizer tok = new StringTokenizer(line, " ");
        // read the indices from the tokenized String
        int numTokens = tok.countTokens();
        int index = 0;
        // The data to be saved in the leaf node:
        float[] data;
        if (numTokens == 2) { // we have exactly one value
            String nextToken = tok.nextToken();
            nextToken = nextToken.substring(2);
            data = new float[1];
            try {
                data[0] = Float.parseFloat(nextToken);
            } catch (NumberFormatException nfe) {
                data[0] = 0;
            }
        } else { // more than one value -- untested
            data = new float[(numTokens - 1) / 2];

            while (index * 2 < numTokens - 1) { // while we are not at the
                                                // last token
                String nextToken = tok.nextToken();
                if (index == 0) {
                    // we are at first token, discard all open brackets
                    nextToken = nextToken.substring(4);
                } else {
                    // we are not at first token, only one open bracket
                    nextToken = nextToken.substring(1);
                }
                // store the index of the unit
                data[index] = Float.parseFloat(nextToken);
                // discard next token
                tok.nextToken();
                // increase index
                index++;
            }
        }
        return new LeafNode.FloatArrayLeafNode(data);
    }

}
