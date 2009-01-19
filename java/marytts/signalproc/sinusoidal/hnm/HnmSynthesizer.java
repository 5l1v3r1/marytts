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

package marytts.signalproc.sinusoidal.hnm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import marytts.signalproc.analysis.RegularizedCepstralEnvelopeEstimator;
import marytts.signalproc.filter.HighPassFilter;
import marytts.signalproc.window.Window;
import marytts.util.MaryUtils;
import marytts.util.data.BufferedDoubleDataSource;
import marytts.util.data.audio.AudioDoubleDataSource;
import marytts.util.data.audio.DDSAudioInputStream;
import marytts.util.math.MathUtils;
import marytts.util.signal.SignalProcUtils;

/**
 * Synthesis using harmonics plus noise model
 * 
 * @author Oytun T&uumlrk
 *
 */
public class HnmSynthesizer {
    
    public HnmSynthesizer()
    {
        
    }
    
    public HnmSynthesizedSignal syntesize(HnmSpeechSignal hnmSignal, 
                                          float[] tScales,
                                          float[] tScalesTimes,
                                          float[] pScales,
                                          float[] pScalesTimes)
    {
        HnmSynthesizedSignal s = new HnmSynthesizedSignal();
        s.harmonicPart = synthesizeHarmonicPart(hnmSignal, tScales, tScalesTimes, pScales, pScalesTimes);
        if (hnmSignal.frames[0].n instanceof FrameNoisePartLpc)
            s.noisePart = synthesizeNoisePartLpc(hnmSignal, tScales, tScalesTimes, pScales, pScalesTimes);
        else if (hnmSignal.frames[0].n instanceof FrameNoisePartPseudoHarmonic)
            s.noisePart = synthesizeNoisePartPseudoHarmonic(hnmSignal, tScales, tScalesTimes, pScales, pScalesTimes);
        
        return s;
    }
    
    public double[] synthesizeHarmonicPart(HnmSpeechSignal hnmSignal, 
                                           float[] tScales,
                                           float[] tScalesTimes,
                                           float[] pScales,
                                           float[] pScalesTimes)
    {
        double[] harmonicPart = null;
        
        int i, k, n;
        float t, tsi, tsiPlusOne; //Time in seconds
        int startIndex, endIndex;
        double akt;
        int numHarmonicsCurrentFrame;
        int maxNumHarmonics = 0;
        for (i=0; i<hnmSignal.frames.length; i++)
        {
            if (hnmSignal.frames[i].maximumFrequencyOfVoicingInHz>0.0f)
            {
                numHarmonicsCurrentFrame = hnmSignal.frames[i].h.phases.length;
                if (numHarmonicsCurrentFrame>maxNumHarmonics)
                    maxNumHarmonics = numHarmonicsCurrentFrame;
            }  
        }
        
        double[] aksis = null;
        double[] aksiPlusOnes = null;
        float[] phasekis = null;
        float[] phasekiPlusOnes = null;
        if (maxNumHarmonics>0)
        {
            aksis = new double[maxNumHarmonics];
            Arrays.fill(aksis, 0.0);
            aksiPlusOnes = new double[maxNumHarmonics];
            Arrays.fill(aksis, 0.0);
            phasekis = new float[maxNumHarmonics];
            Arrays.fill(phasekis, 0.0f);
            phasekiPlusOnes = new float[maxNumHarmonics];
            Arrays.fill(phasekiPlusOnes, 0.0f);
        }
        
        float f0InHz, f0InHzNext, f0av;
        int lastPeriodInSamples = 0;
        double ht;
        float phasekt = 0.0f;
        
        float phasekiPlusOneEstimate = 0.0f;
        int Mk;
        boolean isVoiced, isNextVoiced, isPrevVoiced;
        int origLen = SignalProcUtils.time2sample(hnmSignal.originalDurationInSeconds, hnmSignal.samplingRateInHz);
        harmonicPart = new double[origLen]; //In fact, this should be prosody scaled length when you implement prosody modifications
        Arrays.fill(harmonicPart, 0.0);
        
        for (i=0; i<hnmSignal.frames.length; i++)
        {
            isVoiced = ((hnmSignal.frames[i].maximumFrequencyOfVoicingInHz>0.0f) ? true : false);
            if (i<hnmSignal.frames.length-1)
                isNextVoiced = ((hnmSignal.frames[i+1].maximumFrequencyOfVoicingInHz>0.0f) ? true : false);
            else
                isNextVoiced = false;
            
            if (i>0)
                isPrevVoiced = ((hnmSignal.frames[i-1].maximumFrequencyOfVoicingInHz>0.0f) ? true : false);
            else
                isPrevVoiced = false;
            
            if (i==0)
                tsi = 0.0f;
            else
                tsi = hnmSignal.frames[i].tAnalysisInSeconds;
            startIndex = SignalProcUtils.time2sample(tsi, hnmSignal.samplingRateInHz);
            if (isNextVoiced)
            {
                if (i==hnmSignal.frames.length-1)
                    tsiPlusOne = hnmSignal.originalDurationInSeconds;
                else
                    tsiPlusOne = hnmSignal.frames[i+1].tAnalysisInSeconds;
                endIndex = SignalProcUtils.time2sample(tsiPlusOne, hnmSignal.samplingRateInHz);
            }
            else
            {
                if (i==hnmSignal.frames.length-1)
                {
                    tsiPlusOne = hnmSignal.originalDurationInSeconds;
                    endIndex = SignalProcUtils.time2sample(tsiPlusOne, hnmSignal.samplingRateInHz);
                }
                else
                {
                    endIndex = startIndex + lastPeriodInSamples;
                    tsiPlusOne = SignalProcUtils.sample2time(endIndex, hnmSignal.samplingRateInHz);
                }
            }

            numHarmonicsCurrentFrame = hnmSignal.frames[i].h.phases.length;
            for (n=startIndex; n<endIndex; n++)
            {
                t = SignalProcUtils.sample2time(n, hnmSignal.samplingRateInHz);

                harmonicPart[n] = 0.0;
                for (k=0; k<Math.min(numHarmonicsCurrentFrame,maxNumHarmonics); k++)
                {
                    f0InHz = hnmSignal.frames[i].h.f0InHz;
                    if (isNextVoiced && k<hnmSignal.frames[i+1].h.phases.length)
                        f0InHzNext = hnmSignal.frames[i+1].h.f0InHz;
                    else
                        f0InHzNext = f0InHz;

                    f0av = 0.5f*(f0InHz+f0InHzNext);

                    //Estimate amplitude
                    if (i==0)
                        aksis[k] = RegularizedCepstralEnvelopeEstimator.cepstrum2linearSpectrumValue(hnmSignal.frames[i].h.ceps, (k+1)*f0InHz, hnmSignal.samplingRateInHz);
                    else
                    {
                        if (isPrevVoiced)
                            aksis[k] = aksiPlusOnes[k]; //from previous
                        else
                            aksis[k] = 0.0;
                    }

                    if (isNextVoiced)
                        aksiPlusOnes[k] = RegularizedCepstralEnvelopeEstimator.cepstrum2linearSpectrumValue(hnmSignal.frames[i+1].h.ceps , (k+1)*f0InHzNext, hnmSignal.samplingRateInHz);
                    else
                        aksiPlusOnes[k] = 0.0;

                    akt = aksis[k] + (aksiPlusOnes[k]-aksis[k])*(t-tsi)/(tsiPlusOne-tsi);
                    //

                    //Estimate phase
                    if (isVoiced)
                        phasekis[k] = hnmSignal.frames[i].h.phases[k];
                    
                    if (isNextVoiced && k<hnmSignal.frames[i+1].h.phases.length)
                        phasekiPlusOnes[k] = hnmSignal.frames[i+1].h.phases[k];
                        
                    if (!isVoiced)
                        phasekis[k] = (float)( phasekiPlusOnes[k] - (k+1)*MathUtils.TWOPI*f0InHzNext*(tsiPlusOne-tsi) ); //Equation (3.54)
                     
                    if (!(isNextVoiced && k<hnmSignal.frames[i+1].h.phases.length))
                        phasekiPlusOnes[k] = (float)( phasekis[k] + (k+1)*MathUtils.TWOPI*f0InHz*(tsiPlusOne-tsi) ); //Equation (3.55)
                        
                    phasekiPlusOneEstimate = (float)( phasekis[k] + (k+1)*MathUtils.TWOPI*f0av*(tsiPlusOne-tsi));
                    Mk = (int)Math.floor((phasekiPlusOneEstimate-phasekiPlusOnes[k])/MathUtils.TWOPI + 0.5);
                    phasekt = (float)( phasekis[k] + (phasekiPlusOnes[k]+MathUtils.TWOPI*Mk-phasekis[k])*(t-tsi)/(tsiPlusOne-tsi) );
                    //

                    harmonicPart[n] += akt*Math.cos(phasekt);
                }
            }

            lastPeriodInSamples = SignalProcUtils.time2sample(tsiPlusOne - tsi, hnmSignal.samplingRateInHz);
        }
        
        return harmonicPart;
    }
    
    //LPC based noise model + OLA approach
    public double[] synthesizeNoisePartLpc(HnmSpeechSignal hnmSignal, 
                                           float[] tScales,
                                           float[] tScalesTimes,
                                           float[] pScales,
                                           float[] pScalesTimes)
    {
        double[] noisePart = null;
        int i;
        boolean isNoised, isNextNoised;
        int lpOrder = 0;
        for (i=0; i<hnmSignal.frames.length; i++)
        {
            isNoised = ((hnmSignal.frames[i].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz) ? true : false);
            if (isNoised)
            {
                lpOrder = ((FrameNoisePartLpc)hnmSignal.frames[i].n).lpCoeffs.length;
                break;
            }
        }

        if (lpOrder>0) //At least one noisy frame with LP coefficients exist
        {
            int origLen = SignalProcUtils.time2sample(hnmSignal.originalDurationInSeconds, hnmSignal.samplingRateInHz);
            noisePart = new double[origLen]; //In fact, this should be prosody scaled length when you implement prosody modifications
            Arrays.fill(noisePart, 0.0);
            double[] winWgtSum = new double[origLen]; //In fact, this should be prosody scaled length when you implement prosody modifications
            Arrays.fill(winWgtSum, 0.0);
            int startIndex, endIndex;

            float t, tsi; //Time in seconds

            Window win;
            int windowType = Window.HAMMING;
            float numPeriods = 4.0f;
            double[] x;
            double[] xWindowed;
            double[] y;
            double[] yWindowed;
            double[] yFiltered;
            double[] wgt;
            double[] yInitial = new double[lpOrder];
            Arrays.fill(yInitial, 0.0); //Start with zero initial conditions
            int n;
            HighPassFilter hpf;
            int fftSize = SignalProcUtils.getDFTSize(hnmSignal.samplingRateInHz);
            
            int wsNoise = SignalProcUtils.time2sample(hnmSignal.windowDurationInSecondsNoise, hnmSignal.samplingRateInHz);
            if (wsNoise%2==0) //Always use an odd window size to have a zero-phase analysis window
                wsNoise++; 
            
            for (i=0; i<hnmSignal.frames.length; i++)
            {
                isNoised = ((hnmSignal.frames[i].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz) ? true : false);
                if (i<hnmSignal.frames.length-1)
                    isNextNoised = ((hnmSignal.frames[i+1].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz) ? true : false);
                else
                    isNextNoised = true;
                
                if (isNoised)
                {
                    if (i==0)
                        tsi = 0.0f;
                    else
                        tsi = Math.max(0.0f, hnmSignal.frames[i].tAnalysisInSeconds-0.5f*hnmSignal.windowDurationInSecondsNoise);

                    startIndex = SignalProcUtils.time2sample(tsi, hnmSignal.samplingRateInHz);

                    //Compute window
                    win = Window.get(windowType, wsNoise);
                    wgt = win.getCoeffs();
                    //
                    
                    x = SignalProcUtils.getWhiteNoiseOfVariance(wsNoise, 1.0);
                    //xWindowed = win.apply(x, 0);
                    y = SignalProcUtils.arFilter(x, ((FrameNoisePartLpc)hnmSignal.frames[i].n).lpCoeffs, ((FrameNoisePartLpc)hnmSignal.frames[i].n).lpGain, yInitial);
                    
                    //MaryUtils.plot(x);
                    //MaryUtils.plot(y);
                    
                    //Highpass filter and overlap-add
                    
                    yWindowed = win.apply(y, 0);
                    //yFiltered = SignalProcUtils.fdFilter(yWindowed, hnmSignal.frames[i].maximumFrequencyOfVoicingInHz, 0.5f*hnmSignal.samplingRateInHz, hnmSignal.samplingRateInHz, fftSize);
                    
                    for (n=startIndex; n<Math.min(startIndex+wsNoise, noisePart.length); n++)
                    {
                        noisePart[n] += y[n-startIndex]*wgt[n-startIndex]; 
                        winWgtSum[n] += wgt[n-startIndex]; 
                    }
                    //
                    
                    //Save last lpOrder filter outputs to use as initial conditions for the next frame, provided that next frame is noised, otherwise set them to zero again
                    if (isNextNoised)
                    {
                        for (n=wsNoise-lpOrder; n<=wsNoise-1; n++)
                            yInitial[n-wsNoise+lpOrder] = y[n];
                    }
                    else
                        Arrays.fill(yInitial, 0.0);
                    //
                }
            }
            
            //Normalize according to total window weights per sample
            for (i=0; i<origLen; i++)
            {
                if (winWgtSum[i]>0.0)
                    noisePart[i] /= winWgtSum[i];
            }
            //
        }

        return noisePart;
    }
    
    //Pseudo harmonics based noise generation for pseudo periods
    public static double[] synthesizeNoisePartPseudoHarmonic(HnmSpeechSignal hnmSignal, 
                                                             float[] tScales,
                                                             float[] tScalesTimes,
                                                             float[] pScales,
                                                             float[] pScalesTimes)
    {
        int i, k, n;
        float t, tsi, tsiPlusOne; //Time in seconds
        int startIndex, endIndex;
        
        double[] noisePart = null;
        int origLen = SignalProcUtils.time2sample(hnmSignal.originalDurationInSeconds, hnmSignal.samplingRateInHz);
        noisePart = new double[origLen]; //In fact, this should be prosody scaled length when you implement prosody modifications
        Arrays.fill(noisePart, 0.0);
        
        double akt;
        int startPseudoHarmonicNo, startPseudoHarmonicNoNext;
        int endPseudoHarmonicNo = (int)Math.floor((0.5*hnmSignal.samplingRateInHz)/HnmAnalyzer.NOISE_F0_IN_HZ+0.5);

        double[] aksis = null;
        double[] aksiPlusOnes = null;
        float[] phasekis = null;

        int numNoiseHarmonicsCurrentFrame;
        int numNoiseHarmonicsNextFrame = 0;
        int maxNumHarmonics = 0;
        for (i=0; i<hnmSignal.frames.length; i++)
        {
            if (hnmSignal.frames[i].maximumFrequencyOfVoicingInHz<0.5*hnmSignal.samplingRateInHz)
            {
                startPseudoHarmonicNo = (int)(Math.floor((hnmSignal.frames[i].maximumFrequencyOfVoicingInHz+0.5*HnmAnalyzer.NOISE_F0_IN_HZ)/HnmAnalyzer.NOISE_F0_IN_HZ))-1;
                numNoiseHarmonicsCurrentFrame = endPseudoHarmonicNo-startPseudoHarmonicNo+1;
                if (numNoiseHarmonicsCurrentFrame>maxNumHarmonics)
                    maxNumHarmonics = numNoiseHarmonicsCurrentFrame;
            }  
        }

        if (maxNumHarmonics>0)
        {
            aksis = new double[maxNumHarmonics];
            Arrays.fill(aksis, 0.0);
            aksiPlusOnes = new double[maxNumHarmonics];
            Arrays.fill(aksis, 0.0);
            phasekis = new float[maxNumHarmonics];
            for (i=0; i<maxNumHarmonics; i++)
                phasekis[i] = (float)(MathUtils.TWOPI*Math.random()-0.5*MathUtils.TWOPI); 
        }

        int lastPeriodInSamples = 0;
        double ht;
        float phasekt = 0.0f;

        float phasekiPlusOneEstimate = 0.0f;
        int Mk;
        boolean isNoised, isNextNoised, isPrevNoised;

        for (i=0; i<hnmSignal.frames.length; i++)
        {
            startPseudoHarmonicNo = (int)(Math.floor((hnmSignal.frames[i].maximumFrequencyOfVoicingInHz+0.5*HnmAnalyzer.NOISE_F0_IN_HZ)/HnmAnalyzer.NOISE_F0_IN_HZ))-1;

            if (i==0)
                numNoiseHarmonicsCurrentFrame = endPseudoHarmonicNo-startPseudoHarmonicNo+1;
            else
                numNoiseHarmonicsCurrentFrame = numNoiseHarmonicsNextFrame;

            if (i<hnmSignal.frames.length-1)
            {  
                startPseudoHarmonicNoNext = (int)(Math.floor((hnmSignal.frames[i+1].maximumFrequencyOfVoicingInHz+0.5*HnmAnalyzer.NOISE_F0_IN_HZ)/HnmAnalyzer.NOISE_F0_IN_HZ))-1;
                numNoiseHarmonicsNextFrame = endPseudoHarmonicNo-startPseudoHarmonicNoNext+1;
            }

            isNoised = ((hnmSignal.frames[i].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz) ? true : false);
            if (i<hnmSignal.frames.length-1)
                isNextNoised = ((hnmSignal.frames[i+1].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz) ? true : false);
            else
                isNextNoised = true;

            if (i>0)
                isPrevNoised = ((hnmSignal.frames[i-1].maximumFrequencyOfVoicingInHz<0.5f*hnmSignal.samplingRateInHz) ? true : false);
            else
                isPrevNoised = true;

            if (i==0)
                tsi = 0.0f;
            else
                tsi = hnmSignal.frames[i].tAnalysisInSeconds;
            startIndex = SignalProcUtils.time2sample(tsi, hnmSignal.samplingRateInHz);
            if (isNextNoised)
            {
                if (i==hnmSignal.frames.length-1)
                    tsiPlusOne = hnmSignal.originalDurationInSeconds;
                else
                    tsiPlusOne = hnmSignal.frames[i+1].tAnalysisInSeconds;
                endIndex = SignalProcUtils.time2sample(tsiPlusOne, hnmSignal.samplingRateInHz);
            }
            else
            {
                if (i==hnmSignal.frames.length-1)
                {
                    tsiPlusOne = hnmSignal.originalDurationInSeconds;
                    endIndex = SignalProcUtils.time2sample(tsiPlusOne, hnmSignal.samplingRateInHz);
                }
                else
                {
                    endIndex = startIndex + lastPeriodInSamples;
                    tsiPlusOne = SignalProcUtils.sample2time(endIndex, hnmSignal.samplingRateInHz);
                }
            }

            for (n=startIndex; n<endIndex; n++)
            {
                t = SignalProcUtils.sample2time(n, hnmSignal.samplingRateInHz);

                noisePart[n] = 0.0;
                for (k=startPseudoHarmonicNo; k<=endPseudoHarmonicNo; k++)
                {
                    //Estimate amplitude
                    if (i==0)
                    {
                        //aksis[k-startPseudoHarmonicNo] = RegularizedCepstralEnvelopeEstimator.cepstrum2linearSpectrumValue(((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i].n).ceps, (k+1)*HnmAnalyzer.NOISE_F0_IN_HZ, hnmSignal.samplingRateInHz);
                        //What if analysis sends noise amplitudes directly?
                                aksis[k-startPseudoHarmonicNo] = ((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i].n).ceps[k-startPseudoHarmonicNo];
                    }
                    else
                    {
                        if (isPrevNoised)
                            aksis[k-startPseudoHarmonicNo] = aksiPlusOnes[k-startPseudoHarmonicNo]; //from previous
                        else
                            aksis[k-startPseudoHarmonicNo] = 0.0;
                    }

                    if (isNextNoised && i<hnmSignal.frames.length-1)
                    {
                        //aksiPlusOnes[k-startPseudoHarmonicNo] = RegularizedCepstralEnvelopeEstimator.cepstrum2linearSpectrumValue(((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i+1].n).ceps , (k+1)*HnmAnalyzer.NOISE_F0_IN_HZ, hnmSignal.samplingRateInHz);
                        //What if analysis sends noise amplitudes directly?
                        aksiPlusOnes[k-startPseudoHarmonicNo] = ((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i+1].n).ceps[k-startPseudoHarmonicNo];
                    }
                    else
                        aksiPlusOnes[k-startPseudoHarmonicNo] = 0.0;

                    akt = aksis[k-startPseudoHarmonicNo] + (aksiPlusOnes[k-startPseudoHarmonicNo]-aksis[k-startPseudoHarmonicNo])*(t-tsi)/(tsiPlusOne-tsi);
                    //

                    //Estimate phase   
                    phasekiPlusOneEstimate = (float)( phasekis[k-startPseudoHarmonicNo] + (k+1)*MathUtils.TWOPI*HnmAnalyzer.NOISE_F0_IN_HZ*(tsiPlusOne-tsi));
                    phasekt = (float)(phasekiPlusOneEstimate*(t-tsi)/(tsiPlusOne-tsi) );
                    //

                    noisePart[n] += akt*Math.cos(phasekt);

                    if (!isNextNoised) //Set to random if next frame is fully voiced (which should be an extremely rare case!)
                        phasekis[k-startPseudoHarmonicNo] = (float)(MathUtils.TWOPI*Math.random()-0.5*MathUtils.TWOPI);
                }
            }

            lastPeriodInSamples = SignalProcUtils.time2sample(tsiPlusOne - tsi, hnmSignal.samplingRateInHz);
        }
        
        return noisePart;
    }
    
    public static void main(String[] args) throws UnsupportedAudioFileException, IOException
    {
        //File input
        String wavFile = args[0];
        AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new File(wavFile));
        int samplingRate = (int)inputAudio.getFormat().getSampleRate();
        AudioDoubleDataSource signal = new AudioDoubleDataSource(inputAudio);
        double[] x = signal.getAllData();
        double maxOrig = MathUtils.getAbsMax(x);
        //
        
        //Analysis
        double skipSizeInSeconds = 0.010;

        HnmAnalyzer ha = new HnmAnalyzer();
        int noisePartRepresentation = HnmAnalyzer.LPC;
        //int noisePartRepresentation = HnmAnalyzer.PSEUDO_HARMONIC;
        
        HnmSpeechSignal hnmSignal = ha.analyze(wavFile, skipSizeInSeconds, noisePartRepresentation);
        //
        
        //Synthesis
        float[] tScales = {1.0f};
        float[] tScalesTimes = null;
        float[] pScales = {1.0f};
        float[] pScalesTimes = null;
        
        HnmSynthesizer hs = new HnmSynthesizer();
        HnmSynthesizedSignal xhat = hs.syntesize(hnmSignal, tScales, tScalesTimes, pScales, pScalesTimes);
        double[] y = SignalProcUtils.addSignals(xhat.harmonicPart, xhat.noisePart);
        //
        
        //This scaling is only for comparison among different parameter sets, different synthesizer outputs etc
        double maxNew = MathUtils.getAbsMax(y);
        for (int i=0; i<y.length; i++)
            y[i] = y[i]*(maxOrig/maxNew);
        //
        
        //A small test here: What if we substract y from x: In case no noise part is synthesized, this should be like a noise signal
        double[] nEstimate = null;
        nEstimate = SignalProcUtils.subtractSignals(x, y); //Not good, still amplitude difference and phase difference
        
        //File output
        DDSAudioInputStream outputAudio = new DDSAudioInputStream(new BufferedDoubleDataSource(y), inputAudio.getFormat());
        String outFileName = args[0].substring(0, args[0].length()-4) + "_hnmResynth.wav";
        AudioSystem.write(outputAudio, AudioFileFormat.Type.WAVE, new File(outFileName));
        
        outputAudio = new DDSAudioInputStream(new BufferedDoubleDataSource(xhat.harmonicPart), inputAudio.getFormat());
        outFileName = args[0].substring(0, args[0].length()-4) + "_hnmHarmonic.wav";
        AudioSystem.write(outputAudio, AudioFileFormat.Type.WAVE, new File(outFileName));
        
        outputAudio = new DDSAudioInputStream(new BufferedDoubleDataSource(xhat.noisePart), inputAudio.getFormat());
        outFileName = args[0].substring(0, args[0].length()-4) + "_hnmNoise.wav";
        AudioSystem.write(outputAudio, AudioFileFormat.Type.WAVE, new File(outFileName));
        
        if (nEstimate!=null)
        {
            outputAudio = new DDSAudioInputStream(new BufferedDoubleDataSource(nEstimate), inputAudio.getFormat());
            outFileName = args[0].substring(0, args[0].length()-4) + "_hnmDiff.wav";
            AudioSystem.write(outputAudio, AudioFileFormat.Type.WAVE, new File(outFileName));
        }
        //
    }
}