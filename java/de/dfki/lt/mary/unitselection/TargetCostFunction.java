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

import java.io.BufferedReader;
import java.io.IOException;

import de.dfki.lt.mary.unitselection.featureprocessors.FeatureProcessorManager;
import de.dfki.lt.mary.unitselection.featureprocessors.FeatureVector;
import de.dfki.lt.mary.unitselection.weightingfunctions.WeightFunctionManager;

/**
 * A target cost function for evaluating the goodness-of-fit of 
 * a given unit for a given target. 
 * @author Marc Schr&ouml;der
 *
 */
public interface TargetCostFunction
{
    /**
     * Initialise the data needed to do a target cost computation.
     * @param featureFileName name of a file containing the unit features
     * @param weightsFile an optional weights file -- if non-null, contains
     * feature weights that override the ones present in the feature file.
     * @param featProc a feature processor manager which can provide feature processors
     * to compute the features for a target at run time
     * @throws IOException
     */
    public void load(String featureFileName, String weightsFile,
            FeatureProcessorManager featProc) throws IOException;

    /**
     * Compute the goodness-of-fit of a given unit for a given target.
     * @param target 
     * @param unit
     * @return a non-negative number; smaller values mean better fit, i.e. smaller cost.
     */
    public double cost(Target target, Unit unit);
    
    /**
     * Compute the features for a given target. A typical use case is to
     * call this method once for every target, and store the resulting
     * feature vector in the target using target.setFeatureVector().
     * @param target the target for which to compute the features
     * @return a feature vector
     * @see Target#setFeatureVector(FeatureVector)
     */
    public FeatureVector computeTargetFeatures(Target target);
}
