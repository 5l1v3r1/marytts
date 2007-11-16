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

/**
 * @author oytun.turk
 *
 */

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.dfki.lt.mary.util.FileUtils;
import de.dfki.lt.signalproc.analysis.F0Reader;
import de.dfki.lt.signalproc.analysis.PitchMarker;
import de.dfki.lt.signalproc.util.AudioDoubleDataSource;
import de.dfki.lt.signalproc.util.BufferedDoubleDataSource;
import de.dfki.lt.signalproc.util.DDSAudioInputStream;
import de.dfki.lt.signalproc.util.MathUtils;
import de.dfki.lt.signalproc.util.SignalProcUtils;
import de.dfki.lt.signalproc.window.Window;

public class ProsodyModifier extends SinusoidalSynthesizer {
    
    public ProsodyModifier(int samplingRate) {
        super(samplingRate);
    }
    
    public double [] process(double [] x, 
                             double [] f0s, 
                             float f0_ws, float f0_ss,
                             boolean isVoicingAdaptiveTimeScaling,
                             float timeScalingVoicingThreshold, 
                             boolean isVoicingAdaptivePitchScaling,
                             float timeScale,
                             float pitchScale,
                             float skipSizeInSeconds,
                             float deltaInHz,
                             float numPeriods,
                             boolean bRefinePeakEstimatesParabola, 
                             boolean bRefinePeakEstimatesBias,  
                             boolean bAdjustNeighFreqDependent,
                             boolean isSilentSynthesis,
                             double absMaxDesired)
    {    
        float [] tScales = new float[1];
        float [] tScalesTimes = new float[1];
        tScales[0] = timeScale;
        tScalesTimes[0] = skipSizeInSeconds;
        
        float [] pScales = new float[1];
        float [] pScalesTimes = new float[1];
        pScales[0] = pitchScale;
        pScalesTimes[0] = skipSizeInSeconds;
        
        return process(x, 
                       f0s, 
                       f0_ws,  f0_ss,
                       isVoicingAdaptiveTimeScaling, 
                       timeScalingVoicingThreshold,
                       isVoicingAdaptivePitchScaling,
                       tScales,
                       tScalesTimes,
                       pScales,
                       pScalesTimes,
                       skipSizeInSeconds,
                       deltaInHz,
                       numPeriods,
                       bRefinePeakEstimatesParabola, 
                       bRefinePeakEstimatesBias,  
                       bAdjustNeighFreqDependent,
                       isSilentSynthesis,
                       absMaxDesired);
    }
    
    public double [] process(double [] x, 
                             double [] f0s, 
                             float f0_ws, float f0_ss,
                             boolean isVoicingAdaptiveTimeScaling,
                             float timeScalingVoicingThreshold,
                             boolean isVoicingAdaptivePitchScaling,
                             float [] timeScales,
                             float [] timeScalesTimes,
                             float [] pitchScales,
                             float [] pitchScalesTimes,
                             float skipSizeInSeconds,
                             float deltaInHz,
                             float numPeriods,
                             boolean bRefinePeakEstimatesParabola, 
                             boolean bRefinePeakEstimatesBias,  
                             boolean bAdjustNeighFreqDependent,
                             boolean isSilentSynthesis,
                             double absMaxDesired)
    {    
        //Analysis
        PitchSynchronousSinusoidalAnalyzer pa = new PitchSynchronousSinusoidalAnalyzer(fs, Window.HAMMING, bRefinePeakEstimatesParabola, bRefinePeakEstimatesBias, bAdjustNeighFreqDependent);
        
        PitchMarker pm = SignalProcUtils.pitchContour2pitchMarks(f0s, fs, x.length, f0_ws, f0_ss, false);

        SinusoidalTracks st = pa.analyzePitchSynchronous(x, pm.pitchMarks, numPeriods, skipSizeInSeconds, deltaInHz);
        
        try {
            st.writeToTextFile("d:\\log_ts1.txt");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        //To do: Estimation of voicing probabilities...
        
        //Modification
        SinusoidalTracks stMod = TrackModifier.modify(st, f0s, f0_ss, f0_ws, pm.pitchMarks, st.voicings, 
                                                               skipSizeInSeconds, numPeriods, 
                                                               isVoicingAdaptiveTimeScaling, 
                                                               timeScalingVoicingThreshold,
                                                               isVoicingAdaptivePitchScaling,  
                                                               timeScales, timeScalesTimes,
                                                               pitchScales, pitchScalesTimes);

        try {
            stMod.writeToTextFile("d:\\log_ts2.txt");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        //Synthesis
        return synthesize(stMod, absMaxDesired, isSilentSynthesis);
    }
    
    public static void main(String[] args) throws UnsupportedAudioFileException, IOException
    {
        //File input
        AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new File(args[0]));
        int samplingRate = (int)inputAudio.getFormat().getSampleRate();
        AudioDoubleDataSource signal = new AudioDoubleDataSource(inputAudio);
        double [] x = signal.getAllData();
        
        //Read pitch contour (real speech or create it from pm file
        F0Reader f0 = null;
        if (true) //Test using real speech (Make sure .ptc file with identical filename as the wavfile exists)
        {
            String strPitchFile = args[0].substring(0, args[0].length()-4) + ".ptc";
            f0 = new F0Reader(strPitchFile);
        }
        else //Test using simple sinusoids (Make sure .pm file with identical filename as the wavfile exists (Tester.java automatically generates it))  
        {
            String strPmFile = args[0].substring(0, args[0].length()-4) + ".pm";
            int [] pitchMarks = FileUtils.readFromBinaryFile(strPmFile);
            f0 = new F0Reader(pitchMarks, samplingRate, 0.020f, 0.010f); 
        }
        //
        
        //Analysis
        float deltaInHz = SinusoidalAnalyzer.DEFAULT_DELTA_IN_HZ;
        float numPeriods = PitchSynchronousSinusoidalAnalyzer.DEFAULT_ANALYSIS_PERIODS;
        boolean isSilentSynthesis = false;
        
        boolean bRefinePeakEstimatesParabola = false;
        boolean bRefinePeakEstimatesBias = false;
        boolean bAdjustNeighFreqDependent = false;
        double absMaxOriginal = MathUtils.getAbsMax(x);
        
        //float skipSizeInSeconds = TrackModifier.DEFAULT_MODIFICATION_SKIP_SIZE;
        float skipSizeInSeconds = 0.01f;
        float timeScale = 1.4f;
        float pitchScale = 1.0f;
        
        boolean isVoicingAdaptiveTimeScaling = true;
        float timeScalingVoicingThreshold = 0.3f;
        boolean isVoicingAdaptivePitchScaling = true;
        
        ProsodyModifier cs = new ProsodyModifier(samplingRate);
        
        double [] y = cs.process(x, 
                f0.getContour(), 
                (float)f0.ws, (float)f0.ss,
                isVoicingAdaptiveTimeScaling,
                timeScalingVoicingThreshold,
                isVoicingAdaptivePitchScaling,
                timeScale,
                pitchScale,
                skipSizeInSeconds,
                deltaInHz,
                numPeriods,
                bRefinePeakEstimatesParabola, 
                bRefinePeakEstimatesBias,  
                bAdjustNeighFreqDependent,
                isSilentSynthesis,
                absMaxOriginal);
        //

        //File output
        DDSAudioInputStream outputAudio = new DDSAudioInputStream(new BufferedDoubleDataSource(y), inputAudio.getFormat());
        String outFileName = args[0].substring(0, args[0].length()-4) + "_sinTScaled.wav";
        AudioSystem.write(outputAudio, AudioFileFormat.Type.WAVE, new File(outFileName));
        //
    }
}
