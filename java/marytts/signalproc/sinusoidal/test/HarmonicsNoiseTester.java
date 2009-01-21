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

package marytts.signalproc.sinusoidal.test;

import java.io.IOException;

import marytts.signalproc.sinusoidal.Sinusoid;
import marytts.util.io.FileUtils;
import marytts.util.math.MathUtils;

/**
 * @author oytun.turk
 *
 */
public class HarmonicsNoiseTester extends SinusoidsNoiseTester {

    public HarmonicsNoiseTester(HarmonicsTester s, NoiseTester n) {
        super(s, n);
        // TODO Auto-generated constructor stub
    }
    
    public static void main(String[] args) throws IOException
    { 
        int i;
        HarmonicsTester s = null;
        NoiseTester n = null;
        HarmonicsNoiseTester h = null;
        
        //Harmonics part
        float f1 = 400.f;
        int numHarmonics = 8;
        float harmonicsStartTimeInSeconds = 0.0f;
        float harmonicsEndTimeInSeconds = 1.0f;
        s = new HarmonicsTester(f1, numHarmonics, harmonicsStartTimeInSeconds, harmonicsEndTimeInSeconds);
        //
        
        //Noise part
        int numNoises = 1;
        float [][] freqs = new float[numNoises][];
        float [] amps = new float[numNoises];
        float noiseStartTimeInSeconds = 0.7f;
        float noiseEndTimeInSeconds = 1.5f;
        for (i=0; i<numNoises; i++)
            freqs[i] = new float[2];
        
        freqs[0][0] = 4000;
        freqs[0][1] = 6000;
        amps[0] = DEFAULT_AMP;

        n = new NoiseTester(freqs, amps, noiseStartTimeInSeconds, noiseEndTimeInSeconds);
        //
        
        h = new HarmonicsNoiseTester(s, n);
        
        if (args.length>1)
            h.write(args[0], args[1]);
        else
            h.write(args[0]);
    }

}
