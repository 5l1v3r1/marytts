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

package de.dfki.lt.mary.sinusoidal;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.dfki.lt.signalproc.analysis.F0Reader;
import de.dfki.lt.signalproc.analysis.PitchMarker;
import de.dfki.lt.signalproc.util.AudioDoubleDataSource;
import de.dfki.lt.signalproc.util.BufferedDoubleDataSource;
import de.dfki.lt.signalproc.util.DDSAudioInputStream;
import de.dfki.lt.signalproc.util.MathUtils;
import de.dfki.lt.signalproc.util.SignalProcUtils;
import de.dfki.lt.signalproc.window.Window;

/**
 * @author oytun.turk
 * 
 * Sinusoidal Modeling Synthesis Module
 * Given tracks of sinusoids estimated during analysis and after possible modifications,
 * synthesis of output speech is performed
 * 
 */
public class SinusoidalSynthesizer {
    private int fs; //Sampling rate in Hz
    
    public SinusoidalSynthesizer(int samplingRate)
    {
        fs = samplingRate;
    }
    
    public double [] synthesize(SinusoidalTracks st)
    {
        int n; //discrete time index
        int i, j;
        int nStart, nEnd;
        float t; //continuous time
        float t2; //continuous time squared
        float t3; //continuous time cubed
        
        float tFinal = st.getOriginalDuration();
        int nFinal = (int)(Math.floor(tFinal*st.fs + 0.5));
        double [] y = new double[nFinal+1];
        Arrays.fill(y, 0.0);
        float currentAmp;
        float currentPhase;
        double alpha, beta;
        int M;
        int T; //Number of samples between consecutive frames (equals to pitch period in pitch synchronous analysis/synthesis)
        int T2; //T squared
        int T3; //T cubed
        double oneOverTwoPi = 1.0/MathUtils.TWOPI;
        double term1, term2;
            
        for (i=0; i<st.totalTracks; i++)
        {
            for (j=0; j<st.tracks[i].totalSins-1; j++)
            {
                nStart = (int)Math.floor(st.tracks[i].times[j]*st.fs+0.5);
                nEnd = (int)Math.floor(st.tracks[i].times[j+1]*st.fs+0.5);
                
                for (n=nStart; n<nEnd; n++)
                {
                    if (false) //Direct synthesis
                    {
                        currentAmp = st.tracks[i].amps[j];
                        currentPhase = st.tracks[i].phases[j];
                    }
                    else //Synthesis with interpolation
                    {
                        //Amplitude interpolation
                        currentAmp = st.tracks[i].amps[j] + (st.tracks[i].amps[j+1]-st.tracks[i].amps[j])*((float)n-nStart)/(nEnd-nStart+1);

                        T = nEnd-nStart+1;

                        //Birth of a track
                        if (j==0)
                            currentPhase = st.tracks[i].phases[j+1] - T*st.tracks[i].freqs[j+1];
                        else //Cubic phase interpolation
                        {

                            M = (int)(Math.floor(oneOverTwoPi*((st.tracks[i].phases[j] + T*st.tracks[i].freqs[j] - st.tracks[i].phases[j+1])+(st.tracks[i].freqs[j+1]-st.tracks[i].freqs[j])*0.5*T)+0.5));
                            term1 = st.tracks[i].phases[j+1]-st.tracks[i].phases[j]-T*st.tracks[i].freqs[j]+M*MathUtils.TWOPI;
                            term2 = st.tracks[i].freqs[j+1]-st.tracks[i].freqs[j];
                            T2 = T*T;
                            T3 = T*T2;
                            alpha = 3.0*term1/T2-term2/T;
                            beta = -2*term1/T3+term2/T2;

                            t = ((float)n)/st.fs;
                            t2 = t*t;
                            t3 = t*t2;
                            currentPhase = (float)(st.tracks[i].phases[j] + st.tracks[i].freqs[j]*t + alpha*t2 + beta*t3);
                        }
                    }
                    
                    //Synthesis
                    y[n] += currentAmp*Math.cos((n-nStart)*st.tracks[i].freqs[j] + currentPhase);
                }
            }
            
            System.out.println("Synthesized track " + String.valueOf(i+1) + " of " + String.valueOf(st.totalTracks));
        }  
        
        double maxy = MathUtils.getAbsMax(y);
        for (i=0; i<y.length; i++)
            y[i] = 0.95*y[i]/maxy;
        
        return y;
    }
    
    public static void main(String[] args) throws UnsupportedAudioFileException, IOException
    {
        //File input
        AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new File(args[0]));
        int samplingRate = (int)inputAudio.getFormat().getSampleRate();
        AudioDoubleDataSource signal = new AudioDoubleDataSource(inputAudio);
        double [] x = signal.getAllData();
        
        SinusoidalAnalyzer sa = null;
        SinusoidalTracks st = null;
        PitchSynchronousSinusoidalAnalyzer pa = null;
        //
        
        //Analysis
        if (true)
        {
            sa = new SinusoidalAnalyzer(samplingRate, Window.HAMMING, true, true);
            st = sa.analyzeFixedRate(x, 0.020f, 0.010f);
        }
        else
        {
            String strPitchFile = args[0].substring(0, args[0].length()-4) + ".ptc";
            F0Reader f0 = new F0Reader(strPitchFile);
            PitchMarker pm = SignalProcUtils.pitchContour2pitchMarks(f0.getContour(), samplingRate, x.length, f0.ws, f0.ss, true);
            pa = new PitchSynchronousSinusoidalAnalyzer(samplingRate, Window.HAMMING, true, true);
            st = pa.analyzePitchSynchronous(x, pm.pitchMarks);
        }
        //
        
        //Resynthesis
        SinusoidalSynthesizer ss = new SinusoidalSynthesizer(samplingRate);
        x = ss.synthesize(st);
        //
        
        //File output
        DDSAudioInputStream outputAudio = new DDSAudioInputStream(new BufferedDoubleDataSource(x), inputAudio.getFormat());
        String outFileName = args[0].substring(0, args[0].length()-4) + "_sinResynth.wav";
        AudioSystem.write(outputAudio, AudioFileFormat.Type.WAVE, new File(outFileName));
        //
    }
}
