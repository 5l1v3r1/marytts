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
package de.dfki.lt.mary.unitselection;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Vector;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.sun.speech.freetts.Item;

import de.dfki.lt.mary.MaryProperties;
import de.dfki.lt.mary.htsengine.HTSModelSet;
import de.dfki.lt.mary.htsengine.HTSTreeSet;
import de.dfki.lt.mary.modules.HTSContextTranslator;
import de.dfki.lt.mary.modules.phonemiser.Phoneme;
import de.dfki.lt.mary.modules.phonemiser.PhonemeSet;
import de.dfki.lt.mary.unitselection.HalfPhoneFFRTargetCostFunction.TargetCostReporter;
import de.dfki.lt.mary.unitselection.featureprocessors.FeatureDefinition;
import de.dfki.lt.mary.unitselection.featureprocessors.FeatureVector;
import de.dfki.lt.mary.unitselection.featureprocessors.MaryGenericFeatureProcessors;
import de.dfki.lt.mary.unitselection.voiceimport.MaryHeader;
import de.dfki.lt.mary.unitselection.weightingfunctions.WeightFunc;
import de.dfki.lt.mary.unitselection.weightingfunctions.WeightFunctionManager;
import de.dfki.lt.signalproc.display.Histogram;
import de.dfki.lt.signalproc.util.DistanceComputer;

public class JoinModelCost implements JoinCostFunction
{
    protected int nCostComputations = 0;
    
    /****************/
    /* DATA FIELDS  */
    /****************/
    private JoinCostFeatures jcf = null;
    
    private HTSContextTranslator contextTranslator = new HTSContextTranslator();
    // TODO: the list of features should be read, not hard-coded
    private Vector<String> featureList = new Vector<String>(Arrays.asList(new String[] {
            "mary_phoneme",
            "mary_prev_phoneme",
            "mary_next_phoneme",
            "mary_ph_vc",
            "mary_ph_cplace",
            "mary_ph_ctype",
            "mary_ph_cvox",
            "mary_ph_vfront",
            "mary_ph_vheight",
            "mary_ph_vlng",
            "mary_ph_vrnd",
            "mary_prev_vc",
            "mary_prev_cplace",
            "mary_prev_ctype",
            "mary_prev_cvox",
            "mary_prev_vfront",
            "mary_prev_vheight",
            "mary_prev_vlng",
            "mary_prev_vrnd",
            "mary_next_vc",
            "mary_next_cplace",
            "mary_next_ctype",
            "mary_next_cvox",
            "mary_next_vfront",
            "mary_next_vheight",
            "mary_next_vlng",
            "mary_next_vrnd",
            "mary_stressed",
            "mary_pos_in_syl",
            "mary_position_type",
            "mary_pos",
            "mary_sentence_punc",
            "mary_sentence_numwords",
            "mary_words_from_sentence_start",
            "mary_words_from_sentence_end",
            "mary_word_numsyls",
            "mary_syls_from_word_start",
            "mary_syls_from_word_end",
            "mary_word_numsegs",
            "mary_segs_from_word_start",
            "mary_segs_from_word_end",
            "mary_syl_numsegs",
            "mary_segs_from_syl_start",
            "mary_segs_from_syl_end",
            "mary_syls_from_prev_stressed",
            "mary_syls_to_next_stressed",
            "mary_prev_punctuation",
            "mary_next_punctuation",
            "mary_words_from_prev_punctuation",
            "mary_words_to_next_punctuation",
            "mary_word_frequency",
            "mary_halfphone_lr"
    }));
    
    private HTSModelSet joinPdf = null;
    private HTSTreeSet joinTree = null;
    
    private FeatureDefinition featureDef = null;
    
    /****************/
    /* CONSTRUCTORS */
    /****************/

    /**
     * Empty constructor; when using this, call load() separately to 
     * initialise this class.
     * @see #load(String)
     */
    public JoinModelCost()
    {
    }
    
    /**
     * Initialise this join cost function by reading the appropriate settings
     * from the MaryProperties using the given configPrefix.
     * @param configPrefix the prefix for the (voice-specific) config entries
     * to use when looking up files to load.
     */
    public void init(String configPrefix) throws IOException
    {
        String joinFileName = MaryProperties.needFilename(configPrefix+".joinCostFile");
        String joinPdfFileName = MaryProperties.getFilename(configPrefix + ".joinPdfFile");
        String joinTreeFileName = MaryProperties.getFilename(configPrefix + ".joinTreeFile");
        load(joinFileName, joinPdfFileName, joinTreeFileName);
    }
    
    @Deprecated
    public void load(String a, String b, String c, float d)
    {
        throw new RuntimeException("Do not use load() -- use init()");
    }
    
    
   /**
     * Load weights and values from the given file
     * @param joinFileName the file from which to read join cost features
     * @param joinPdfFileName the file from which to read the Gaussian models in the leaves of the tree
     * @param joinTreeFileName the file from which to read the Tree, in HTS format.
     */
    public void load(String joinFileName, String joinPdfFileName, String joinTreeFileName)
    throws IOException
    {
        jcf = new JoinCostFeatures(joinFileName);

        assert featureDef != null : "Expected to have a feature definition, but it is null!";
        /* Load PDFs*/
        joinPdf = new HTSModelSet();
        try {
            joinPdf.loadJoinModelSet(joinPdfFileName);
        } catch (Exception e) {
            IOException ioe = new IOException("Cannot load join model pdfs from "+joinPdfFileName);
            ioe.initCause(e);
            throw ioe;
        }
        
        /* Load Trees */
        int numTrees = 1;  /* just JoinModeller will be loaded */
        joinTree = new HTSTreeSet(numTrees);
        try {
            joinTree.loadJoinModellerTree(joinTreeFileName, featureDef);
        } catch (Exception e) {
            IOException ioe = new IOException("Cannot load join model trees from "+joinTreeFileName);
            ioe.initCause(e);
            throw ioe;
        }
    }
    
    /**
     * Set the feature definition to use for interpreting target feature vectors.
     * @param def the feature definition to use.
     */
    public void setFeatureDefinition(FeatureDefinition def)
    {
        this.featureDef = def;
    }
    
    /*****************/
    /* MISC METHODS  */
    /*****************/

    /**
     * A combined cost computation, as a weighted sum
     * of the signal-based cost (computed from the units)
     * and the phonetics-based cost (computed from the targets).
     * 
     * @param t1 The left target.
     * @param u1 The left unit.
     * @param t2 The right target.
     * @param u2 The right unit.
     * 
     * @return the cost of joining the left unit with the right unit, as a non-negative value.
     */
    public double cost(Target t1, Unit u1, Target t2, Unit u2 ) {
        // Units of length 0 cannot be joined:
        if (u1.getDuration() == 0 || u2.getDuration() == 0) return Double.POSITIVE_INFINITY;
        // In the case of diphones, replace them with the relevant part:
        if (u1 instanceof DiphoneUnit) {
            u1 = ((DiphoneUnit)u1).getRight();
        }
        if (u2 instanceof DiphoneUnit) {
            u2 = ((DiphoneUnit)u2).getLeft();
        }
        
        if (u1.index+1 == u2.index) return 0;
        double cost = 1; // basic penalty for joins of non-contiguous units. 
        
        float[] v1 = jcf.getRightJCF(u1.index);
        float[] v2 = jcf.getLeftJCF(u2.index);
        //double[] diff = new double[v1.length];
        //for ( int i = 0; i < v1.length; i++ ) {
        //    diff[i] = (double)v1[i] - v2[i];
        //}
        // TODO: ignore F0 for now 
        double[] diff = new double[v1.length-1];
        for ( int i = 0; i < v1.length-1; i++ ) {
            diff[i] = (double)v1[i] - v2[i];
        }
        
        // Now evaluate likelihood of the diff under the join model
        // Compute the model name:
        assert featureDef != null : "Feature Definition was not set";
        FeatureVector fv1 = null;
        if (t1 instanceof DiphoneTarget) {
            HalfPhoneTarget hpt1 = ((DiphoneTarget)t1).getRight();
            assert hpt1 != null;
            fv1 = hpt1.getFeatureVector();
        } else {
            fv1 = t1.getFeatureVector();
        }
        assert fv1 != null : "Target has no feature vector";
        //String modelName = contextTranslator.features2context(featureDef, fv1, featureList);
        
        /* Given a context feature model name, find its join PDF mean and variance */
        /* first, find an index in the tree and then find the mean and variance that corresponds to that index in joinPdf */
        int indexPdf;
        int vectorSize = joinPdf.getJoinVsize();
        double[] mean = new double[vectorSize];
        double[] variance = new double[vectorSize];
        
        indexPdf = joinTree.searchJoinModellerTree(fv1, featureDef, joinTree.getTreeHead(0).getRoot(), false);
        
        joinPdf.findJoinPdf(indexPdf, mean, variance);

        double distance = DistanceComputer.getNormalizedEuclideanDistance(diff, mean, variance);
        cost += distance;
        
        return cost;
    }
    
    
    
    
    
    

}
