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

package marytts.machinelearning;

import java.io.FileNotFoundException;
import java.io.IOException;

import marytts.util.io.MaryRandomAccessFile;
import marytts.util.math.MathUtils;


/**
 * @author oytun.turk
 *
 * Gaussian mixture model
 * 
 */
public class GMM {
    public double[] weights;
    public GaussianComponent[] components;
    public String info;

    public int featureDimension;
    public int totalComponents;
    public boolean isDiagonalCovariance;
    
    public GMM()
    {
        this(0, 0);
    }
    
    public GMM(int featureDimensionIn, int totalMixturesIn)
    {
        init(featureDimensionIn, totalMixturesIn, true);
    }
    
    public GMM(int featureDimensionIn, int totalComponentsIn, boolean isDiagonalCovarIn)
    {
        init(featureDimensionIn, totalComponentsIn, isDiagonalCovarIn);
    }

    public GMM(KMeansClusteringTrainer kmeansClusterer)
    {
        init(kmeansClusterer.getFeatureDimension(), kmeansClusterer.getTotalClusters(), kmeansClusterer.isDiagonalCovariance());
        
        int i;
        for (i=0; i<kmeansClusterer.getTotalClusters(); i++)
            components[i] = new GaussianComponent(kmeansClusterer.clusters[i]);
    }
    
    public GMM(GMM existing)
    {
        featureDimension = existing.featureDimension;
        totalComponents = existing.totalComponents;
        isDiagonalCovariance = existing.isDiagonalCovariance;
        
        if (existing.totalComponents>0 && existing.components!=null)
        {
            components = new GaussianComponent[totalComponents];
            for (int i=0; i<totalComponents; i++)
                components[i] = new GaussianComponent(existing.components[i]);
        }
        else
        {
            components = null;
            totalComponents = 0;
        }
        
        if (existing.weights!=null)
        {
            weights = new double[existing.weights.length];
            System.arraycopy(existing.weights, 0, weights, 0, existing.weights.length);
        }
        else
            weights = null;
        
        info = existing.info;
    }
    
    public GMM(String gmmFile)
    {
        try {
            read(gmmFile);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public void init(int featureDimensionIn, int totalMixturesIn, boolean isDiagonalCovarIn)
    {
        featureDimension = featureDimensionIn;
        totalComponents = totalMixturesIn;
        isDiagonalCovariance = isDiagonalCovarIn;
        
        if (totalComponents>0)
        {
            components = new GaussianComponent[totalComponents];
            weights = new double[totalComponents];
            
            for (int i=0; i<totalComponents; i++)
            {
                components[i] = new GaussianComponent(featureDimensionIn, isDiagonalCovarIn);
                weights[i] = 1.0/totalComponents;
            }
        }
        else
        {
            components = null;
            weights = null;
            totalComponents = 0;
            if (featureDimension<0)
                featureDimension=0;
        }
        
        info = "";
    }

    //P(x)
    public double probability(double[] x)
    {
        double score = 0.0;
        int i;

        for (i=0; i<totalComponents; i++)
            score += weights[i]*components[i].probability(x);

        return score;
    }
    
    //P(Ci|x)
    public double[] componentProbabilities(double[] x)
    {
        double[] probs = new double[totalComponents];
        int i;
        double totalProb = 0.0;
        
        if (isDiagonalCovariance)
        {
            for (i=0; i<totalComponents; i++)
            {
                probs[i] = weights[i]*MathUtils.getGaussianPdfValue(x, components[i].meanVector, components[i].covMatrix[0], components[i].getConstantTerm());
                totalProb += probs[i];
             }
        }
        else
        {
            for (i=0; i<totalComponents; i++)
            {
                probs[i] = weights[i]*MathUtils.getGaussianPdfValue(x, components[i].meanVector, components[i].getDetCovMatrix(), components[i].getInvCovMatrix());
                totalProb += probs[i];
            }
        }
        
        for (i=0; i<totalComponents; i++)
            probs[i] /= totalProb;
        
        return probs;
    }
    
    public void write(String gmmFile) throws IOException
    {
        MaryRandomAccessFile stream = new MaryRandomAccessFile(gmmFile, "rw");
        write(stream);
        stream.close();
    }
    
    public void write(MaryRandomAccessFile stream) throws IOException
    {
        stream.writeIntEndian(featureDimension);
        stream.writeIntEndian(totalComponents);
        stream.writeBooleanEndian(isDiagonalCovariance);
        if (info!=null && info.length()>0)
        {
            stream.writeIntEndian(info.length());
            stream.writeCharEndian(info.toCharArray());
        }
        else
            stream.writeIntEndian(0);
        
        stream.writeDoubleEndian(weights);
        for (int i=0; i<totalComponents; i++)
            components[i].write(stream);  
    }
    
    public void read(String gmmFile) throws IOException
    {
        MaryRandomAccessFile stream = new MaryRandomAccessFile(gmmFile, "r");
        read(stream);
        stream.close();
    }
    
    public void read(MaryRandomAccessFile stream) throws IOException
    {
        featureDimension = stream.readIntEndian();
        totalComponents = stream.readIntEndian();
        isDiagonalCovariance = stream.readBooleanEndian();
        int tmpLen = stream.readIntEndian();
        if (tmpLen>0)
            info = String.copyValueOf(stream.readCharEndian(tmpLen));
        
        weights = stream.readDoubleEndian(totalComponents);
        
        components = new GaussianComponent[totalComponents];
        for (int i=0; i<totalComponents; i++)
        {
            components[i] = new GaussianComponent();
            components[i].read(stream); 
        }
    }
}
