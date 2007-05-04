/**
 * Portions Copyright 2006 DFKI GmbH.
 * Portions Copyright 2001 Sun Microsystems, Inc.
 * Portions Copyright 1999-2001 Language Technologies Institute, 
 * Carnegie Mellon University.
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
package de.dfki.lt.mary.unitselection.voiceimport;

import java.io.File;
import java.util.Locale;

/**
 * The DatabaseLayout class registers the base directory of a voice database,
 * as well as the various subdirectories where the various voice database
 * components should be stored or read from.
 * 
 * @author sacha
 *
 */
public class DatabaseLayout 
{   
    /****************/
    /* CONSTRUCTORS */
    /****************/
    
    /**
     * Constructor for a new database layout.
     * 
     */
    public DatabaseLayout() {
        initDefaultProps();
    }
    
    /**
     * Constructor for a new database layout.
     * @param locale the locale of the voice
     */
    public DatabaseLayout(Locale locale) {
        setIfDoesntExist( "db.locale", locale.getLanguage());
        initDefaultProps();
    }
    
    /**
     * Initializes a default database layout.
     *
     */
    private void initDefaultProps() {
        
        /* root : the name of the root directory for the database */
        setIfDoesntExist( "db.rootDir", new File(".").getAbsolutePath() );
        
        /* Output directory for Mary format files */
        setIfDoesntExist( "db.marySubDir", "mary_files" );
        setIfDoesntExist( "db.maryExtension", ".mry" );
        
        /* Input directory for Mary (database import) config files */
        setIfDoesntExist( "db.maryConfigSubDir", "mary_configs" );
        /* Default feature weights file */
        setIfDoesntExist( "db.halfphone-featureweights.file", "halfphoneUnitFeatureDefinition.txt");
        setIfDoesntExist( "db.phone-featureweights.file", "phoneUnitFeatureDefinition.txt");
        /* Default feature sequence file */
        setIfDoesntExist( "db.featuresequence.file", "featureSequence.txt" );
        /* Default join cost feature weights file */
        setIfDoesntExist( "db.joinCostWeights.file", "joinCostWeights.txt" );
        
        /* The file for the list of utterances */
        setIfDoesntExist( "db.basenameFile", "basenames.lst" );
        setIfDoesntExist( "db.basenameTimelineBaseName", "timeline_basenames" );
        
        /* Default text.data file */
        setIfDoesntExist( "db.text.baseFile", "etc/txt.done.data" );
        
        /* Text files */
        setIfDoesntExist( "db.text.subDir", "text" );
        setIfDoesntExist( "db.text.extension", ".txt" );
        
        /* Phonetic label files */
        setIfDoesntExist( "db.phonelab.subDir", "lab" );
        setIfDoesntExist( "db.phonelab.extension", ".lab" );
        
        /* Unit label files */
        setIfDoesntExist( "db.unitlab.subDir", "phonelab" );
        setIfDoesntExist( "db.unitlab.extension", ".lab" );
        setIfDoesntExist( "db.halfphone-unitlab.subDir", "halfphonelab" );
        setIfDoesntExist( "db.halfphone-unitlab.extension", ".hplab" );

        
        /* Unit feature files */
        setIfDoesntExist( "db.unitfeatures.subDir", "phonefeatures" );
        setIfDoesntExist( "db.unitfeatures.extension", ".pfeats" );
        setIfDoesntExist( "db.halfphone-unitfeatures.subDir", "halfphonefeatures" );
        setIfDoesntExist( "db.halfphone-unitfeatures.extension", ".hpfeats" );

        /* Raw Mary XML files */
        setIfDoesntExist( "db.rawmaryxml.subDir", "text" );
        setIfDoesntExist( "db.rawmaryxml.extension", ".rawmaryxml" );
        
        /* Wav files */
        setIfDoesntExist( "db.wavSubDir", "wav" );
        setIfDoesntExist( "db.wavExtension", ".wav" );
        setIfDoesntExist( "db.waveTimelineBaseName", "timeline_waveforms" );
       
        /* LPC files */
        setIfDoesntExist( "db.lpcSubDir", "lpc" );
        setIfDoesntExist( "db.lpcExtension", ".lpc" );
        setIfDoesntExist( "db.lpcTimelineBaseName", "timeline_quantized_lpc+res" );
        
        /* Pitchmark files*/
        setIfDoesntExist( "db.pitchmarksSubDir", "pm" );
        setIfDoesntExist( "db.pitchmarksExtension", ".pm" );
        setIfDoesntExist( "db.correctedPitchmarksSubDir", "pm" );
        setIfDoesntExist( "db.correctedPitchmarksExtension", ".pm.corrected" );
        
        /* Mel Cepstrum files */
        setIfDoesntExist( "db.melcepSubDir", "mcep" );
        setIfDoesntExist( "db.melcepExtension", ".mcep" );
        setIfDoesntExist( "db.melcepTimelineBaseName", "timeline_mcep" );
        
        /* Timeline files */
        setIfDoesntExist( "db.timelineSubDir", System.getProperty("db.marySubDir") );
        setIfDoesntExist( "db.timelineExtension", ".mry" );
        
        /* CART files */
        setIfDoesntExist( "db.cartsSubDir", System.getProperty("db.marySubDir") );
        
        /* Wagon files */
        setIfDoesntExist( "db.wagonSubDir", "wagon" ); 
        setIfDoesntExist( "db.wagonDesc", "wagon.desc" );
        setIfDoesntExist( "db.wagonFeats", "wagon.feats" );
        setIfDoesntExist( "db.wagonDistTabs", "wagon.distTabs" );
        setIfDoesntExist( "db.wagonCart", "wagon.cart" );
        setIfDoesntExist( "db.topLevelTree", "topLevel.tree" );
        
        /* Other Mary files */
        setIfDoesntExist( "db.halfphoneFeaturesBaseName", "halfphoneFeatures" );
        setIfDoesntExist( "db.phoneFeaturesBaseName", "phoneFeatures" );
        setIfDoesntExist( "db.joinCostFeaturesBaseName", "joinCostFeatures" );
        setIfDoesntExist( "db.precomputedJoinCostsBaseName", "joinCosts" );
        setIfDoesntExist( "db.halfphoneUnitFileBaseName", "halfphoneUnits" );
        setIfDoesntExist( "db.phoneUnitFileBaseName", "phoneUnits" );
        setIfDoesntExist( "db.cartFileBaseName", "cart" );
    }
    
    /**
     * Sets a property if this property has not been set before. This is used to preserve
     * user overrides if they were produced before the instanciation of the databaseLayout.
     * 
     * @param propertyName The property name.
     * @param propertyVal The property value.
     */
    public static void setIfDoesntExist( String propertyName, String propertyVal ) {
        if ( System.getProperty( propertyName ) == null ) System.setProperty( propertyName, propertyVal );
    }
    
    /*****************/
    /* OTHER METHODS */
    /*****************/
    
    /* Various accessors and absolute path makers: */

    /* Locale */
    public String locale() {return ( System.getProperty( "db.locale") ); }
    
    /* Database root directory */
    public String rootDirName() { return( System.getProperty( "db.rootDir") ); }
    
    /* List of basenames */
    public String basenameFile() {
        String ret = System.getProperty( "db.basename.file" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.basenameFile" ) );
    }
    public String basenameTimelineFileName() {
        String ret = System.getProperty( "db.basename.timeline.file" );
        if ( ret != null ) return( ret );
        /* else: */
        return( timelineDirName() + System.getProperty( "db.basenameTimelineBaseName" ) + timelineExt() );
    }
    
    /* BASE TEXT FILE */
    public String baseTxtFileName() {
        String ret = System.getProperty( "db.text.file" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.text.baseFile" ) );
    }
    
    
    /* TXT */
    public String txtDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.text.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String txtExt() { return( System.getProperty( "db.text.extension") ); }
    
    /* LAB */
    public String labDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.phonelab.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String labExt() { return( System.getProperty( "db.phonelab.extension") ); }
    
    /* UNITLAB */
    public String phoneUnitLabDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.unitlab.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String phoneUnitLabExt() { return( System.getProperty( "db.unitlab.extension") ); }
    public String halfphoneUnitLabDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.halfphone-unitlab.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String halfphoneUnitLabExt() { return( System.getProperty( "db.halfphone-unitlab.extension") ); }
    
    /* UNIT FEATURES */
    public String phoneUnitFeaDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.unitfeatures.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String phoneUnitFeaExt() { return( System.getProperty( "db.unitfeatures.extension") ); }
    public String halfphoneUnitFeaDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.halfphone-unitfeatures.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String halfphoneUnitFeaExt() { return( System.getProperty( "db.halfphone-unitfeatures.extension") ); }
    
    /* RAW MARY XML */
    public String rmxDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.rawmaryxml.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String rmxExt() { return( System.getProperty( "db.rawmaryxml.extension") ); }
    
    /* WAV */
    public String wavDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.wavSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String wavExt() { return( System.getProperty( "db.wavExtension") ); }
    
    /* LPC */
    public String lpcDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.lpcSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String lpcExt() { return( System.getProperty( "db.lpcExtension") ); }
    /* File name for the LPC+residual timeline */
    public String lpcTimelineFileName() {
        String ret = System.getProperty( "db.lpcTimelineFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( timelineDirName() + System.getProperty( "db.lpcTimelineBaseName" ) + timelineExt() );
    }
    
    /* TIMELINES */
    public String timelineDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.timelineSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String timelineExt() { return( System.getProperty( "db.timelineExtension") ); }
    
    /* PITCHMARKS */
    public String pitchmarksDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.pitchmarksSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String pitchmarksExt() { return( System.getProperty( "db.pitchmarksExtension") ); }
    public String correctedPitchmarksDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.correctedPitchmarksSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String correctedPitchmarksExt() { return( System.getProperty( "db.correctedPitchmarksExtension") ); }
    
    /* MELCEP */
    public String melcepDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.melcepSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String melcepExt() { return( System.getProperty( "db.melcepExtension") ); }
    /* File name for the mel cepstrum timeline */
    public String melcepTimelineFileName() {
        String ret = System.getProperty( "db.melcepTimelineFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( timelineDirName() + System.getProperty( "db.melcepTimelineBaseName" ) + timelineExt() );
    }
    
    /* File name for the waveform timeline */
    public String waveTimelineFileName() {
        String ret = System.getProperty( "db.waveTimelineFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( timelineDirName() + System.getProperty( "db.waveTimelineBaseName" ) + timelineExt() );
    }
    
    /* Feature Sequence for top-level CART */
    public String featSequenceFileName () {
        return ( maryConfigDirName() + System.getProperty( "file.separator" )
                + System.getProperty( "db.featuresequence.file") ); 
    }
    
    /* Wagon files */
    public String wagonDirName(){
        return ( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.wagonSubDir" ) );
    }
    public String wagonDescFile(){
        return  System.getProperty( "db.wagonDesc" );
    }
    public String wagonFeatsFile(){
        return  System.getProperty( "db.wagonFeats" );
    }
      public String wagonDistTabsFile(){
        return  System.getProperty( "db.wagonDistTabs" );
    }
     public String wagonCartFile(){
        return  System.getProperty( "db.wagonCart" );
    }
     
    public String topLevelTreeFilename(){
        return ( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.maryConfigSubDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.topLevelTree" ) );
    }
    
    /* MARY FILES */
    
    /* - Directories: */
    
    public String maryDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.marySubDir") ); }
    
    public String maryConfigDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.maryConfigSubDir") ); }
    
    public String cartsDirName() { return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.cartsSubDir") ); }
    
    /* - Configs:*/
    
    /* File name for the unit feature definition and unit feature weights */
    public String halfphoneUnitFeatureDefinitionFileName() {
        String ret = System.getProperty( "db.halfphoneUnitFeatureDefinitionFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( maryConfigDirName() + System.getProperty( "file.separator" ) + System.getProperty( "db.halfphone-featureweights.file" ) );
    }
    public String halfphoneWeightsFileName() { return( halfphoneUnitFeatureDefinitionFileName() ); }
    public String phoneUnitFeatureDefinitionFileName() {
        String ret = System.getProperty( "db.phoneUnitFeatureDefinitionFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( maryConfigDirName() + System.getProperty( "file.separator" ) + System.getProperty( "db.phone-featureweights.file" ) );
    }
    public String phoneWeightsFileName() { return( phoneUnitFeatureDefinitionFileName() ); }
    
    /** For halfphone synthesis, the name of the feature definition file containing
     * the weights for the left halves.
     */
    public String halfPhoneLeftWeightsFileName()
    {
        return maryConfigDirName() + "/" + System.getProperty("db.featureweights.left.file", "weights.left.txt");
    }

    /** For halfphone synthesis, the name of the feature definition file containing
     * the weights for the right halves.
     */
    public String halfPhoneRightWeightsFileName()
    {
        return maryConfigDirName() + "/" + System.getProperty("db.featureweights.right.file", "weights.right.txt");
    }

    /* File name for the unit feature definition and unit feature weights */
    public String featureSequenceFileName() {
        String ret = System.getProperty( "db.featureSequenceFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( maryConfigDirName() + System.getProperty( "file.separator" ) + System.getProperty( "db.featuresequence.file" ) );
    }
    
    /* File name for the unit feature definition and unit feature weights */
    public String joinCostWeightsFileName() {
        String ret = System.getProperty( "db.joinCostWeightsFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( maryConfigDirName() + System.getProperty( "file.separator" ) + System.getProperty( "db.joinCostWeights.file" ) );
    }
    
    /* - Mary format files:*/
    
    /* File name for the target features file */
    public String halfphoneFeaturesFileName() {
        String ret = System.getProperty( "db.halfphoneFeaturesFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.halfphoneFeaturesBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }
    public String phoneFeaturesFileName() {
        String ret = System.getProperty( "db.phoneFeaturesFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.phoneFeaturesBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }

    /* File name for the target features file */
    public String halfphoneFeaturesWithAcousticFeaturesFileName() {
        String ret = System.getProperty( "db.halfphoneFeaturesFileNameAc" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.halfphoneFeaturesBaseName" ) + "_ac" + System.getProperty( "db.maryExtension" ) );
    }

    
    /* File name for the join cost features file */
    public String joinCostFeaturesFileName() {
        String ret = System.getProperty( "db.joinCostFeaturesFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.joinCostFeaturesBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }

    public String precomputedJoinCostsFileName() {
        String ret = System.getProperty( "db.precomputedJoinCostsFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.precomputedJoinCostsBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }

    /* File name for the unit file */
    public String halfphoneUnitFileName() {
        String ret = System.getProperty( "db.halfphoneUnitFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.halfphoneUnitFileBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }
    public String phoneUnitFileName() {
        String ret = System.getProperty( "db.phoneUnitFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.phoneUnitFileBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }
    
    /* File name for the cart file */
    public String cartFileName() {
        String ret = System.getProperty( "db.cartFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.rootDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.cartFileBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }
    
}
