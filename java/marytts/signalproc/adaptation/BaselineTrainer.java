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

package marytts.signalproc.adaptation;

import java.io.IOException;

import javax.sound.sampled.UnsupportedAudioFileException;

import marytts.signalproc.adaptation.BaselineFeatureCollection;
import marytts.signalproc.adaptation.codebook.WeightedCodebookTrainerParams;
import marytts.signalproc.adaptation.gmm.jointgmm.JointGMMTrainerParams;
import marytts.tools.voiceimport.BasenameList;


/**
 * 
 * @author oytun.turk
 * 
 * Baseline class for voice conversion training
 *
 */
public class BaselineTrainer {
    
    public BaselinePreprocessor preprocessor;
    public BaselineFeatureExtractor featureExtractor;
    
    public BaselineTrainer(BaselinePreprocessor pp,
                           BaselineFeatureExtractor fe) 
    {
        preprocessor = new BaselinePreprocessor(pp);
        featureExtractor = new BaselineFeatureExtractor(fe);
    }
    
    //This baseline version does nothing. Please implement functionality in derived classes.
    public boolean checkParams()
    {
        return true;
    }
    
    //This baseline version just returns identical target indices for each source entry
    //Note that the returned map contains smallest number of items in source and target training sets
    public int[] getIndexedMapping(BaselineAdaptationSet sourceTrainingSet, BaselineAdaptationSet targetTrainingSet)
    {
        int[] map = null;
        int numItems = Math.min(sourceTrainingSet.items.length, targetTrainingSet.items.length);
        if (numItems>0)
        {
            map = new int[numItems]; 
            int i;
            
            for (i=0; i<numItems; i++)
                map[i] = i;
        }
        
        return map;
    }
}
