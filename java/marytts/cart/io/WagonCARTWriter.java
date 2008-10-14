/**
 * Copyright 2006 DFKI GmbH.
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
package marytts.cart.io;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import marytts.cart.CART;
import marytts.cart.Node;
import marytts.cart.LeafNode.*;
import marytts.cart.DecisionNode;
import marytts.features.FeatureDefinition;
import marytts.features.FeatureVector;
import marytts.tools.voiceimport.MaryHeader;

/**
 * IO functions for CARTs in WagonCART format
 * 
 * @author Anna Hunecke, Marc Schröder, Marcela Charfuelan
 */
public class WagonCARTWriter {

    /**
     * Dump the CARTs in the cart map
     * to destinationDir/CARTS.bin
     * 
     * @param cart tree
     * @param destDir the destination directory
     */
    public void dumpWagonCART(CART cart, String destFile)
    throws IOException
    {
        System.out.println("Dumping CART to "+ destFile +" ...");
        
        //Open the destination file (cart.bin) and output the header
        DataOutputStream out = new DataOutputStream(new
                BufferedOutputStream(new 
                FileOutputStream(destFile)));
        //create new CART-header and write it to output file
        MaryHeader hdr = new MaryHeader(MaryHeader.CARTS);
        hdr.writeTo(out);

        //write number of nodes
        out.writeInt(cart.getNumNodes());
        String name = "";
        //dump name and CART
        out.writeUTF(name);
        //dump CART
        dumpBinary(cart, out);
      
        //finish
        out.close();
        System.out.println(" ... done\n");
    } 
    
    
    /**
     * Debug output to a text file
     * 
     * @param cart the cart tree
     * @param pw   the print writer of the text file
     * @throws IOException
     */
    public void toTextOut(CART cart, PrintWriter pw) throws IOException {
        try {
            toWagonFormat(cart.getRootNode(), null, "", pw);
            pw.flush();
            pw.close();
        } catch (IOException ioe) {
            IOException newIOE = new IOException(
                    "Error dumping CART to standard output");
            newIOE.initCause(ioe);
            throw newIOE;
        }
    }
    
    /**
     * Dumps this CART to the output stream in WagonFormat.
     * 
     * @param os   the output stream
     * @param cart the cart tree
     * 
     * @throws IOException
     *             if an error occurs during output
     */
    public void dumpBinary(CART cart, DataOutput os) throws IOException {
        try {
            toWagonFormat(cart.getRootNode(), (DataOutputStream) os, null, null);
        } catch (IOException ioe) {
            IOException newIOE = new IOException(
                    "Error dumping CART to output stream");
            newIOE.initCause(ioe);
            throw newIOE;
        }
    }
    
    private void toWagonFormat(Node node, DataOutputStream out, String extension,
            PrintWriter pw) throws IOException {
        
        if( node instanceof DecisionNode )
            toWagonFormat(((DecisionNode) node), out, extension, pw);
        
        else if( node instanceof FeatureVectorLeafNode ) 
            toWagonFormat(((FeatureVectorLeafNode) node), out, extension, pw);
        
        else if( node instanceof FloatLeafNode ) 
            toWagonFormat(((FloatLeafNode) node), out, extension, pw);
        
        else if( node instanceof IntAndFloatArrayLeafNode ) 
            toWagonFormat(((IntAndFloatArrayLeafNode) node), out, extension, pw);
        
        else if( node instanceof IntArrayLeafNode ) 
            toWagonFormat(((IntArrayLeafNode) node), out, extension, pw);
        
        else if( node instanceof StringAndFloatLeafNode ) 
            toWagonFormat(((StringAndFloatLeafNode) node), out, extension, pw);  
        
    }
    
    
    /**
     * Writes the Cart to the given DataOut in Wagon Format
     * 
     * @param out
     *            the outputStream
     * @param extension
     *            the extension that is added to the last daughter
     */
    private void toWagonFormat(DecisionNode node, DataOutputStream out, String extension,
            PrintWriter pw) throws IOException {
        if (out != null) {
            // dump to output stream
            // two open brackets + definition of node
            writeStringToOutput("((" + node.getNodeDefinition() + ")", out);
        } else {
            // dump to Standard out
            // two open brackets + definition of node
            // System.out.println("(("+getNodeDefinition());
        }
        if (pw != null) {
            // dump to print writer
            // two open brackets + definition of node
            pw.println("((" + node.getNodeDefinition() + ")");
        }
        // add the daughters
        for (int i = 0; i < node.getNumberOfDaugthers(); i++) {

            if (node.getDaughter(i) == null) {
                String nullDaughter = "";

                if (i + 1 != node.getNumberOfDaugthers() ) {
                    nullDaughter = "((() 0))";

                } else {
                    // extension must be added to last daughter
                    if (extension != null) {
                        nullDaughter = "((() 0)))" + extension;

                    } else {
                        // we are in the root node, add a closing bracket
                        nullDaughter = "((() 0)))";
                    }
                }

                if (out != null) {
                    // dump to output stream
                    writeStringToOutput(nullDaughter, out);
                } else {
                    // dump to Standard out
                    // System.out.println(nullDaughter);
                }
                if (pw != null) {
                    pw.println(" " + nullDaughter);
                }
            } else {
                if (i + 1 != node.getNumberOfDaugthers()) {

                    toWagonFormat(node.getDaughter(i), out, "",pw);
                    //daughters[i].toWagonFormat(out, "", pw);
                } else {

                    // extension must be added to last daughter
                    if (extension != null) {
                        toWagonFormat(node.getDaughter(i), out, ")" + extension, pw);
                        //daughters[i].toWagonFormat(out, ")" + extension, pw);
                    } else {
                        // we are in the root node, add a closing bracket
                        toWagonFormat(node.getDaughter(i), out, ")", pw);
                        //daughters[i].toWagonFormat(out, ")", pw);
                    }
                }
            }
        }
    }
    
    
    
    
    /**
     * Writes the Cart to the given DataOut in Wagon Format
     * 
     * @param out
     *            the outputStream
     * @param extension
     *            the extension that is added to the last daughter
     */
    private void toWagonFormat(FeatureVectorLeafNode node, DataOutputStream out, String extension,
            PrintWriter pw) throws IOException {
        StringBuffer sb = new StringBuffer();
        FeatureVector fv[] = node.getFeatureVectors(); 
        
        // open three brackets
        sb.append("(((");
        //make sure that we have a feature vector array
        //this is done when calling getFeatureVectors(). 
        //if (growable && 
        //         (featureVectors == null
        //                 || featureVectors.length == 0)){
        //    featureVectors = (FeatureVector[])
        //        featureVectorList.toArray(
        //            new FeatureVector[featureVectorList.size()]);
        //}
        // for each index, write the index and then a pseudo float
        for (int i = 0; i < fv.length; i++) {
            sb.append("(" + fv[i].getUnitIndex() + " 0)");
            if (i + 1 != fv.length) {
                sb.append(" ");
            }
        }
        // write the ending
        sb.append(") 0))" + extension);
        // dump the whole stuff
        if (out != null) {
            // write to output stream

            writeStringToOutput(sb.toString(), out);
        } else {
            // write to Standard out
            // System.out.println(sb.toString());
        }
        if (pw != null) {
            // dump to printwriter
            pw.println(sb.toString());
        }
    }
    
    
    /**
     * Writes the Cart to the given DataOut in Wagon Format
     * 
     * @param out
     *            the outputStream
     * @param extension
     *            the extension that is added to the last daughter
     */
    private void toWagonFormat(FloatLeafNode node, DataOutputStream out, String extension,
            PrintWriter pw) throws IOException {
        String s = "(("
            + node.getStDeviation() // stddev
            + " "
            + node.getMean() // mean
            + "))";
        // dump the whole stuff
        if (out != null) {
            // write to output stream

            writeStringToOutput(s, out);
        } else {
            // write to Standard out
            // System.out.println(sb.toString());
        }
        if (pw != null) {
            // dump to printwriter
            pw.print(s);
        }
    }
    
    
    /**
     * Writes the Cart to the given DataOut in Wagon Format
     * 
     * @param out
     *            the outputStream
     * @param extension
     *            the extension that is added to the last daughter
     */
    private void toWagonFormat(IntAndFloatArrayLeafNode node, DataOutputStream out, String extension,
            PrintWriter pw) throws IOException {
        StringBuffer sb = new StringBuffer();
        int data[] = node.getIntData();
        float floats[] = node.getFloatData();
        
        // open three brackets
        sb.append("(((");
        // for each index, write the index and then its float
        for (int i = 0; i < data.length; i++) {
            sb.append("(" + data[i] + " "+floats[i]+")");
            if (i + 1 != data.length) {
                sb.append(" ");
            }
        }
        // write the ending
        sb.append(") 0))" + extension);
        // dump the whole stuff
        if (out != null) {
            // write to output stream

            writeStringToOutput(sb.toString(), out);
        } else {
            // write to Standard out
            // System.out.println(sb.toString());
        }
        if (pw != null) {
            // dump to printwriter
            // TODO: change print to println
            pw.print(sb.toString());
        }
    }  
    
    /**
     * Writes the Cart to the given DataOut in Wagon Format
     * 
     * @param out
     *            the outputStream
     * @param extension
     *            the extension that is added to the last daughter
     */
    private void toWagonFormat(IntArrayLeafNode node, DataOutputStream out, String extension,
            PrintWriter pw) throws IOException {
        StringBuffer sb = new StringBuffer();
        int data[] = node.getIntData();
        
        // open three brackets
        sb.append("(((");
        // for each index, write the index and then a pseudo float
        for (int i = 0; i < data.length; i++) {
            sb.append("(" + data[i] + " 0)");
            if (i + 1 != data.length) {
                sb.append(" ");
            }
        }
        // write the ending
        sb.append(") 0))" + extension);
        // dump the whole stuff
        if (out != null) {
            // write to output stream

            writeStringToOutput(sb.toString(), out);
        } else {
            // write to Standard out
            // System.out.println(sb.toString());
        }
        if (pw != null) {
            // dump to printwriter
            pw.print(sb.toString());
        }
    }
       
    /**
     * Writes the Cart to the given DataOut in Wagon Format
     * 
     * @param out
     *            the outputStream
     * @param extension
     *            the extension that is added to the last daughter
     */
    private void toWagonFormat(StringAndFloatLeafNode node, DataOutputStream out, String extension,
            PrintWriter pw) throws IOException {
        StringBuffer sb = new StringBuffer();
        int data[] = node.getIntData();
        float floats[] = node.getFloatData();
        FeatureDefinition fd = node.getFeatureDefinition();
        int tf = node.getTargetfeature();
        
        // open three brackets
        sb.append("(((");
        // for each index, write the index and then its float
        for (int i = 0; i < data.length; i++) {
            sb.append("(" + fd.getFeatureValueAsString(tf, data[i]) + " "+floats[i]+")");
            if (i + 1 != data.length) {
                sb.append(" ");
            }
        }
        // write the ending
        sb.append(") 0))" + extension);
        // dump the whole stuff
        if (out != null) {
            // write to output stream

            writeStringToOutput(sb.toString(), out);
        } else {
            // write to Standard out
            // System.out.println(sb.toString());
        }
        if (pw != null) {
            // dump to printwriter
            // TODO: change print to println
            pw.print(sb.toString());
        }
    }
    
    /**
     * Write the given String to the given data output (Replacement for
     * writeUTF)
     * 
     * @param str
     *            the String
     * @param out
     *            the data output
     */
    private static void writeStringToOutput(String str, DataOutput out)
            throws IOException {
        out.writeInt(str.length());
        out.writeChars(str);
    }
    
}
