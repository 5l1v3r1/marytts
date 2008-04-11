package de.dfki.lt.mary.unitselection.adaptation.gmm.jointgmm;

import java.io.IOException;

import javax.sound.sampled.UnsupportedAudioFileException;

import de.dfki.lt.mary.unitselection.adaptation.BaselineAdaptationItem;
import de.dfki.lt.mary.unitselection.adaptation.BaselineAdaptationSet;
import de.dfki.lt.mary.unitselection.adaptation.BaselineFeatureExtractor;
import de.dfki.lt.mary.unitselection.adaptation.BaselinePostprocessor;
import de.dfki.lt.mary.unitselection.adaptation.BaselinePreprocessor;
import de.dfki.lt.mary.unitselection.adaptation.BaselineTransformer;
import de.dfki.lt.mary.unitselection.adaptation.FdpsolaAdapter;
import de.dfki.lt.mary.unitselection.adaptation.prosody.PitchMapping;
import de.dfki.lt.mary.unitselection.adaptation.prosody.PitchMappingFile;
import de.dfki.lt.mary.unitselection.adaptation.prosody.PitchStatistics;
import de.dfki.lt.mary.unitselection.adaptation.prosody.PitchTransformationData;
import de.dfki.lt.mary.unitselection.adaptation.prosody.ProsodyTransformerParams;
import de.dfki.lt.mary.unitselection.adaptation.smoothing.SmoothingDefinitions;
import de.dfki.lt.mary.unitselection.voiceimport.BasenameList;
import de.dfki.lt.mary.util.FileUtils;
import de.dfki.lt.mary.util.StringUtil;
import de.dfki.lt.signalproc.analysis.LsfFileHeader;

public class JointGMMTransformer extends BaselineTransformer {

    public JointGMMTransformerParams params;
    
    public JointGMMMapper mapper;
    public JointGMM jointGmm;
    
    private PitchMappingFile pitchMappingFile;
    public PitchMapping pitchMapping;

    public JointGMMTransformer(BaselinePreprocessor pp,
            BaselineFeatureExtractor fe,
            BaselinePostprocessor po,
            JointGMMTransformerParams pa) 
    {
        super(pp, fe, po, pa);
        params = new JointGMMTransformerParams(pa);
        
        jointGmm = null;
        mapper = null;
    }
    
    public boolean checkParams() throws IOException
    {
        params.inputFolder = StringUtil.checkLastSlash(params.inputFolder);
        params.outputBaseFolder = StringUtil.checkLastSlash(params.outputBaseFolder);
        
        //Read joint GMM file
        if (!FileUtils.exists(params.jointGmmFile))
        {
            System.out.println("Error: Codebook file " + params.jointGmmFile + " not found!");
            return false;     
        }
        else //Read full GMM from the joint GMM file
        {
            jointGmm = new JointGMM(params.jointGmmFile);
            
            params.lsfParams = new LsfFileHeader(jointGmm.lsfParams);
            //params.mapperParams.lpOrder = params.lsfParams.lpOrder;
        }
        //
        
        //Read pitch mapping file
        if (!FileUtils.exists(params.pitchMappingFile))
        {
            System.out.println("Error: Pitch mapping file " + params.pitchMappingFile + " not found!");
            return false;     
        }
        else //Read lsfParams from the codebook header
        {
            pitchMappingFile = new PitchMappingFile(params.pitchMappingFile, PitchMappingFile.OPEN_FOR_READ);
            pitchMapping = new PitchMapping();
            
            pitchMapping.header = pitchMappingFile.readPitchMappingHeader();
        }
        //
            
        if (!FileUtils.exists(params.inputFolder) || !FileUtils.isDirectory(params.inputFolder))
        {
            System.out.println("Error: Input folder " + params.inputFolder + " not found!");
            return false; 
        }
        
        if (!FileUtils.isDirectory(params.outputBaseFolder))
        {
            System.out.println("Creating output base folder " + params.outputBaseFolder + "...");
            FileUtils.createDirectory(params.outputBaseFolder);
        }
        
        if (params.outputFolderInfoString!="")
        {
            params.outputFolder = params.outputBaseFolder + params.outputFolderInfoString + 
                                  "_mixes" + String.valueOf(jointGmm.source.totalComponents) + 
                                  "_prosody" + String.valueOf(params.prosodyParams.pitchStatisticsType) + "x" + String.valueOf(params.prosodyParams.pitchTransformationMethod);
        }
        else
        {
            params.outputFolder = params.outputBaseFolder + 
                                  "_mixes" + String.valueOf(jointGmm.source.totalComponents) + 
                                  "_prosody" + String.valueOf(params.prosodyParams.pitchStatisticsType) + "x" + String.valueOf(params.prosodyParams.pitchTransformationMethod);
        }
            
        if (!FileUtils.isDirectory(params.outputFolder))
        {
            System.out.println("Creating output folder " + params.outputFolder + "...");
            FileUtils.createDirectory(params.outputFolder);
        }
        
        return true;
    }
    
    public void run() throws IOException, UnsupportedAudioFileException
    {
        if (checkParams())
        {
            BaselineAdaptationSet inputSet = getInputSet(params.inputFolder);
            if (inputSet==null)
                System.out.println("No input files found in " + params.inputFolder);
            else
            {
                BaselineAdaptationSet outputSet = getOutputSet(inputSet, params.outputFolder);

                transform(inputSet, outputSet);
            }
        }
    }
    
  //Create list of input files
    public BaselineAdaptationSet getInputSet(String inputFolder)
    {   
        BasenameList b = new BasenameList(inputFolder, BaselineAdaptationSet.DEFAULT_WAV_EXTENSION);
        
        BaselineAdaptationSet inputSet = new BaselineAdaptationSet(b.getListAsVector().size());
        
        for (int i=0; i<inputSet.items.length; i++)
            inputSet.items[i].setFromWavFilename(inputFolder + b.getName(i) + BaselineAdaptationSet.DEFAULT_WAV_EXTENSION);
        
        return inputSet;
    }
    //
    
    //Create list of output files using input set
    public BaselineAdaptationSet getOutputSet(BaselineAdaptationSet inputSet, String outputFolder)
    {   
        BaselineAdaptationSet outputSet  = null;

        outputFolder = StringUtil.checkLastSlash(outputFolder);
        
        if (inputSet!=null && inputSet.items!=null)
        {
            outputSet = new BaselineAdaptationSet(inputSet.items.length);

            for (int i=0; i<inputSet.items.length; i++)
                outputSet.items[i].audioFile = outputFolder + StringUtil.getFileName(inputSet.items[i].audioFile) + "_output" + BaselineAdaptationSet.DEFAULT_WAV_EXTENSION;
        }

        return outputSet;
    }
    //
    
    public void transform(BaselineAdaptationSet inputSet, BaselineAdaptationSet outputSet) throws UnsupportedAudioFileException
    {
        System.out.println("Transformation started...");
        
        if (inputSet.items!=null && outputSet.items!=null)
        {
            int numItems = Math.min(inputSet.items.length, outputSet.items.length);
            
            if (numItems>0)
            {
                preprocessor.run(inputSet);
                
                int desiredFeatures = BaselineFeatureExtractor.F0_FEATURES;
                
                try {
                    featureExtractor.run(inputSet, params, desiredFeatures);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            
            //Read the pitch mapping file
            pitchMappingFile.readPitchMappingFileExcludingHeader(pitchMapping);
            
            //Create a mapper object
            mapper = new JointGMMMapper();
            
            //Do the transformations now
            for (int i=0; i<numItems; i++)
            {
                try {
                    transformOneItem(inputSet.items[i], outputSet.items[i], params, mapper, jointGmm, pitchMapping);
                } catch (UnsupportedAudioFileException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                
                System.out.println("Transformed file " + String.valueOf(i+1) + " of "+ String.valueOf(numItems));
            }
        } 
        
        System.out.println("Transformation completed...");
    }
    
    //This function performs the actual voice conversion
    public static void transformOneItem(BaselineAdaptationItem inputItem, 
                                        BaselineAdaptationItem outputItem,
                                        JointGMMTransformerParams wctParams,
                                        JointGMMMapper jgMapper,
                                        JointGMM wCodebook,
                                        PitchTransformationData pMap
                                       ) throws UnsupportedAudioFileException, IOException
    {   
        if (wctParams.isFixedRateVocalTractConversion)
            wctParams.isSeparateProsody = true;

        //Desired values should be specified in the following four parameters
        double [] pscales = {1.0};
        double [] tscales = {1.0};
        double [] escales = {1.0};
        double [] vscales = {1.0};

        //These are for fixed rate vocal tract transformation: Do not change these!!!
        double [] pscalesNone = {1.0};
        double [] tscalesNone = {1.0};
        double [] escalesNone = {1.0};
        double [] vscalesNone = {1.0};
        boolean noPscaleFromFestivalUttFile = false;
        boolean noTscaleFromFestivalUttFile = false;
        boolean noEscaleFromTargetWavFile = false;
        //

        FdpsolaAdapter adapter = null;
        JointGMMTransformerParams currentWctParams = new JointGMMTransformerParams(wctParams);

        String firstPassOutputWavFile = "";
        String smoothedVocalTractFile = "";

        if (currentWctParams.isTemporalSmoothing) //Need to do two pass for smoothing
            currentWctParams.isSeparateProsody = true;

        if (currentWctParams.isSeparateProsody) //First pass with no prosody modifications
        {
            firstPassOutputWavFile = StringUtil.getFolderName(outputItem.audioFile) + StringUtil.getFileName(outputItem.audioFile) + "_vt.wav";
            smoothedVocalTractFile = StringUtil.getFolderName(outputItem.audioFile) + StringUtil.getFileName(outputItem.audioFile) + "_vt.vtf";
            int tmpPitchTransformationMethod = currentWctParams.prosodyParams.pitchTransformationMethod;
            currentWctParams.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.NO_TRANSFORMATION;

            boolean tmpPscaleFromFestivalUttFile = currentWctParams.isPscaleFromFestivalUttFile;
            boolean tmpTscaleFromFestivalUttFile = currentWctParams.isTscaleFromFestivalUttFile;
            boolean tmpEscaleFromTargetWavFile = currentWctParams.isEscaleFromTargetWavFile;
            currentWctParams.isPscaleFromFestivalUttFile = noPscaleFromFestivalUttFile;
            currentWctParams.isTscaleFromFestivalUttFile = noTscaleFromFestivalUttFile;
            currentWctParams.isEscaleFromTargetWavFile = noEscaleFromTargetWavFile;
            
            if (currentWctParams.isTemporalSmoothing) //This estimates the vocal tract filter but performs no prosody and vocal tract transformations
            {
                currentWctParams.smoothingState = SmoothingDefinitions.ESTIMATING_SMOOTHED_VOCAL_TRACT;
                currentWctParams.smoothedVocalTractFile = smoothedVocalTractFile; //It is an output at first pass

                adapter = new FdpsolaAdapter(inputItem, firstPassOutputWavFile, currentWctParams,
                                             pscalesNone, tscalesNone, escalesNone, vscalesNone);

                adapter.bSilent = !currentWctParams.isDisplayProcessingFrameCount;
                adapter.fdpsolaOnline(jgMapper, wCodebook, pMap); //Call voice conversion version

                currentWctParams.smoothingState = SmoothingDefinitions.TRANSFORMING_TO_SMOOTHED_VOCAL_TRACT;
                currentWctParams.smoothedVocalTractFile = smoothedVocalTractFile; //Now it is an input
                
                adapter = new FdpsolaAdapter(inputItem, firstPassOutputWavFile, currentWctParams,
                                             pscalesNone, tscalesNone, escalesNone, vscalesNone);
            }
            else
            {
                currentWctParams.smoothingMethod = SmoothingDefinitions.NO_SMOOTHING;
                currentWctParams.smoothingState = SmoothingDefinitions.NONE;
                currentWctParams.smoothedVocalTractFile = "";

                adapter = new FdpsolaAdapter(inputItem, firstPassOutputWavFile, currentWctParams,
                                             pscalesNone, tscalesNone, escalesNone, vscalesNone);
            }
            
            currentWctParams.isPscaleFromFestivalUttFile = tmpPscaleFromFestivalUttFile;
            currentWctParams.isTscaleFromFestivalUttFile = tmpTscaleFromFestivalUttFile;
            currentWctParams.isEscaleFromTargetWavFile = tmpEscaleFromTargetWavFile;

            //Then second step: prosody modification (with possible additional vocal tract scaling)
            if (adapter!=null)
            {
                adapter.bSilent = !currentWctParams.isDisplayProcessingFrameCount;
                adapter.fdpsolaOnline(jgMapper, wCodebook, pMap); //Call voice conversion version

                if (isScalingsRequired(pscales, tscales, escales, vscales) || tmpPitchTransformationMethod!=ProsodyTransformerParams.NO_TRANSFORMATION)
                {
                    System.out.println("Performing prosody modifications...");

                    currentWctParams.isVocalTractTransformation = false; //isVocalTractTransformation should be false 
                    currentWctParams.isFixedRateVocalTractConversion = false; //isFixedRateVocalTractConversion should be false to enable prosody modifications with FD-PSOLA
                    currentWctParams.isResynthesizeVocalTractFromSourceModel = false; //isResynthesizeVocalTractFromSourceCodebook should be false
                    currentWctParams.isVocalTractMatchUsingTargetModel = false; //isVocalTractMatchUsingTargetCodebook should be false
                    currentWctParams.prosodyParams.pitchTransformationMethod = tmpPitchTransformationMethod;
                    currentWctParams.smoothingMethod = SmoothingDefinitions.NO_SMOOTHING;
                    currentWctParams.smoothingState = SmoothingDefinitions.NONE;
                    currentWctParams.smoothedVocalTractFile = "";

                    String tmpInputWavFile = inputItem.audioFile;
                    inputItem.audioFile = firstPassOutputWavFile;
                    
                    adapter = new FdpsolaAdapter(inputItem, outputItem.audioFile, currentWctParams,
                                                 pscales, tscales, escales, vscales);
                    
                    inputItem.audioFile = tmpInputWavFile;

                    adapter.bSilent = true;
                    adapter.fdpsolaOnline(null, wCodebook, pMap);
                }
                else //Copy output file
                    FileUtils.copy(firstPassOutputWavFile, outputItem.audioFile);

                //Delete first pass output file
                if (!currentWctParams.isSaveVocalTractOnlyVersion)
                    FileUtils.delete(firstPassOutputWavFile);

                System.out.println("Done...");
            }
        }
        else //Single-pass prosody+vocal tract transformation and modification
        {
            currentWctParams.smoothingMethod = SmoothingDefinitions.NO_SMOOTHING;
            currentWctParams.smoothingState = SmoothingDefinitions.NONE;
            currentWctParams.smoothedVocalTractFile = "";

            adapter = new FdpsolaAdapter(inputItem, outputItem.audioFile, currentWctParams,
                                         pscales, tscales, escales, vscales);

            adapter.bSilent = !wctParams.isDisplayProcessingFrameCount;
            adapter.fdpsolaOnline(jgMapper, wCodebook, pMap); //Call voice conversion version
        }
    }

    public static void main(String[] args) throws IOException, UnsupportedAudioFileException 
    {
        BaselinePreprocessor pp = new BaselinePreprocessor();
        BaselineFeatureExtractor fe = new BaselineFeatureExtractor();
        BaselinePostprocessor po = new BaselinePostprocessor();
        JointGMMTransformerParams pa = new JointGMMTransformerParams();
        
        pa.isDisplayProcessingFrameCount = true;
        
        pa.inputFolder = "d:\\1\\neutral50\\test_tts";
        pa.outputBaseFolder = "d:\\1\\neutral_X_angry_50_new\\neutral2angryOut_jointGMM";
        
        String baseFile = "d:\\1\\neutral_X_angry_50_new\\neutralF_X_angryF";
        pa.jointGmmFile = baseFile + JointGMM.DEFAULT_EXTENSION;
        pa.pitchMappingFile = baseFile + PitchMappingFile.DEFAULT_EXTENSION;
        
        pa.outputFolderInfoString = "labelsGaussKmeans";
        
        pa.isForcedAnalysis = false;
        pa.isSourceVocalTractSpectrumFromModel = false;
        pa.isVocalTractTransformation = true;
        pa.isResynthesizeVocalTractFromSourceModel = false;
        pa.isVocalTractMatchUsingTargetModel= false;
        
        pa.isSeparateProsody = true;
        pa.isSaveVocalTractOnlyVersion = true;
        pa.isFixedRateVocalTractConversion = true;
        
        //Prosody transformation
        pa.prosodyParams.pitchStatisticsType = PitchStatistics.STATISTICS_IN_HERTZ;
        //pa.prosodyParams.pitchStatisticsType = PitchStatistics.STATISTICS_IN_LOGHERTZ;
        
        pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.GLOBAL_MEAN;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.GLOBAL_STDDEV;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.GLOBAL_RANGE;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.GLOBAL_SLOPE;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.GLOBAL_INTERCEPT;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.GLOBAL_MEAN_STDDEV;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.GLOBAL_MEAN_SLOPE;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.GLOBAL_INTERCEPT_STDDEV;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.GLOBAL_INTERCEPT_SLOPE;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.SENTENCE_MEAN;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.SENTENCE_STDDEV;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.SENTENCE_RANGE;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.SENTENCE_SLOPE;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.SENTENCE_INTERCEPT;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.SENTENCE_MEAN_STDDEV;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.SENTENCE_MEAN_SLOPE;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.SENTENCE_INTERCEPT_STDDEV;
        //pa.prosodyParams.pitchTransformationMethod = ProsodyTransformerParams.SENTENCE_INTERCEPT_SLOPE;
        
        pa.prosodyParams.isUseInputMean = false;
        pa.prosodyParams.isUseInputStdDev = false;
        pa.prosodyParams.isUseInputRange = false;
        pa.prosodyParams.isUseInputIntercept = false;
        pa.prosodyParams.isUseInputSlope = false;
        //
        
        //Smoothing
        pa.isTemporalSmoothing = true;
        pa.smoothingNumNeighbours = 1;
        //pa.smoothingMethod = SmoothingDefinitions.OUTPUT_LSFCONTOUR_SMOOTHING;
        //pa.smoothingMethod = SmoothingDefinitions.OUTPUT_VOCALTRACTSPECTRUM_SMOOTHING;
        pa.smoothingMethod = SmoothingDefinitions.TRANSFORMATION_FILTER_SMOOTHING;
        //
        
        //TTS tests
        pa.isPscaleFromFestivalUttFile = false;
        pa.isTscaleFromFestivalUttFile = false;
        pa.isEscaleFromTargetWavFile = false;
        //
        
        JointGMMTransformer t = new JointGMMTransformer(pp, fe, po, pa);
        t.run();
    }

}
