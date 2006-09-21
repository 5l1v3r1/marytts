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
package de.dfki.lt.mary.unitselection.voiceimport_reorganized;

import java.io.File;
import java.util.Properties;

import de.dfki.lt.mary.MaryProperties;

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
     * Initializes a default database layout.
     *
     */
    private void initDefaultProps() {
        
        /* baseName : the name of the root directory for the database */
        setIfDoesntExist( "db.baseName", "." );
        
        /* Default text.data file */
        setIfDoesntExist( "db.text.baseFile", "etc/txt.done.data" );
        
        /* Text files */
        setIfDoesntExist( "db.text.subDir", "text" );
        setIfDoesntExist( "db.text.extension", ".txt" );
        
        /* Phonetic label files */
        setIfDoesntExist( "db.phonelab.subDir", "lab" );
        setIfDoesntExist( "db.phonelab.extension", ".lab" );
        
        /* Unit label files */
        setIfDoesntExist( "db.unitlab.subDir", "unitlab" );
        setIfDoesntExist( "db.unitlab.extension", ".unitlab" );
        
        /* Unit feature files */
        setIfDoesntExist( "db.unitfeatures.subDir", "unitfeatures" );
        setIfDoesntExist( "db.unitfeatures.extension", ".feats" );
        
        /* Raw Mary XML files */
        setIfDoesntExist( "db.rawmaryxml.subDir", "text" );
        setIfDoesntExist( "db.rawmaryxml.extension", ".rawmaryxml" );
        
        /* Wav files */
        setIfDoesntExist( "db.wavSubDir", "wav" );
        setIfDoesntExist( "db.wavExtension", ".wav" );
        
        /* LPC files */
        setIfDoesntExist( "db.lpcSubDir", "lpc" );
        setIfDoesntExist( "db.lpcExtension", ".lpc" );
        setIfDoesntExist( "db.lpcTimelineBaseName", "timeline_quantized_lpc+res" );
        
        /* Pitchmark files*/
        setIfDoesntExist( "db.pitchmarksSubDir", "pm" );
        setIfDoesntExist( "db.pitchmarksExtension", ".pm" );
        
        /* Mel Cepstrum files */
        setIfDoesntExist( "db.melcepSubDir", "mcep" );
        setIfDoesntExist( "db.melcepExtension", ".mcep" );
        setIfDoesntExist( "db.melcepTimelineBaseName", "timeline_mcep" );
        
        /* Timeline files */
        setIfDoesntExist( "db.timelineSubDir", "mary_timelines" );
        setIfDoesntExist( "db.timelineExtension", ".bin" );
        
        /* Mary format files */
        setIfDoesntExist( "db.marySubDir", "mary" );
        setIfDoesntExist( "db.maryExtension", ".bin" );
        
        setIfDoesntExist( "db.cartsSubDir", "mary" );
        
        setIfDoesntExist( "db.targetFeaturesBaseName", "targetFeatures" );
        setIfDoesntExist( "db.joinCostFeaturesBaseName", "joinCostFeatures" );
        setIfDoesntExist( "db.unitFileBaseName", "units" );
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

    public String baseName() { return( System.getProperty( "db.baseName") ); }
    
    /* BASE TEXT FILE */
    public String baseTxtFileName() {
        String ret = System.getProperty( "db.text.file" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.text.baseFile" ) );
    }
    
    
    /* TXT */
    public String txtDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.text.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String txtExt() { return( System.getProperty( "db.text.extension") ); }
    
    /* LAB */
    public String labDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.phonelab.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String labExt() { return( System.getProperty( "db.phonelab.extension") ); }
    
    /* UNITLAB */
    public String unitLabDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.unitlab.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String unitLabExt() { return( System.getProperty( "db.unitlab.extension") ); }
    
    /* UNIT FEATURES */
    public String unitFeaDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.unitfeatures.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String unitFeaExt() { return( System.getProperty( "db.unitfeatures.extension") ); }
    
    /* RAW MARY XML */
    public String rmxDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.rawmaryxml.subDir" ) + System.getProperty( "file.separator" ) ); }
    public String rmxExt() { return( System.getProperty( "db.rawmaryxml.extension") ); }
    
    /* WAV */
    public String wavDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.wavSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String wavExt() { return( System.getProperty( "db.wavExtension") ); }
    
    /* LPC */
    public String lpcDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
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
    public String timelineDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.timelineSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String timelineExt() { return( System.getProperty( "db.timelineExtension") ); }
    
    /* PITCHMARKS */
    public String pitchmarksDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.pitchmarksSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String pitchmarksExt() { return( System.getProperty( "db.pitchmarksExtension") ); }
    
    /* MELCEP */
    public String melcepDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.melcepSubDir" ) + System.getProperty( "file.separator" ) ); }
    public String melcepExt() { return( System.getProperty( "db.melcepExtension") ); }
    /* File name for the mel cepstrum timeline */
    public String melcepTimelineFileName() {
        String ret = System.getProperty( "db.melcepTimelineFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( timelineDirName() + System.getProperty( "db.melcepTimelineBaseName" ) + timelineExt() );
    }
    
    
    /* MARY FILES */
    public String maryDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.marySubDir") ); }
    
    public String cartsDirName() { return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
            + System.getProperty( "db.cartsSubDir") ); }
    
    
    /* File name for the target features file */
    public String targetFeaturesFileName() {
        String ret = System.getProperty( "db.targetFeaturesFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.targetFeaturesBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }
    
    /* File name for the join cost features file */
    public String joinCostFeaturesFileName() {
        String ret = System.getProperty( "db.joinCostFeaturesFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.joinCostFeaturesBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }

    /* File name for the unit file */
    public String unitFileName() {
        String ret = System.getProperty( "db.unitFileName" );
        if ( ret != null ) return( ret );
        /* else: */
        return( System.getProperty( "db.baseName" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.marySubDir" ) + System.getProperty( "file.separator" )
                + System.getProperty( "db.unitFileBaseName" ) + System.getProperty( "db.maryExtension" ) );
    }
    
}
