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

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.dfki.lt.signalproc.analysis.F0Reader;
import de.dfki.lt.signalproc.analysis.PitchMarker;
import de.dfki.lt.signalproc.util.AudioDoubleDataSource;
import de.dfki.lt.signalproc.util.DoubleDataSource;
import de.dfki.lt.signalproc.util.SignalProcUtils;
import de.dfki.lt.signalproc.window.Window;

/**
 * @author oytun.turk
 *
 */
public class PitchSynchronousSinusoidalAnalyzer extends SinusoidalAnalyzer {
    public static float DEFAULT_ANALYSIS_PERIODS = 2.5f;
    
    // fs: Sampling rate in Hz
    // windowType: Type of window (See class Window for details)
    // bRefinePeakEstimatesParabola: Refine peak and frequency estimates by fitting parabolas?
    // bRefinePeakEstimatesBias: Further refine peak and frequency estimates by correcting bias? 
    //                           (Only effective when bRefinePeakEstimatesParabola=true)
    public PitchSynchronousSinusoidalAnalyzer(int samplingRate, int windowTypeIn, boolean bRefinePeakEstimatesParabolaIn, boolean bRefinePeakEstimatesBiasIn)
    {
        super(samplingRate, windowTypeIn, bRefinePeakEstimatesParabolaIn, bRefinePeakEstimatesBiasIn);
    }
    
    public PitchSynchronousSinusoidalAnalyzer(int samplingRate, int windowTypeIn, boolean bRefinePeakEstimatesParabolaIn)
    {
        this(samplingRate, windowTypeIn, bRefinePeakEstimatesParabolaIn, true);
    }
    
    public PitchSynchronousSinusoidalAnalyzer(int samplingRate, int windowTypeIn)
    {
        this(samplingRate, windowTypeIn, true);
    }
    
    public PitchSynchronousSinusoidalAnalyzer(int samplingRate)
    {
        this(samplingRate, Window.HAMMING);
    }
    //
    
    //Pitch synchronous analysis
    public SinusoidalTracks analyzePitchSynchronous(double [] x, int [] pitchMarks)
    {
        return analyzePitchSynchronous(x, pitchMarks, DEFAULT_ANALYSIS_PERIODS);
    }
    
    /* 
     * Pitch synchronous analysis
     * 
     * x: Speech/Audio signal to be analyzed
     * pitchMarks: Integer array of sample indices for pitch period start instants
     * numPeriods: Number of pitch periods to be used in analysis
     */
    public SinusoidalTracks analyzePitchSynchronous(double [] x, int [] pitchMarks, float numPeriods)
    {
        int totalFrm = (int)Math.floor(pitchMarks.length-numPeriods+0.5);
        if (totalFrm>pitchMarks.length-1)
            totalFrm = pitchMarks.length-1;
        
        //Extract frames and analyze them
        double [] frm = null;
        int i, j;

        Sinusoid [][] framesSins =  new Sinusoid[totalFrm][];
        float [] times = new float[totalFrm];
        
        for (i=0; i<totalFrm; i++)
        {
            ws = (int)Math.floor(numPeriods*(pitchMarks[i+1]-pitchMarks[i])+ 0.5);
            frm = new double[ws];
            
            win = Window.get(windowType, ws);
            win.normalize(1.0f); //Normalize to sum up to unity
            
            Arrays.fill(frm, 0.0);
            for (j=pitchMarks[i]; j<Math.min(pitchMarks[i]+ws-1, x.length); j++)
                frm[j-pitchMarks[i]] = x[j];
            
            win.apply(frm, 0);
            
            framesSins[i] = analyze_frame(frm);
            
            times[i] = (float)(0.5*(pitchMarks[i+1]+pitchMarks[i])/fs);
            
            System.out.println("Analysis complete for frame " + String.valueOf(i+1) + " of " + String.valueOf(totalFrm));
        }
        //
        
        //Extract sinusoidal tracks
        TrackGenerator tg = new TrackGenerator();
        SinusoidalTracks sinTracks = tg.generateTracksFreqOnly(framesSins, times, 50.0f, fs);
        sinTracks.getTrackStatistics();
        
        return sinTracks;
    }
    
    public static void main(String[] args) throws UnsupportedAudioFileException, IOException
    {
        AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new File(args[0]));
        int samplingRate = (int)inputAudio.getFormat().getSampleRate();
        AudioDoubleDataSource signal = new AudioDoubleDataSource(inputAudio);
        double [] x = signal.getAllData();
        
        String strPitchFile = args[0].substring(0, args[0].length()-4) + ".ptc";
        F0Reader f0 = new F0Reader(strPitchFile);
        PitchMarker pm = SignalProcUtils.pitchContour2pitchMarks(f0.getContour(), samplingRate, x.length, f0.ws, f0.ss, true);
        PitchSynchronousSinusoidalAnalyzer sa = new PitchSynchronousSinusoidalAnalyzer(samplingRate, Window.HAMMING, true, true);
        
        SinusoidalTracks st = sa.analyzePitchSynchronous(x, pm.pitchMarks);        
    }
}
