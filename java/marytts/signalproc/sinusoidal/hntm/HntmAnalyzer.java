/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.signalproc.sinusoidal.hntm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import marytts.machinelearning.ContextualGMMParams;
import marytts.modules.phonemiser.Allophone;
import marytts.modules.phonemiser.AllophoneSet;
import marytts.signalproc.analysis.CepstrumSpeechAnalyser;
import marytts.signalproc.analysis.F0ReaderWriter;
import marytts.signalproc.analysis.Labels;
import marytts.signalproc.analysis.LpcAnalyser;
import marytts.signalproc.analysis.PitchMarks;
import marytts.signalproc.analysis.RegularizedCepstralEnvelopeEstimator;
import marytts.signalproc.analysis.SeevocAnalyser;
import marytts.signalproc.analysis.SpectrumWithPeakIndices;
import marytts.signalproc.analysis.LpcAnalyser.LpCoeffs;
import marytts.signalproc.filter.HighPassFilter;
import marytts.signalproc.sinusoidal.BaseSinusoidalSpeechSignal;
import marytts.signalproc.sinusoidal.NonharmonicSinusoidalSpeechFrame;
import marytts.signalproc.sinusoidal.NonharmonicSinusoidalSpeechSignal;
import marytts.signalproc.sinusoidal.PitchSynchronousSinusoidalAnalyzer;
import marytts.signalproc.sinusoidal.pitch.HnmPitchVoicingAnalyzer;
import marytts.signalproc.sinusoidal.pitch.VoicingAnalysisOutputData;
import marytts.signalproc.window.HammingWindow;
import marytts.signalproc.window.Window;
import marytts.util.MaryUtils;
import marytts.util.data.audio.AudioDoubleDataSource;
import marytts.util.io.FileUtils;
import marytts.util.math.ArrayUtils;
import marytts.util.math.ComplexArray;
import marytts.util.math.ComplexNumber;
import marytts.util.math.FFT;
import marytts.util.math.FFTMixedRadix;
import marytts.util.math.MathUtils;
/*
import marytts.util.math.jampack.H;
import marytts.util.math.jampack.Inv;
import marytts.util.math.jampack.JampackException;
import marytts.util.math.jampack.Parameters;
import marytts.util.math.jampack.Times;
import marytts.util.math.jampack.Z;
import marytts.util.math.jampack.Zmat;
*/
import marytts.util.signal.SignalProcUtils;
import marytts.util.string.StringUtils;

/**
 * This class implements a harmonic+noise model for speech as described in
 * Stylianou, Y., 1996, "Harmonic plus Noise Models for Speech, combined with Statistical Methods, 
 *                       for Speech and Speaker Modification", Ph.D. thesis, 
 *                       Ecole Nationale Supérieure des Télécommunications.
 * 
 * @author Oytun T&uumlrk
 *
 */
public class HntmAnalyzer {
    public static final int HARMONICS_PLUS_NOISE = 1;
    public static final int HARMONICS_PLUS_TRANSIENTS_PLUS_NOISE = 2;
    
    public static final int LPC = 1; //Noise part model based on LPC
    public static final int PSEUDO_HARMONIC = 2; //Noise part model based on pseude harmonics for f0=NOISE_F0_IN_HZ
    
    public static final double NOISE_F0_IN_HZ = 100.0; //Pseudo-pitch for unvoiced portions (will be used for pseudo harmonic modelling of the noise part)
    public static float FIXED_MAX_FREQ_OF_VOICING_FOR_QUICK_TEST = 3500.0f;
    public static float FIXED_MAX_FREQ_OF_NOISE_FOR_QUICK_TEST = 8000.0f;
    public static float HPF_TRANSITION_BANDWIDTH_IN_HZ = 100.0f;
    public static float NOISE_ANALYSIS_WINDOW_DURATION_IN_SECONDS = 0.050f; //Fixed window size for noise analysis, should be generally large (>=0.040 seconds)
    public static float OVERLAP_BETWEEN_HARMONIC_AND_NOISE_REGIONS_IN_HZ = 0.0f;
    public static float OVERLAP_BETWEEN_TRANSIENT_AND_NONTRANSIENT_REGIONS_IN_SECONDS = 0.005f;
    
    public HntmAnalyzer()
    {
        
    }
    
    public HntmSpeechSignal analyze(double[] x, int fs, F0ReaderWriter f0, Labels labels, 
                                    float windowSizeInSeconds, float skipSizeInSeconds, 
                                    int model, int noisePartRepresentation)
    {
        HntmSpeechSignal hnmSignal = null;
        
        float originalDurationInSeconds = SignalProcUtils.sample2time(x.length, fs);
        int lpOrder = SignalProcUtils.getLPOrder(fs);
        float preCoefNoise = 0.0f;

        //// TO DO
        //Step1. Initial pitch estimation: Current version just reads from a file
        if (f0!=null)
        {
            int pitchMarkOffset = 0;
            PitchMarks pm = SignalProcUtils.pitchContour2pitchMarks(f0.contour, fs, x.length, f0.header.ws, f0.header.ss, true, pitchMarkOffset);

            float[] initialF0s = ArrayUtils.subarrayf(f0.contour, 0, f0.header.numfrm);
            //float[] initialF0s = HnmPitchVoicingAnalyzer.estimateInitialPitch(x, samplingRate, windowSizeInSeconds, skipSizeInSeconds, f0MinInHz, f0MaxInHz, windowType);
            //

            //Step2: Do for each frame (at 10 ms skip rate):
            //2.a. Voiced/Unvoiced decision

            //2.b. If voiced, maximum frequency of voicing estimation
            //     Otherwise, maximum frequency of voicing is set to 0.0
            int fftSize = 4096;
            float[] maxFrequencyOfVoicings = HnmPitchVoicingAnalyzer.analyzeVoicings(x, fs, windowSizeInSeconds, skipSizeInSeconds, fftSize, initialF0s, (float)f0.header.ws, (float)f0.header.ss);
            float maxFreqOfVoicingInHz;
            //maxFreqOfVoicingInHz = HnmAnalyzer.FIXED_MAX_FREQ_OF_VOICING_FOR_QUICK_TEST; //This should come from the above automatic analysis

            //2.c. Refined pitch estimation
            float[] f0s = ArrayUtils.subarrayf(f0.contour, 0, f0.header.numfrm);
            //float[] f0s = HnmPitchVoicingAnalyzer.estimateRefinedPitch(fftSize, fs, leftNeighInHz, rightNeighInHz, searchStepInHz, initialF0s, maxFrequencyOfVoicings);
            ////


            //Step3. Determine analysis time instants based on refined pitch values.
            //       (Pitch synchronous if voiced, 10 ms skip if unvoiced)
            int windowType = Window.HAMMING;
            double numPeriods = 2.0;

            double f0InHz = f0s[0];
            int T0;
            double assumedF0ForUnvoicedInHz = 100.0;
            boolean isVoiced, isNoised;
            if (f0InHz>10.0)
                isVoiced=true;
            else
            {
                isVoiced=false;
                f0InHz=assumedF0ForUnvoicedInHz;
            }

            T0 = (int)Math.floor(fs/f0InHz+0.5);

            int i, j, k;

            int ws;
            int wsNoise = SignalProcUtils.time2sample(NOISE_ANALYSIS_WINDOW_DURATION_IN_SECONDS, fs);
            if (wsNoise%2==0) //Always use an odd window size to have a zero-phase analysis window
                wsNoise++;  

            Window winNoise = Window.get(windowType, wsNoise);
            winNoise.normalizePeakValue(1.0f);
            double[] wgtSquaredNoise = winNoise.getCoeffs();
            for (j=0; j<wgtSquaredNoise.length; j++)
                wgtSquaredNoise[j] = wgtSquaredNoise[j]*wgtSquaredNoise[j];

            int fftSizeNoise = SignalProcUtils.getDFTSize(fs);

            int totalFrm = (int)Math.floor(pm.pitchMarks.length-numPeriods+0.5);
            if (totalFrm>pm.pitchMarks.length-1)
                totalFrm = pm.pitchMarks.length-1;

            //Extract frames and analyze them
            double[] frm = null; //Extracted pitch synchronously
            double[] frmNoise = new double[wsNoise]; //Extracted at fixed window size around analysis time instant since LP analysis requires longer windows (40 ms)
            int noiseFrmStartInd;

            int pmInd = 0;

            boolean isOutputToTextFile = false;
            Window win;
            int closestInd;

            String[] transientPhonemesList = {"p", "t", "k", "pf", "ts", "tS"};

            if (model == HntmAnalyzer.HARMONICS_PLUS_NOISE)
                hnmSignal = new HntmSpeechSignal(totalFrm, fs, originalDurationInSeconds, (float)f0.header.ws, (float)f0.header.ss, NOISE_ANALYSIS_WINDOW_DURATION_IN_SECONDS, preCoefNoise);
            else if (model == HntmAnalyzer.HARMONICS_PLUS_TRANSIENTS_PLUS_NOISE && labels!=null)
                hnmSignal = new HntmPlusTransientsSpeechSignal(totalFrm, fs, originalDurationInSeconds, (float)f0.header.ws, (float)f0.header.ss, NOISE_ANALYSIS_WINDOW_DURATION_IN_SECONDS, preCoefNoise, labels.items.length);
            
            boolean isPrevVoiced = false;

            int numHarmonics = 0;
            int prevNumHarmonics = 0;
            ComplexNumber[] harmonicAmps = null;
            ComplexNumber[] noiseHarmonicAmps = null;

            double[] phases;
            double[] dPhases;
            double[] dPhasesPrev = null;
            int MValue;

            int cepsOrderHarmonic = 16;
            int cepsOrderNoise = 16;
            int numNoiseHarmonics = (int)Math.floor((0.5*fs)/NOISE_F0_IN_HZ+0.5);
            double[] freqsInHzNoise = new double [numNoiseHarmonics];
            for (j=0; j<numNoiseHarmonics; j++)
                freqsInHzNoise[j] = NOISE_F0_IN_HZ*(j+1);

            double[][] M = null;
            double[][] MTransW = null;
            double[][] MTransWM = null; 
            double[][] lambdaR = null;
            double[][] inverted = null;
            if (noisePartRepresentation==PSEUDO_HARMONIC)
            {
                M = RegularizedCepstralEnvelopeEstimator.precomputeM(freqsInHzNoise, fs, cepsOrderNoise);
                MTransW = RegularizedCepstralEnvelopeEstimator.precomputeMTransW(M, null);
                MTransWM = RegularizedCepstralEnvelopeEstimator.precomputeMTransWM(MTransW, M); 
                lambdaR = RegularizedCepstralEnvelopeEstimator.precomputeLambdaR(RegularizedCepstralEnvelopeEstimator.DEFAULT_LAMBDA, cepsOrderNoise);
                inverted = RegularizedCepstralEnvelopeEstimator.precomputeInverted(MTransWM, lambdaR);
            }
            
            int maxVoicingIndex;
            int currentLabInd = 0;
            boolean isInTransientSegment = false;
            int transientSegmentInd = 0;
            for (i=0; i<totalFrm; i++)
            {  
                f0InHz = pm.f0s[i];
                T0 = pm.pitchMarks[i+1]-pm.pitchMarks[i];
                ws = (int)Math.floor(numPeriods*T0+ 0.5);
                if (ws%2==0) //Always use an odd window size to have a zero-phase analysis window
                    ws++;            

                hnmSignal.frames[i].tAnalysisInSeconds = (float)((pm.pitchMarks[i]+0.5f*ws)/fs);  //Middle of analysis frame
                
                if (model == HntmAnalyzer.HARMONICS_PLUS_TRANSIENTS_PLUS_NOISE && labels!=null)
                {
                    while(labels.items[currentLabInd].time<hnmSignal.frames[i].tAnalysisInSeconds)
                    {
                        currentLabInd++;
                        if (currentLabInd>labels.items.length-1)
                        {
                            currentLabInd = labels.items.length-1;
                            break;
                        }
                    }
                    
                    if (!isInTransientSegment) //Perhaps start of a new transient segment
                    {
                        for (j=0; j<transientPhonemesList.length; j++)
                        {
                            if (labels.items[currentLabInd].phn.compareTo(transientPhonemesList[j])==0)
                            {
                                isInTransientSegment = true;
                                ((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments[transientSegmentInd] = new TransientSegment();
                                ((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments[transientSegmentInd].startTime = Math.max(0.0f, (((float)pm.pitchMarks[i])/fs)-HntmAnalyzer.OVERLAP_BETWEEN_TRANSIENT_AND_NONTRANSIENT_REGIONS_IN_SECONDS);
                                break;
                            }
                        }
                    }
                    else //Perhaps end of an existing transient segment
                    {
                        boolean isTransientPhoneme = false;
                        for (j=0; j<transientPhonemesList.length; j++)
                        {
                            if (labels.items[currentLabInd].phn.compareTo(transientPhonemesList[j])==0)
                            {
                                isTransientPhoneme = true;
                                break;
                            }
                        }
                        
                        if (!isTransientPhoneme) //End of transient segment, put it in transient part
                        {
                            
                            float endTime = Math.min((((float)pm.pitchMarks[i]+0.5f*ws)/fs)+HntmAnalyzer.OVERLAP_BETWEEN_TRANSIENT_AND_NONTRANSIENT_REGIONS_IN_SECONDS, hnmSignal.originalDurationInSeconds);
                            int waveformStartInd = Math.max(0, SignalProcUtils.time2sample(((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments[transientSegmentInd].startTime, fs));
                            int waveformEndInd = Math.min(x.length-1, SignalProcUtils.time2sample(endTime, fs));
                            if (waveformEndInd-waveformStartInd+1>0)
                            {
                                ((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments[transientSegmentInd].waveform = new int[waveformEndInd-waveformStartInd+1];
                                for (j=waveformStartInd; j<=waveformEndInd; j++)
                                    ((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments[transientSegmentInd].waveform[j-waveformStartInd] = (int)x[j];
                            }
                            
                            transientSegmentInd++;
                            isInTransientSegment = false;
                        }
                    }
                }

                maxVoicingIndex = SignalProcUtils.time2frameIndex(hnmSignal.frames[i].tAnalysisInSeconds, windowSizeInSeconds, skipSizeInSeconds);
                maxVoicingIndex = Math.min(maxVoicingIndex, maxFrequencyOfVoicings.length-1);
                maxFreqOfVoicingInHz = maxFrequencyOfVoicings[maxVoicingIndex];
                //if (hnmSignal.frames[i].tAnalysisInSeconds<0.7 && f0InHz>10.0)
                if (f0InHz>10.0)
                    hnmSignal.frames[i].maximumFrequencyOfVoicingInHz = maxFreqOfVoicingInHz; //Normally, this should come from analysis!!!
                else
                    hnmSignal.frames[i].maximumFrequencyOfVoicingInHz = 0.0f;

                isVoiced = hnmSignal.frames[i].maximumFrequencyOfVoicingInHz>0.0 ? true:false;
                isNoised = hnmSignal.frames[i].maximumFrequencyOfVoicingInHz<0.5*fs ? true:false;
                
                if (isInTransientSegment)
                {
                    hnmSignal.frames[i].h = null;
                    hnmSignal.frames[i].n = null;
                }
                else
                {
                    if (!isVoiced)
                        f0InHz = assumedF0ForUnvoicedInHz;

                    T0 = SignalProcUtils.time2sample(1.0/f0InHz, fs);

                    ws = (int)Math.floor(numPeriods*T0+ 0.5);
                    if (ws%2==0) //Always use an odd window size to have a zero-phase analysis window
                        ws++;

                    frm = new double[ws];
                    Arrays.fill(frm, 0.0);

                    for (j=pm.pitchMarks[i]; j<Math.min(pm.pitchMarks[i]+ws-1, x.length); j++)
                        frm[j-pm.pitchMarks[i]] = x[j];

                    win = Window.get(windowType, ws);
                    //win.normalize(1.0f);
                    double[] wgt = win.getCoeffs();
                    //double[] wgtSquared = new double[wgt.length];
                    //for (j=0; j<wgt.length; j++)
                    //    wgtSquared[j] = wgt[j]*wgt[j];

                    //Step4. Estimate complex amplitudes of harmonics if voiced
                    //       The phase of the complex amplitude is the phase and its magnitude is the absolute amplitude of the harmonic
                    if (isVoiced)
                    {
                        numHarmonics = (int)Math.floor(hnmSignal.frames[i].maximumFrequencyOfVoicingInHz/f0InHz+0.5);
                        
                        harmonicAmps = estimateComplexAmplitudes(frm, wgt, f0InHz, numHarmonics, fs, hnmSignal.frames[i].tAnalysisInSeconds);
                        
                        /*
                        //Jampack version for matrix operations
                        try {
                            harmonicAmps = estimateComplexAmplitudesJampack(frm, wgt, f0InHz, numHarmonics, fs, hnmSignal.frames[i].tAnalysisInSeconds);
                        } catch (JampackException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        //
                        */
                        
                        //harmonicAmps = estimateComplexAmplitudesUncorrelated(frm, wgtSquared, numHarmonics, f0InHz, fs);

                        //Only for visualization
                        //double[] absMags = MathUtils.magnitudeComplex(harmonicAmps);
                        //double[] dbMags = MathUtils.amp2db(absMags);
                        //MaryUtils.plot(dbMags);
                        //

                        hnmSignal.frames[i].f0InHz = (float)f0InHz;
                        hnmSignal.frames[i].h = new FrameHarmonicPart();
                    }
                    else
                        numHarmonics = 0;

                    //Step5. Perform full-spectrum LPC analysis for generating noise part
                    Arrays.fill(frmNoise, 0.0);
                    noiseFrmStartInd = Math.max(0, SignalProcUtils.time2sample(hnmSignal.frames[i].tAnalysisInSeconds-0.5f*NOISE_ANALYSIS_WINDOW_DURATION_IN_SECONDS, fs));
                    for (j=noiseFrmStartInd; j<Math.min(noiseFrmStartInd+wsNoise, x.length); j++)
                        frmNoise[j-noiseFrmStartInd] = x[j];

                    if (isNoised)
                    {
                        if (noisePartRepresentation==LPC)
                        {
                            double origStd = MathUtils.standardDeviation(frmNoise);

                            //We have support for preemphasis - this needs to be handled during synthesis of the noisy part with preemphasis removal
                            frmNoise = winNoise.apply(frmNoise, 0);

                            //SignalProcUtils.displayDFTSpectrumInDBNoWindowing(frmNoise, fftSizeNoise);  

                            if (hnmSignal.frames[i].maximumFrequencyOfVoicingInHz-OVERLAP_BETWEEN_HARMONIC_AND_NOISE_REGIONS_IN_HZ>0.0f)
                            {
                                frmNoise = SignalProcUtils.fdFilter(frmNoise, hnmSignal.frames[i].maximumFrequencyOfVoicingInHz, 0.5f*fs, fs, fftSizeNoise);
                                //HighPassFilter hpf = new HighPassFilter((hnmSignal.frames[i].maximumFrequencyOfVoicingInHz-OVERLAP_BETWEEN_HARMONIC_AND_NOISE_REGIONS_IN_HZ)/fs, HnmAnalyzer.HPF_TRANSITION_BANDWIDTH_IN_HZ/fs);
                                //frmNoise = hpf.apply(frmNoise);
                            }

                            //Only for display purposes...
                            //SignalProcUtils.displayDFTSpectrumInDBNoWindowing(frmNoise, fftSizeNoise); 

                            LpCoeffs lpcs = LpcAnalyser.calcLPC(frmNoise, lpOrder, preCoefNoise);
                            hnmSignal.frames[i].n = new FrameNoisePartLpc(lpcs.getA(), lpcs.getGain());
                            if (Double.isNaN(lpcs.getGain()))
                                System.out.println("NaN in analysis!!!");

                            //hnmSignal.frames[i].n = new FrameNoisePartLpc(lpcs.getA(), origStd);

                            //Only for display purposes...
                            //SignalProcUtils.displayLPSpectrumInDB(((FrameNoisePartLpc)hnmSignal.frames[i].n).lpCoeffs, ((FrameNoisePartLpc)hnmSignal.frames[i].n).gain, fftSizeNoise);
                        }
                        else if (noisePartRepresentation==PSEUDO_HARMONIC)
                        {
                            //Note that for noise we use the uncorrelated version of the complex amplitude estimator
                            //Correlated version resulted in ill-conditioning
                            //Also, analysis was pretty slow since the number of harmonics is large for pseudo-harmonics of noise, 
                            //i.e. for 16 KHz 5 to 8 KHz bandwidth in steps of 100 Hz produces 50 to 80 pseudo-harmonics

                            //(1) Uncorrelated approach as in Stylianou´s thesis
                            noiseHarmonicAmps = estimateComplexAmplitudesUncorrelated(frmNoise, wgtSquaredNoise, numNoiseHarmonics, NOISE_F0_IN_HZ, fs);
                            //OR... (2)Expensive approach which does not work very well
                            //noiseHarmonicAmps = estimateComplexAmplitudes(frm, wgt, numNoiseHarmonics, NOISE_F0_IN_HZ, fs);
                            //OR... (3) Uncorrelated approach using full autocorrelation matrix (checking if there is a problem in estimateComplexAmplitudesUncorrelated
                            //noiseHarmonicAmps = estimateComplexAmplitudesUncorrelated2(frm, wgtSquared, numNoiseHarmonics, NOISE_F0_IN_HZ, fs);

                            double[] linearAmpsNoise = new double[numNoiseHarmonics];
                            for (j=0; j<numNoiseHarmonics; j++)
                                linearAmpsNoise[j] = MathUtils.magnitudeComplex(noiseHarmonicAmps[j]);

                            double[] vocalTractDB = MathUtils.amp2db(linearAmpsNoise);
                            //MaryUtils.plot(vocalTractDB);

                            hnmSignal.frames[i].n = new FrameNoisePartPseudoHarmonic();
                            //(1) This is how amplitudes are represented in Stylianou´s thesis
                            ((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i].n).ceps = RegularizedCepstralEnvelopeEstimator.freqsLinearAmps2cepstrum(linearAmpsNoise, MTransW, inverted);
                            //OR... (2) The following is the expensive approach in which all matrices are computed again and again
                            //((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i].n).ceps = RegularizedCepstralEnvelopeEstimator.freqsLinearAmps2cepstrum(linearAmpsNoise, freqsInHzNoise, fs, cepsOrderNoise);
                            //OR... (3) Let´s try to copy linearAmps as they are with no cepstral processing to see if synthesis works OK:
                            //((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i].n).ceps = new double[numNoiseHarmonics];
                            //System.arraycopy(linearAmpsNoise, 0, ((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i].n).ceps, 0, numNoiseHarmonics);


                            /*
                            //The following is only for visualization
                            //int fftSize = 4096;
                            //double[] vocalTractDB = RegularizedCepstralEnvelopeEstimator.cepstrum2logAmpHalfSpectrum(((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i].n).ceps, fftSize, fs);
                            double[] vocalTractDB = new double[numNoiseHarmonics];
                            for (j=0; j<numNoiseHarmonics; j++)
                                vocalTractDB[j] = RegularizedCepstralEnvelopeEstimator.cepstrum2linearSpectrumValue(((FrameNoisePartPseudoHarmonic)hnmSignal.frames[i].n).ceps, (j+1)*HnmAnalyzer.NOISE_F0_IN_HZ, fs);
                            vocalTractDB = MathUtils.amp2db(vocalTractDB);
                            MaryUtils.plot(vocalTractDB);
                            //
                             */    
                            }
                        }
                        else
                            hnmSignal.frames[i].n = null;
                        //

                    //Step6. Estimate amplitude envelopes
                    if (numHarmonics>0)
                    {
                        if (isVoiced)
                        {
                            double[] linearAmps = new double[numHarmonics];
                            double[] freqsInHz = new double [numHarmonics];
                            for (j=0; j<numHarmonics; j++)
                            {
                                linearAmps[j] = MathUtils.magnitudeComplex(harmonicAmps[j]);
                                freqsInHz[j] = f0InHz*(j+1);
                            }

                            hnmSignal.frames[i].h.ceps = RegularizedCepstralEnvelopeEstimator.freqsLinearAmps2cepstrum(linearAmps, freqsInHz, fs, cepsOrderHarmonic);
                            //hnmSignal.frames[i].h.ceps = ArrayUtils.subarray(linearAmps, 0, linearAmps.length); //Use amplitudes directly

                            //The following is only for visualization
                            //int fftSize = 4096;
                            //double[] vocalTractDB = RegularizedCepstralEnvelopeEstimator.cepstrum2logAmpHalfSpectrum(hnmFrames[i].ceps , fftSize, fs);
                            //MaryUtils.plot(vocalTractDB);
                            //
                        }
                        //

                        hnmSignal.frames[i].h.phases = new float[numHarmonics];
                        for (k=0; k<numHarmonics; k++)
                            hnmSignal.frames[i].h.phases[numHarmonics-k-1] = (float)MathUtils.phaseInRadians(harmonicAmps[numHarmonics-k-1]);
                    }
                }

                if (isVoiced && !isInTransientSegment)
                    isPrevVoiced = true;
                else
                {
                    prevNumHarmonics = 0;
                    isPrevVoiced = false;
                }

                System.out.println("Analysis complete at " + String.valueOf(hnmSignal.frames[i].tAnalysisInSeconds) + "s. for frame " + String.valueOf(i+1) + " of " + String.valueOf(totalFrm)); 
             }
        }
        
        if (hnmSignal instanceof HntmPlusTransientsSpeechSignal)
        {
            int i;
            int numTransientSegments = 0;
            for (i=0; i<((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments.length; i++)
            {
                if (((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments[i]!=null)
                    numTransientSegments++;
            }
            
            if (numTransientSegments>0)
            {
                TransientPart tempPart = new TransientPart(numTransientSegments);
                int count = 0;
                for (i=0; i<((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments.length; i++)
                {
                    if (((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments[i]!=null)
                    {
                        tempPart.segments[count++] = new TransientSegment(((HntmPlusTransientsSpeechSignal)hnmSignal).transients.segments[i]);
                        if (count>=numTransientSegments)
                            break;
                    }
                }
                
                ((HntmPlusTransientsSpeechSignal)hnmSignal).transients = new TransientPart(tempPart);
            }
            else
                ((HntmPlusTransientsSpeechSignal)hnmSignal).transients = null;
        }
        
        return hnmSignal;
    }
    
    //Complex amplitude estimation for harmonics in time domain 
    //(Full correlation matrix approach, no independence between harmonics assumed)
    //  This function implements Equation 3.25 in Stylianou`s PhD thesis
    //  The main advantage is the operation being in time domain.
    //  Therefore, we can use window sizes as short as two pitch periods and track rapid changes in amplitudes and phases
    //  frm: speech frame to be analysed (its length should be 2*N+1)
    //  wgtSquared: window weights squared
    //  f0InHz: f0 value for the current frame in Hz
    //  L: number of harmonics
    //  samplingRateInHz: sampling rate in Hz
    /*
    public ComplexNumber[] estimateComplexAmplitudes(double[] frm, double[] wgtSquared, double f0InHz, int L, double samplingRateInHz)
    {
        int M = frm.length;
        assert M % 2==1; //Frame length should be odd
        int N = (M-1)/2;
        
        ComplexNumber[][] R = new ComplexNumber[2*L+1][2*L+1];
        ComplexNumber[] b = new ComplexNumber[2*L+1];
        ComplexNumber tmp;
        
        int t, i, k;
        double omega;

        for (i=1; i<=2*L+1; i++)
        {
            for (k=1; k<=2*L+1; k++)
            {
                R[i-1][k-1] = new ComplexNumber(0.0, 0.0);
                for (t=-N; t<=N; t++)
                {
                    omega = MathUtils.TWOPI*f0InHz*t/samplingRateInHz*(k-i);
                    tmp = new ComplexNumber(wgtSquared[t+N]*Math.cos(omega), wgtSquared[t+N]*Math.sin(omega));
                    R[i-1][k-1] = MathUtils.addComplex(R[i-1][k-1], tmp);
                }
            }
        }   
        
        for (k=1; k<=2*L+1; k++)
        {
            b[k-1] = new ComplexNumber(0.0, 0.0);
            for (t=-N; t<=N; t++)
            {
                omega = MathUtils.TWOPI*f0InHz*t/samplingRateInHz*(L+1-k);
                tmp = new ComplexNumber(wgtSquared[t+N]*frm[t+N]*Math.cos(omega), wgtSquared[t+N]*frm[t+N]*Math.sin(omega));
                b[k-1] = MathUtils.addComplex(b[k-1], tmp);
            }
        }

        ComplexNumber[][] invR = MathUtils.inverse(R);
        
       
        //Check matrix inversion operation
        //ComplexNumber[][] RinvR = MathUtils.matrixProduct(R, invR);
        //for (i=0; i<RinvR.length; i++)
        //{
        //    for (k=0; k<RinvR[i].length; k++)
        //    {
        //        if (i!=k && MathUtils.magnitudeComplex(RinvR[i][k])>1e-10)
        //            System.out.println("Check here! Non-zero non-diagonal element detected!");
        //        if (i==k && Math.abs(MathUtils.magnitudeComplex(RinvR[i][k])-1.0)>1e-10)
        //            System.out.println("Check here! Non-unity diagonal element detected!");
        //    }
        //}
        //
        
        //
        ComplexNumber[] x = MathUtils.matrixProduct(invR, b);
        ComplexNumber[] xpart = new ComplexNumber[L+1];
        for (k=L+1; k<2*L+1; k++) //The remaning complex amplitudes from L+1 to 2L are complex conjugates of entries from L-1,...,1
            xpart[k-(L+1)] = new ComplexNumber(x[k]);
        
        return xpart;
    }
    */
    
    public ComplexNumber[] estimateComplexAmplitudes(double[] s, double[] wgt, double f0InHz, int L, double samplingRateInHz, float tAnalysis)
    {
        int M = s.length;
        assert M % 2==1; //Frame length should be odd
        int N = (M-1)/2;
        
        ComplexNumber[][] B = new ComplexNumber[2*N+1][2*L+1];
        ComplexNumber tmp;
        
        int t, i, k;
        double omega;

        ComplexNumber[][] W = MathUtils.diagonalComplexMatrix(wgt);
        for (t=-N; t<=N; t++)
        {
            for (k=-L; k<=L; k++)
            {
                //omega = MathUtils.TWOPI*k*f0InHz*(tAnalysis+t/samplingRateInHz);
                omega = MathUtils.TWOPI*k*f0InHz*(t/samplingRateInHz);
                B[t+N][k+L] = new ComplexNumber(Math.cos(omega), Math.sin(omega));
            }
        }
        ComplexNumber[][] BTWTW = MathUtils.matrixProduct(MathUtils.hermitianTranspoze(B), MathUtils.transpoze(W));
        BTWTW = MathUtils.matrixProduct(BTWTW, W);
        
        ComplexNumber[] b = MathUtils.matrixProduct(BTWTW, s);
        
        //ComplexNumber[][] R = MathUtils.matrixProduct(BTWTW, B);
        
        ComplexNumber[][] R = new ComplexNumber[2*L+1][2*L+1];
        for (i=1; i<=2*L+1; i++)
        {
            for (k=1; k<=2*L+1; k++)
            {
                R[i-1][k-1] = new ComplexNumber(0.0, 0.0);
                for (t=-N; t<=N; t++)
                {
                    //omega = MathUtils.TWOPI*f0InHz*(tAnalysis+t/samplingRateInHz)*(i-k);
                    omega = MathUtils.TWOPI*f0InHz*(t/samplingRateInHz)*(i-k);
                    tmp = new ComplexNumber(wgt[t+N]*wgt[t+N]*Math.cos(omega), wgt[t+N]*wgt[t+N]*Math.sin(omega));
                    R[i-1][k-1] = MathUtils.addComplex(R[i-1][k-1], tmp);
                }
            }
        } 
        
        ComplexNumber[][] invR = MathUtils.inverse(R);

        //Check matrix inversion operation
        ComplexNumber[][] RinvR = MathUtils.matrixProduct(R, invR);
        for (i=0; i<RinvR.length; i++)
        {
            for (k=0; k<RinvR[i].length; k++)
            {
                if (i!=k && MathUtils.magnitudeComplex(RinvR[i][k])>1e-10)
                    System.out.println("Check here! Non-zero non-diagonal element detected!");
                if (i==k && Math.abs(MathUtils.magnitudeComplex(RinvR[i][k])-1.0)>1e-10)
                    System.out.println("Check here! Non-unity diagonal element detected!");
            }
        }
        //
        
        //
        ComplexNumber[] x = MathUtils.matrixProduct(invR, b);
        ComplexNumber[] xpart = new ComplexNumber[L];
        
        for (k=L-1; k>=0; k--) //The remaning complex amplitudes from L+1 to 2L are complex conjugates of entries from L-1,...,0
            xpart[L-1-k] = new ComplexNumber(x[k].real, x[k].imag);
        
        double gain = MathUtils.absMax(s)/MathUtils.sum(MathUtils.abs(xpart));
        
        for (k=0; k<xpart.length; k++)
            xpart[k] = MathUtils.multiply(gain, xpart[k]);
        
        return xpart;
    }
    
    /*
    public ComplexNumber[] estimateComplexAmplitudesJampack(double[] s, double[] wgt, double f0InHz, int L, double samplingRateInHz, float tAnalysis) throws JampackException
    {
        if (Parameters.getBaseIndex()!=0)
            Parameters.setBaseIndex(0);
        
        int M = s.length;
        assert M % 2==1; //Frame length should be odd
        int N = (M-1)/2;
        
        //ComplexNumber[][] B = new ComplexNumber[2*N+1][2*L+1];
        Zmat B = new Zmat(2*N+1, 2*L+1);
        //ComplexNumber tmp;
        Z tmp;
        
        int t, i, k;
        double omega;

        //ComplexNumber[][] W = MathUtils.diagonalComplexMatrix(wgt);
        Zmat W = new Zmat(wgt.length, wgt.length);
        for (t=0; t<wgt.length; t++)
            W.put(t,t,new Z(wgt[t], 0.0));
        
        for (t=-N; t<=N; t++)
        {
            for (k=-L; k<=L; k++)
            {
                //omega = MathUtils.TWOPI*k*f0InHz*(tAnalysis+t/samplingRateInHz);
                omega = MathUtils.TWOPI*k*f0InHz*(t/samplingRateInHz);
                //B[t+N][k+L] = new ComplexNumber(Math.cos(omega), Math.sin(omega));
                B.put(t+N, k+L, new Z(Math.cos(omega), Math.sin(omega)));
            }
        }
        //ComplexNumber[][] BTWTW = MathUtils.matrixProduct(MathUtils.transpoze(B), MathUtils.transpoze(W));
        //BTWTW = MathUtils.matrixProduct(BTWTW, W);
        Zmat BTWTW = Times.o(H.o(B), H.o(W));
        BTWTW = Times.o(BTWTW, W);
        
        //ComplexNumber[] b = MathUtils.matrixProduct(BTWTW, s);
        Zmat S = new Zmat(s.length, 1);
        for (t=0; t<s.length; t++)
            S.put(t, 0, new Z(s[t], 0.0));
        Zmat b = Times.o(BTWTW, S);
        
        //ComplexNumber[][] R = MathUtils.matrixProduct(BTWTW, B);
        
        //ComplexNumber[][] R = new ComplexNumber[2*L+1][2*L+1];
        Zmat R = new Zmat(2*L+1, 2*L+1);
        for (i=1; i<=2*L+1; i++)
        {
            for (k=1; k<=2*L+1; k++)
            {
                //R[i-1][k-1] = new ComplexNumber(0.0, 0.0);
                R.put(i-1, k-1, new Z(0.0, 0.0));
                for (t=-N; t<=N; t++)
                {
                    //omega = MathUtils.TWOPI*f0InHz*(tAnalysis+t/samplingRateInHz)*(i-k);
                    omega = MathUtils.TWOPI*f0InHz*(t/samplingRateInHz)*(i-k);
                    //tmp = new ComplexNumber(wgt[t+N]*wgt[t+N]*Math.cos(omega), wgt[t+N]*wgt[t+N]*Math.sin(omega));
                    tmp = new Z(wgt[t+N]*wgt[t+N]*Math.cos(omega), wgt[t+N]*wgt[t+N]*Math.sin(omega));
                    //R[i-1][k-1] = MathUtils.addComplex(R[i-1][k-1], tmp);
                    R.put(i-1, k-1, new Z(R.get(i-1, k-1).re+tmp.re, R.get(i-1, k-1).im+tmp.im));
                }
            }
        } 
        
        //ComplexNumber[][] invR = MathUtils.inverse(R);
        Zmat invR = Inv.o(R);

        //Check matrix inversion operation
        //ComplexNumber[][] RinvR = MathUtils.matrixProduct(R, invR);
        Zmat RinvR = Times.o(R, invR);
        //for (i=0; i<RinvR.length; i++)
        for (i=0; i<RinvR.nr; i++)
        {
            //for (k=0; k<RinvR[i].length; k++)
            for (k=0; k<RinvR.nc; k++)
            {
                //if (i!=k && MathUtils.magnitudeComplex(RinvR[i][k])>1e-10)
                if (i!=k && MathUtils.magnitudeComplex(RinvR.get(i, k).re, RinvR.get(i, k).im)>1e-10)
                    System.out.println("Check here! Non-zero non-diagonal element detected!");
                //if (i==k && Math.abs(MathUtils.magnitudeComplex(RinvR[i][k])-1.0)>1e-10)
                if (i==k && Math.abs(MathUtils.magnitudeComplex(RinvR.get(i, k).re, RinvR.get(i, k).im)-1.0)>1e-10)
                    System.out.println("Check here! Non-unity diagonal element detected!");
            }
        }
        //
        
        //
        //ComplexNumber[] x = MathUtils.matrixProduct(invR, b);
        Zmat x = Times.o(invR, b);
        ComplexNumber[] xpart = new ComplexNumber[L];
        
        for (k=L-1; k>=0; k--) //The remaning complex amplitudes from L+1 to 2L are complex conjugates of entries from L-1,...,0
        {
            //xpart[L-1-k] = new ComplexNumber(x[k].real, x[k].imag);
            xpart[L-1-k] = new ComplexNumber(x.get(k,0).re, x.get(k,0).im);
        }
        
        //double gain = MathUtils.absMax(s)/MathUtils.sum(MathUtils.abs(xpart));
        //for (k=0; k<xpart.length; k++)
            //xpart[k] = MathUtils.multiply(gain, xpart[k]);

        return xpart;
    }
    */
    
    //Complex amplitude estimation for harmonics in time domain (Diagonal correlation matrix approach, harmonics assumed independent)
    //The main advantage is the operation being in time domain.
    //Therefore, we can use window sizes as short as two pitch periods and track rapid changes in amplitudes and phases
    //N: local pitch period in samples
    //wgtSquared: window weights squared
    //frm: speech frame to be analysed (its length should be 2*N+1)
    //Uses Equation 3.32 in Stylianou`s thesis
    //This requires harmonics to be uncorrelated.
    //We use this for estimating pseudo-harmonic amplitudes of the noise part.
    //Note that this function is equivalent to peak-picking in the frequency domain in Quatieri´s sinusoidal framework.
    public ComplexNumber[] estimateComplexAmplitudesUncorrelated(double[] frm, double[] wgtSquared, int L, double f0InHz, double samplingRateInHz)
    {
        int M = frm.length;
        assert M % 2==1; //Frame length should be odd
        int N = (M-1)/2;
        
        ComplexNumber tmp;
        
        int t, k;
        double omega;
        
        double denum = 0.0;
        for (t=-N; t<=N; t++)
            denum += wgtSquared[t+N];
        
        ComplexNumber[] Ak = new ComplexNumber[L];
        for (k=1; k<=L; k++)
        {
            Ak[k-1] = new ComplexNumber(0.0, 0.0);
            for (t=-N; t<=N; t++)
            {
                omega = -1.0*MathUtils.TWOPI*k*f0InHz*((double)t/samplingRateInHz);
                tmp = new ComplexNumber(wgtSquared[t+N]*frm[t+N]*Math.cos(omega), wgtSquared[t+N]*frm[t+N]*Math.sin(omega));
                Ak[k-1] = MathUtils.addComplex(Ak[k-1], tmp);
            }
            Ak[k-1] = MathUtils.divide(Ak[k-1], denum);
        }
        
        return Ak;
    }
    
    //This is just for testing the full autocorrelation algorithm with diagonal autocorrelation matrix. It produced the same result using estimateComplexAmplitudesUncorrelated2
    public ComplexNumber[] estimateComplexAmplitudesUncorrelated2(double[] frm, double[] wgtSquared, int L, double f0InHz, double samplingRateInHz)
    {
        int M = frm.length;
        assert M % 2==1; //Frame length should be odd
        int N = (M-1)/2;
        
        ComplexNumber[][] R = new ComplexNumber[2*L+1][2*L+1];
        ComplexNumber[] b = new ComplexNumber[2*L+1];
        ComplexNumber tmp;
        
        int t, i, k;
        double omega;

        for (i=1; i<=2*L+1; i++)
        {
            for (k=1; k<=2*L+1; k++)
            {
                R[i-1][k-1] = new ComplexNumber(0.0, 0.0);
                if (i==k)
                {
                    for (t=-N; t<=N; t++)
                    {
                        omega = MathUtils.TWOPI*f0InHz*t/samplingRateInHz*(k-i);
                        tmp = new ComplexNumber(wgtSquared[t+N]*Math.cos(omega), wgtSquared[t+N]*Math.sin(omega));
                        R[i-1][k-1] = MathUtils.addComplex(R[i-1][k-1], tmp);
                    }
                }
            }
        }   
        
        for (k=1; k<=2*L+1; k++)
        {
            b[k-1] = new ComplexNumber(0.0, 0.0);
            for (t=-N; t<=N; t++)
            {
                omega = MathUtils.TWOPI*f0InHz*t/samplingRateInHz*(L+1-k);
                tmp = new ComplexNumber(wgtSquared[t+N]*frm[t+N]*Math.cos(omega), wgtSquared[t+N]*frm[t+N]*Math.sin(omega));
                b[k-1] = MathUtils.addComplex(b[k-1], tmp);
            }
        }
        
        ComplexNumber[] x = MathUtils.matrixProduct(MathUtils.inverse(R), b);
        ComplexNumber[] xpart = new ComplexNumber[L+1];
        for (k=L+1; k<2*L+1; k++) //The remaning complex amplitudes from L+1 to 2L are complex conjugates of entries from L-1,...,1
            xpart[k-(L+1)] = new ComplexNumber(x[k]);
        
        return xpart;
    }
}


