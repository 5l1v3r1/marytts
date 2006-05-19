import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;

import java.util.*;

/**
 * Database containing both the Catalog and the Track Files.
 */
public class UnitDatabase {
    static float lpcMin;
    static float lpcRange;
    static float mcepMin;
    static float mcepRange;

    UnitCatalog unitCatalog;
    Map filenames;
    HashMap sts;
    HashMap mcep;

    /**
     * Creates a new UnitDatabase.
     *
     * @param unitCatalog the unit unitCatalog
     * @param sts Track data from individual sts files, indexed by filename
     * @param mcep Track data from individual mcep files, indexed by filename
     */
    public UnitDatabase(UnitCatalog unitCatalog, HashMap sts, HashMap mcep) {
        this.unitCatalog = unitCatalog;
        this.sts = sts;
        this.mcep = mcep;
    }
    
    public UnitDatabase(){
    }
    
    public void setUnitCatalog(UnitCatalog unitCatalog){
        this.unitCatalog = unitCatalog;
    }
    
    public void setFilenames(Map filenames){
        this.filenames = filenames;
    }
    
    /**
     * Dumps Catalog to stdout.
     */
    void dumpUnitCatalog(PrintStream out) {
        /* Sort the keys (which are the unit types)
         */
        Iterator keys = new TreeSet(unitCatalog.keySet()).iterator();

        int currentIndex = 0;
        
        while (keys.hasNext()) {
            String key = (String) keys.next();
            ArrayList units = (ArrayList) unitCatalog.get(key);
            out.println("UNIT_TYPE " + key
                        + " " + currentIndex
                        + " " + units.size());
            currentIndex += units.size();
        }
    }
    
    public void dumpUnitCatalog(String filename) throws IOException {
        PrintStream out = new PrintStream(new FileOutputStream(filename));
        dumpUnitCatalog(out);
        out.close();
    }

    /**
     * Gets the LPC metadata
     */
    static void getLPCParams() throws IOException {
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(
                    new FileInputStream("lpc/lpc.params")));
        String line = reader.readLine();
        while (line != null) {
            if (line.startsWith("LPC_MIN=")) {
                lpcMin = Float.parseFloat(line.substring(8));
            } else if (line.startsWith("LPC_RANGE=")) {
                lpcRange = Float.parseFloat(line.substring(10));
            }
            line = reader.readLine();
        }
        reader.close();        
    }
    
    /**
     * Gets the MCEP metadata
     */
    static void getMCEPParams() throws IOException {
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(
                    new FileInputStream("mcep/mcep.params")));
        String line = reader.readLine();
        while (line != null) {
            if (line.startsWith("MCEP_MIN=")) {
                mcepMin = Float.parseFloat(line.substring(9));
            } else if (line.startsWith("MCEP_RANGE=")) {
                mcepRange = Float.parseFloat(line.substring(11));
            }
            line = reader.readLine();
        }
        reader.close();        
    }
        
    /**
     * Dumps the sts and mcep data.
     */
    public void dumpVoiceData(PrintStream stsOut, PrintStream mcepOut)
        throws IOException {
        System.out.println("   Dumping Voice Data ...");
        int sampleRate = 0;
        int numLPCChannels = 0;
        int numMCEPChannels = 0;
        
        /* Sort the keys (which are the filenames)
         */
        
        Iterator keys = new TreeSet(filenames.keySet()).iterator();
        
        int numFrames = 0;
        int i=0;
        while (keys.hasNext()) {
            
            String filename = (String) keys.next();
            Track track = new Track("sts/" + filename + ".sts",
                    Track.STS);
            sampleRate = track.sampleRate;
            numFrames += track.numFrames;
            if (i==0){
                numLPCChannels = track.numChannels;
                track = new Track("mcep/" + filename + ".mcep.txt",
                                      Track.MCEP,
                                      mcepMin,
                                      mcepRange);
                //track = (Track) mcep.get(filename);
                numMCEPChannels = track.numChannels;
                i++;
            }
        }
        /* [[[WDW FIXME: has hardcoded data.]]]
         */
        
        stsOut.println("STS STS " + numFrames
                       + " " + numLPCChannels
                       + " " + sampleRate
                       + " " + lpcMin + " " + lpcRange
                       + " 0.000000 1"); // postEmph and residualFold 
        
        mcepOut.println("STS MCEP " + numFrames
                        + " " + numMCEPChannels
                        + " " + sampleRate
                        + " " + mcepMin + " " + mcepRange
                        + " 0.000000 1"); // postEmph and residualFold

        keys = new TreeSet(filenames.keySet()).iterator();
        int currentIndex = 0;        
        while (keys.hasNext()) {
            String filename = (String) keys.next();
            
            Track track = new Track("sts/" + filename + ".sts",
                    Track.STS);
            track.startIndex = currentIndex;
            track.dumpData(stsOut);
            
            track = new Track("mcep/" + filename + ".mcep.txt",
                    Track.MCEP,
                    mcepMin,
                    mcepRange);
            track.startIndex = currentIndex;
            track.dumpData(mcepOut); 
            filenames.put(filename, new Integer(currentIndex));
            currentIndex += track.numFrames;
        }
        System.out.println("   Done");
    }

    /**
     * Dumps the unit index.
     */
    public void dumpUnitIndex(PrintStream unitIndexOut,
                              PrintStream stsOut,
                              PrintStream mcepOut) throws IOException {

        System.out.println("  Dumping STS and MCEP tracks");        
        dumpVoiceData(stsOut, mcepOut);

        System.out.println("  Dumping unit index");
        
        /* Sort the keys (which are the unit_types)
         */
        
        TreeSet unitTypes = new TreeSet(unitCatalog.keySet());

        int phoneNumber = 0; // count the index position
	// First round through all units: correct the index position
	// to the one that the units will have when we output them:
	for (Iterator keys = unitTypes.iterator(); keys.hasNext(); ) {
            String unitType = (String) keys.next();
            ArrayList units = (ArrayList) unitCatalog.get(unitType);
            
            for (int i = 0; i < units.size(); i++) {
                Unit unit = (Unit) units.get(i);
		unit.index = phoneNumber;
                phoneNumber++;
            }
	}

        int unitTypeIndex = 0;
	phoneNumber = 0;
        
	for (Iterator keys = unitTypes.iterator(); keys.hasNext(); ) {
            String unitType = (String) keys.next();
            ArrayList units = (ArrayList) unitCatalog.get(unitType);
            
            for (int i = 0; i < units.size(); i++) {
                Unit unit = (Unit) units.get(i);
                
                Track track = new Track("sts/" + unit.filename + ".sts",
                        Track.STS);
                int startIndex = track.findTrackFrameIndex(unit.start);
                int endIndex = track.findTrackFrameIndex(unit.end);
                int trackStartIndex = ((Integer)filenames.get(unit.filename)).intValue();
                
                unitIndexOut.println(
                    "UNITS " + unitTypeIndex
                    + " " + phoneNumber
                    + " " + (startIndex + trackStartIndex)
                    + " " + (endIndex + trackStartIndex)
                    + " "
                    + ((unit.previous != null) ? unit.previous.index : 65535)
                    + " "
                    + ((unit.next != null) ? unit.next.index : 65535));

                if (false) {
                    System.out.println(
                        "  " 
                        + ((unit.previous != null)
                           ? unit.previous.toString()
                           : "CLUNIT_NONE"));
                    System.out.println(
                        "  " + unit);
                    System.out.println(
                        "  "  
                        + ((unit.next != null)
                           ? unit.next.toString()
                           : "CLUNIT_NONE"));                
                }
                phoneNumber++;
            }
            unitTypeIndex++;
        }       
    }

    public void dumpUnitIndex(String unitIndexFilename,
                              String stsFilename,
                              String mcepFilename) throws IOException {
        PrintStream unitIndexOut = new PrintStream(
          new BufferedOutputStream(
            new FileOutputStream(unitIndexFilename)));
        PrintStream stsOut = new PrintStream(
          new BufferedOutputStream(
            new FileOutputStream(stsFilename)));
        //PrintStream unitIndexOut = null;
        //PrintStream stsOut = null;
        PrintStream mcepOut = new PrintStream(
	  new BufferedOutputStream(
            new FileOutputStream(mcepFilename)));
        
        dumpUnitIndex(unitIndexOut, stsOut, mcepOut);
        
        unitIndexOut.close();
        stsOut.close();
        mcepOut.close();
    }
    
    /**
     * Testing.  args[0] = *.catalog file
     *           args[1..n] = * files (no path or suffix - the code
     *                        will add sts/file.sts and
     *                        mcep/file.mcep.txt                        
     */
    static public void main(String[] args) {
        try {
            System.out.println("Version 1.0");
            System.out.println("Reading " + args[0]);
            UnitDatabase database = new UnitDatabase();
            
            System.out.println("Creating FreeTTS/unit_catalog");
            UnitCatalog unitCatalog = new UnitCatalog(args[0]);
            database.setUnitCatalog(unitCatalog);
            database.dumpUnitCatalog("FreeTTS/unit_catalog.txt");
            
            
            /* Store the TrackFile in the sts and mcep HashMaps
             * indexed by the filename.
             */
            
            System.out.println(
                "Creating FreeTTS/unit_index.txt, FreeTTS/sts.txt, and "
                + "FreeTTS/mcep.txt");
            
            
            
            //System.out.println("Reading and dumping STS");
            getLPCParams();
            getMCEPParams();
            //HashMap sts = new HashMap();
            
            //for (int i = 1; i < args.length; i++) {
            //    sts.put(
            //        args[i],
            //        new Track("sts/" + args[i] + ".sts",
            //                  Track.STS));
            //}
            
            //HashMap mcep = new HashMap();
            //for (int i = 1; i < args.length; i++){
            //    mcep.put(
            //        args[i],
            //        new Track("mcep/" + args[i] + ".mcep.txt",
            //                  Track.MCEP,
            //                  mcepMin,
            //                  mcepRange));
            //}
            //database.setSTS(sts);
            
            Map filenames = new HashMap();
            for (int i = 1; i < args.length; i++){
                filenames.put(args[i],null);
            }
            database.setFilenames(filenames);
            database.dumpUnitIndex("FreeTTS/unit_index.txt",
                    "FreeTTS/sts.txt",
                    "FreeTTS/mcep.txt");
            
            

            System.out.println("Done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
