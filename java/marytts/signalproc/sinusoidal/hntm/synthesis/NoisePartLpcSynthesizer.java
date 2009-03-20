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

package marytts.signalproc.sinusoidal.hntm.synthesis;

import java.util.Arrays;

import marytts.signalproc.sinusoidal.hntm.analysis.FrameNoisePartLpc;
import marytts.signalproc.sinusoidal.hntm.analysis.HntmSpeechSignal;
import marytts.signalproc.window.Window;
import marytts.util.signal.SignalProcUtils;

/**
 * @author oytun.turk
 *
 */
public class NoisePartLpcSynthesizer {
    
    //LPC based noise model + OLA approach + Gain normalization according to generated harmonic part gain
    public static double[] synthesize(HntmSpeechSignal hnmSignal)
    { 
        double[] noisePart = null;
        int i;
        boolean isNoised, isPrevNoised, isNextNoised;
        boolean isVoiced;
        int lpOrder = 0;
        float t;
        float tsi = 0.0f;
        float tsiNext; //Time in seconds
        int startIndex = 0;
        int startIndexNext;
        int outputLen = SignalProcUtils.time2sample(hnmSignal.originalDurationInSeconds, hnmSignal.samplingRateInHz);
        
        for (i=0; i<hnmSignal.frames.length; i++)
        {
            isNoised = ((hnmSignal.frames[i].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz) ? true : false);
            if (isNoised)
            {
                lpOrder = ((FrameNoisePartLpc)hnmSignal.frames[i].n).lpCoeffs.length;
                break;
            }
        }

        float noiseWindowDurationInSeconds;
        float[] targetContour = new float[hnmSignal.frames.length];
        Arrays.fill(targetContour, 0.0f);
        float[] times = new float[hnmSignal.frames.length];
        Arrays.fill(times, -1.0f);
        
        if (lpOrder>0) //At least one noisy frame with LP coefficients exist
        {
            noisePart = new double[outputLen]; //In fact, this should be prosody scaled length when you implement prosody modifications
            Arrays.fill(noisePart, 0.0);
            double[] winWgtSum = new double[outputLen]; //In fact, this should be prosody scaled length when you implement prosody modifications
            Arrays.fill(winWgtSum, 0.0);

            Window winNoise;
            int windowType = Window.HAMMING;
            double[] x;
            double[] xWindowed;
            double[] y;
            double[] yWindowed;
            double[] yFiltered;
            double[] wgt;
            double[] yInitial = new double[lpOrder];
            Arrays.fill(yInitial, 0.0); //Start with zero initial conditions
            int n;
            int fftSizeNoise = SignalProcUtils.getDFTSize(hnmSignal.samplingRateInHz);

            int wsNoise = 0;

            boolean isDisplay = false;

            //Noise source of full length
            double[] noiseSourceHpf = null;
            //noiseSource = SignalProcUtils.getNoise(HnmAnalyzer.FIXED_MAX_FREQ_OF_VOICING_FOR_QUICK_TEST, 0.5f*hnmSignal.samplingRateInHz, HnmAnalyzer.HPF_TRANSITION_BANDWIDTH_IN_HZ,  hnmSignal.samplingRateInHz, (int)(1.1*outputLen)); //Pink noise full signal length, works OK
            /*
            if (HnmAnalyzer.FIXED_MAX_FREQ_OF_VOICING_FOR_QUICK_TEST<0.5*hnmSignal.samplingRateInHz)
                noiseSourceHpf = SignalProcUtils.getNoise(HnmAnalyzer.FIXED_MAX_FREQ_OF_VOICING_FOR_QUICK_TEST, HnmAnalyzer.FIXED_MAX_FREQ_OF_NOISE_FOR_QUICK_TEST, HnmAnalyzer.HPF_TRANSITION_BANDWIDTH_IN_HZ,  hnmSignal.samplingRateInHz, (int)(1.1*outputLen)); //Pink noise full signal length, works OK
            if (noiseSourceHpf!=null)
                MathUtils.adjustMeanVariance(noiseSourceHpf, 0.0, 1.0);
            double[] noiseSourceFull = SignalProcUtils.getWhiteNoise((int)(1.1*outputLen), 1.0); //White noise full signal length, works OK
            MathUtils.adjustMeanVariance(noiseSourceFull, 0.0, 1.0);
            */
            
            /*
            //Write the noise source to a wav file for checking
            AudioInputStream inputAudio = null;
            try {
                inputAudio = AudioSystem.getAudioInputStream(new File("d:\\hn.wav"));
            } catch (UnsupportedAudioFileException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            DDSAudioInputStream outputAudio = new DDSAudioInputStream(new BufferedDoubleDataSource(noiseSource), inputAudio.getFormat());
            try {
                AudioSystem.write(outputAudio, AudioFileFormat.Type.WAVE, new File("d:\\noiseSource.wav"));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
             */
            //

            int transitionOverlapLen = SignalProcUtils.time2sample(HntmSynthesizer.NOISE_SYNTHESIS_TRANSITION_OVERLAP_IN_SECONDS, hnmSignal.samplingRateInHz);
            
            for (i=0; i<hnmSignal.frames.length; i++)
            {
                if (hnmSignal.frames[i].h!=null && hnmSignal.frames[i].maximumFrequencyOfVoicingInHz>0.0f)
                    isVoiced = true;
                else
                    isVoiced = false;
                if (hnmSignal.frames[i].n!=null && hnmSignal.frames[i].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz)
                    isNoised = true;
                else
                    isNoised = false;
                if (i<hnmSignal.frames.length-1 && hnmSignal.frames[i+1].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz && hnmSignal.frames[i+1].n!=null)
                    isNextNoised = true;
                else
                    isNextNoised = false;
                if (i>0 && hnmSignal.frames[i-1].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz && hnmSignal.frames[i-1].n!=null)
                    isPrevNoised = true;
                else
                    isPrevNoised = false;

                if (i<hnmSignal.frames.length-1 && isNextNoised)
                    noiseWindowDurationInSeconds = Math.max(HntmSynthesizer.NOISE_SYNTHESIS_WINDOW_DURATION_IN_SECONDS, 2*(hnmSignal.frames[i+1].tAnalysisInSeconds-hnmSignal.frames[i].tAnalysisInSeconds));
                else
                    noiseWindowDurationInSeconds = HntmSynthesizer.NOISE_SYNTHESIS_WINDOW_DURATION_IN_SECONDS;
                wsNoise = SignalProcUtils.time2sample(noiseWindowDurationInSeconds, hnmSignal.samplingRateInHz);
                if (!isNextNoised)
                    wsNoise += transitionOverlapLen;
                if (!isPrevNoised)
                    wsNoise += transitionOverlapLen;
                
                if (wsNoise%2==0) //Always use an odd window size to have a zero-phase analysis window
                    wsNoise++; 

                if (i==0)
                    tsi = 0.0f;
                else
                    tsi = Math.max(0.0f, hnmSignal.frames[i].tAnalysisInSeconds-0.5f*noiseWindowDurationInSeconds);

                //if (tsi>1.8 && tsi<1.82)
                //    System.out.println("Time=" + String.valueOf(tsi) + " " + (isPrevNoised?"+":"-") + (isNoised?"+":"-") + (isNextNoised?"+":"-"));

                startIndex = SignalProcUtils.time2sample(tsi, hnmSignal.samplingRateInHz);

                if (i<hnmSignal.frames.length-1)
                {
                    tsiNext = Math.max(0.0f, hnmSignal.frames[i].tAnalysisInSeconds+0.5f*noiseWindowDurationInSeconds);
                    startIndexNext = SignalProcUtils.time2sample(tsiNext, hnmSignal.samplingRateInHz);
                }
                else
                {
                    startIndexNext = outputLen-1;
                    tsiNext = SignalProcUtils.sample2time(startIndexNext, hnmSignal.samplingRateInHz); 
                }

                if (isNoised && hnmSignal.frames[i].n!=null)
                {       
                    //Compute window
                    winNoise = Window.get(windowType, wsNoise);
                    wgt = winNoise.getCoeffs();
                    //

                    //x = SignalProcUtils.getWhiteNoiseOfVariance(wsNoise, 1.0); //Variance specified white noise
                    //x = SignalProcUtils.getWhiteNoise(wsNoise, 0.5); //Absolute value limited white noise

                    //double[] tmpNoise = SignalProcUtils.getNoise(hnmSignal.frames[i].maximumFrequencyOfVoicingInHz, 0.5f*hnmSignal.samplingRateInHz, 50.0, hnmSignal.samplingRateInHz, 5*wsNoise); //Pink noise
                    //x = new double[wsNoise];
                    //System.arraycopy(tmpNoise, 2*wsNoise, x, 0, wsNoise); 

                    //x = SignalProcUtils.getNoise(hnmSignal.frames[i].maximumFrequencyOfVoicingInHz, 0.5*hnmSignal.samplingRateInHz, 100.0, hnmSignal.samplingRateInHz, wsNoise); //Pink noise
                    x = SignalProcUtils.getWhiteNoise(wsNoise, 1.0f);
                    
                    y = SignalProcUtils.arFilterFreqDomain(x, ((FrameNoisePartLpc)hnmSignal.frames[i].n).lpCoeffs, 1.0, hnmSignal.frames[i].maximumFrequencyOfVoicingInHz, 0.5*hnmSignal.samplingRateInHz, hnmSignal.samplingRateInHz);
                    
                    if (hnmSignal.preCoefNoise>0.0f)
                        y = SignalProcUtils.removePreemphasis(y, hnmSignal.preCoefNoise);
                    
                    y = SignalProcUtils.normalizeAverageSampleEnergy(y, hnmSignal.frames[i].noiseTotalEnergyRatio*hnmSignal.frames[i].totalSampleEnergy);

                    y = winNoise.apply(y, 0);
                    
                    times[i] = hnmSignal.frames[i].tAnalysisInSeconds;
                    targetContour[i] = hnmSignal.frames[i].noiseTotalEnergyRatio*hnmSignal.frames[i].totalSampleEnergy;

                    //Overlap-add
                    for (n=startIndex; n<Math.min(startIndex+wsNoise, noisePart.length); n++)
                    {
                        noisePart[n] += y[n-startIndex]*wgt[n-startIndex]; 
                        winWgtSum[n] += wgt[n-startIndex]*wgt[n-startIndex];
                    }
                    //
                }

                System.out.println("LPC noise synthesis complete at " + String.valueOf(hnmSignal.frames[i].tAnalysisInSeconds) + "s. for frame " + String.valueOf(i+1) + " of " + String.valueOf(hnmSignal.frames.length) + "..." + String.valueOf(startIndex) + "-" + String.valueOf(startIndex+wsNoise)); 
            }
            
            for (i=0; i<winWgtSum.length; i++)
            {
                if (winWgtSum[i]>0.0)
                    noisePart[i] /= winWgtSum[i];
            }
        }
        
        //Energy contour normalization
        float[] currentContour = SignalProcUtils.getAverageSampleEnergyContour(noisePart, times, hnmSignal.samplingRateInHz, HntmSynthesizer.NOISE_SYNTHESIS_WINDOW_DURATION_IN_SECONDS);
        //MaryUtils.plot(currentContour);
        //MaryUtils.plot(noisePart);
        //MaryUtils.plot(targetContour);
        
        noisePart = SignalProcUtils.normalizeAverageSampleEnergyContour(noisePart, times, currentContour, targetContour, hnmSignal.samplingRateInHz, HntmSynthesizer.NOISE_SYNTHESIS_WINDOW_DURATION_IN_SECONDS);
        float[] currentContour2 = SignalProcUtils.getAverageSampleEnergyContour(noisePart, times, hnmSignal.samplingRateInHz, HntmSynthesizer.NOISE_SYNTHESIS_WINDOW_DURATION_IN_SECONDS);
        //MaryUtils.plot(currentContour2);
        //

        /*
        //Now, apply the triangular noise envelope for voiced parts
        double[] enEnv;
        int enEnvLen;
        tsiNext = 0;
        int l1, lMid, l2;
        for (i=0; i<hnmSignal.frames.length; i++)
        {
            isVoiced = ((hnmSignal.frames[i].maximumFrequencyOfVoicingInHz>0.0f) ? true : false);
            if (isVoiced)
            {
                if (i==0)
                    tsi = 0.0f; 
                else
                    tsi = hnmSignal.frames[i].tAnalysisInSeconds;

                startIndex = SignalProcUtils.time2sample(tsi, hnmSignal.samplingRateInHz);

                if (i<hnmSignal.frames.length-1)
                {
                    tsiNext = Math.max(0.0f, hnmSignal.frames[i+1].tAnalysisInSeconds);
                    startIndexNext = SignalProcUtils.time2sample(tsiNext, hnmSignal.samplingRateInHz);
                }
                else
                {
                    startIndexNext = outputLen-1;
                    tsiNext = SignalProcUtils.sample2time(startIndexNext, hnmSignal.samplingRateInHz);
                }
                
                enEnvLen = startIndexNext-startIndex+1;
                if (enEnvLen>0)
                {
                    enEnv = new double[enEnvLen];

                    int n;
                    l1 = SignalProcUtils.time2sample(0.15*(tsiNext-tsi), hnmSignal.samplingRateInHz);
                    l2 = SignalProcUtils.time2sample(0.85*(tsiNext-tsi), hnmSignal.samplingRateInHz);
                    lMid = (int)Math.floor(0.5*(l1+l2)+0.5);
                    for (n=0; n<l1; n++)
                        enEnv[n] = ENERGY_TRIANGLE_LOWER_VALUE;
                    for (n=l1; n<lMid; n++)
                        enEnv[n] = (n-l1)*(ENERGY_TRIANGLE_UPPER_VALUE-ENERGY_TRIANGLE_LOWER_VALUE)/(lMid-l1)+ENERGY_TRIANGLE_LOWER_VALUE;
                    for (n=lMid; n<l2; n++)
                        enEnv[n] = (n-lMid)*(ENERGY_TRIANGLE_LOWER_VALUE-ENERGY_TRIANGLE_UPPER_VALUE)/(l2-lMid)+ENERGY_TRIANGLE_UPPER_VALUE;
                    for (n=l2; n<enEnvLen; n++)
                        enEnv[n] = ENERGY_TRIANGLE_LOWER_VALUE;

                    for (n=startIndex; n<=Math.min(noisePart.length-1, startIndexNext); n++)
                        noisePart[n] *= enEnv[n-startIndex];
                }
            }
        }
        */

        return noisePart;
    }
}
